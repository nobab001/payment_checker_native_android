/**
 * v2.5 presence worker — Phase 2: probe state machine + markDeviceOffline.
 */
const prisma = require('../../db/prisma');
const policyLoader = require('./policyLoader');
const store = require('./presenceStore');
const engine = require('./engine');
const metrics = require('./metrics');

const { DRY_RUN } = engine;

let lastMetricsLogAt = 0;
const METRICS_LOG_INTERVAL_MS = 60 * 60 * 1000;

function maybeLogMetrics() {
  const now = Date.now();
  if (now - lastMetricsLogAt < METRICS_LOG_INTERVAL_MS) return;
  lastMetricsLogAt = now;
  const snap = metrics.snapshot();
  console.log(`[PresenceV25] METRICS ${JSON.stringify(snap)} dryRun=${DRY_RUN}`);
}

/**
 * Sweep one user's seen ZSET for devices past offline_deadline (by package policy).
 */
async function sweepUserDevices(userId, packageKey, policy, now = Date.now(), allowedDeviceIds = null) {
  const hbMs = (Number(policy.heartbeat_interval_sec) || 300) * 1000;
  const v2 = await policyLoader.isPresenceV2Enabled(packageKey);
  if (!v2) return { checked: 0, candidates: 0 };

  const missThreshold = now - hbMs;
  const candidates = await store.findExpiredDevices(userId, missThreshold, 50);
  if (!candidates.length) return { checked: 0, candidates: 0 };

  let processed = 0;
  const allowSet = allowedDeviceIds instanceof Set ? allowedDeviceIds : null;

  for (const deviceId of candidates) {
    // Skip / clean Redis for pending, rejected, or unknown devices (e.g. LOGIN before approve).
    if (allowSet && !allowSet.has(String(deviceId))) {
      await store.clearProbeState(userId, deviceId);
      await store.removeSeen(userId, deviceId);
      await store.releaseProbeLock(userId, deviceId);
      continue;
    }

    const probePreview = await store.getProbeState(userId, deviceId);
    const previewStage = probePreview?.stage ? Number(probePreview.stage) : 0;
    const lockTtlSec = store.computeLockTtlSec(policy);

    const locked = await store.acquireProbeLock(userId, deviceId, {
      workerId: `worker:${process.pid}`,
      probeStage: previewStage,
      ttlSec: lockTtlSec,
    });
    if (!locked) {
      metrics.inc('lock_skipped', 1);
      console.log(`[PresenceV25] LOCK_SKIP user=${userId} device=${deviceId}`);
      continue;
    }

    try {
      processed += 1;

      const seenScoreMs = await store.getSeenScore(userId, deviceId);
      if (!seenScoreMs || seenScoreMs <= 0) continue;

      const ageMs = now - seenScoreMs;
      if (ageMs < hbMs) continue; // race: heartbeat came in between scan + lock

      const steps = Array.isArray(policy.probe_steps_json) ? policy.probe_steps_json : [];
      const p0Ms = (Number(steps[0]) || 0) * 1000;
      const p1Ms = (Number(steps[1]) || 0) * 1000;
      const p2Ms = (Number(steps[2]) || 0) * 1000;

      const stage1EndMs = hbMs + p0Ms;
      const stage2EndMs = stage1EndMs + p1Ms;
      const offlineDeadlineMs = stage2EndMs + p2Ms;

      const offline = ageMs >= offlineDeadlineMs;
      const targetStage = offline
        ? 3
        : ageMs < stage1EndMs
          ? 1
          : ageMs < stage2EndMs
            ? 2
            : 3;

      const probe = await store.getProbeState(userId, deviceId);
      const hadProbe = probe && probe.stage !== undefined && String(probe.stage) !== '';
      const currentStage = hadProbe ? Number(probe.stage) : 0;

      if (offline) {
        if (currentStage !== 3) {
          await store.setProbeState(userId, deviceId, { stage: 3 });
          console.log(
            `[PresenceV25] PROBE_STAGE user=${userId} device=${deviceId} stage=3 from=${currentStage}`
          );
        }

        await engine.markDeviceOffline(userId, deviceId, { reason: 'PROBE_STAGE3' });
        await store.clearProbeState(userId, deviceId);
        await store.removeSeen(userId, deviceId);
        await store.releaseProbeLock(userId, deviceId);
        continue;
      }

      if (!hadProbe) {
        await store.setProbeState(userId, deviceId, { stage: targetStage });
        metrics.inc('probe_started', 1);
        console.log(
          `[PresenceV25] PROBE_START user=${userId} device=${deviceId} stage=${targetStage}`
        );
      } else if (currentStage !== targetStage) {
        await store.setProbeState(userId, deviceId, { stage: targetStage });
        console.log(
          `[PresenceV25] PROBE_STAGE user=${userId} device=${deviceId} stage=${targetStage} from=${currentStage}`
        );
      } else if (currentStage > 0) {
        metrics.inc('probe_resumed', 1);
        if (process.env.PRESENCE_V25_VERBOSE === '1') {
          console.log(
            `[PresenceV25] PROBE_RESUME user=${userId} device=${deviceId} stage=${currentStage}`
          );
        }
      }
    } finally {
      await store.releaseProbeLock(userId, deviceId);
    }
  }

  return { checked: candidates.length, candidates: processed };
}

/**
 * Active registered devices for sweep (v2.5 uses presence:seen per user).
 */
async function listActiveDeviceUsers(limit = 200) {
  const rows = await prisma.$queryRaw`
    SELECT DISTINCT user_id, device_id
    FROM registered_devices
    WHERE status = 'active'
      AND device_id IS NOT NULL AND device_id != ''
    LIMIT ${limit}
  `;
  const byUser = new Map();
  for (const row of rows || []) {
    const uid = String(row.user_id);
    if (!byUser.has(uid)) byUser.set(uid, []);
    byUser.get(uid).push(String(row.device_id));
  }
  return byUser;
}

async function resolvePackageKeyForUser(userId) {
  try {
    const commPolicy = require('../commPolicyService');
    const profile = await commPolicy.resolveCommProfile(userId);
    return profile?.id || 'personal';
  } catch (_) {
    return 'personal';
  }
}

/**
 * Main sweep tick — uses minimum worker_sweep from policies for cron scheduling;
 * per-user deadline uses that user's package policy.
 */
async function runSweepTick() {
  const policies = await policyLoader.refreshAllPolicies();
  const minSweep = Math.min(
    ...Object.values(policies).map((p) => Number(p.worker_sweep_sec) || 30),
    30,
  );

  const users = await listActiveDeviceUsers(300);
  let totalCandidates = 0;
  let usersChecked = 0;

  for (const [userId, deviceIds] of users) {
    usersChecked += 1;
    const packageKey = await resolvePackageKeyForUser(userId);
    const policy = policies[packageKey] || policies.personal || policies.gateway;
    if (!policy) continue;
    const allowSet = new Set((deviceIds || []).map(String));
    const r = await sweepUserDevices(userId, packageKey, policy, Date.now(), allowSet);
    totalCandidates += r.candidates || 0;
  }

  if (totalCandidates > 0 || process.env.PRESENCE_V25_VERBOSE === '1') {
    console.log(
      `[PresenceV25] sweep users=${usersChecked} candidates=${totalCandidates} dryRun=${DRY_RUN}`
    );
  }

  const didLogMetrics = (() => {
    const before = lastMetricsLogAt;
    maybeLogMetrics();
    return lastMetricsLogAt !== before;
  })();

  // Phase 3 shadow: run with hourly metrics (or every sweep when VERBOSE)
  if (didLogMetrics || process.env.PRESENCE_V25_VERBOSE === '1' || process.env.PRESENCE_V25_SHADOW === '1') {
    try {
      await require('./shadowCompare').runShadowCompare(80);
    } catch (e) {
      console.warn('[PresenceV25] shadow compare failed:', e.message);
    }
  }

  return { usersChecked, candidates: totalCandidates, minSweepSec: minSweep };
}

module.exports = {
  runSweepTick,
  sweepUserDevices,
  listActiveDeviceUsers,
};
