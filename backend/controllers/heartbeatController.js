/**
 * POST /api/gateway/heartbeat — package-tiered liveness + Comm Policy response.
 */

const numberHealth = require('../services/numberHealthService');
const commPolicy = require('../services/commPolicyService');
const dataSyncCache = require('../services/dataSyncCache');
const presenceV25 = require('../services/presenceV25');

async function postHeartbeat(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';

    if (!deviceId) {
      return res.status(400).json({ success: false, error: 'X-Device-Id header আবশ্যক' });
    }

    const profile = await numberHealth.getCachedProfile(userId);
    const policy = commPolicy.toClientPolicy(profile);

    let templateVersion = null;
    try {
      templateVersion = await dataSyncCache.getGlobalTemplateVersion();
    } catch (_) {
      templateVersion = null;
    }

    const smsActive = req.body.sms_service_active !== false
      && req.body.smsServiceActive !== false;

    if (!smsActive) {
      // Monitoring Stop — hide numbers from checkout immediately
      const watchdog = require('../services/deviceDisconnectWatchdog');
      const v2On = await presenceV25.isPresenceV2Enabled(profile?.id || 'personal');
      if (v2On) {
        try {
          await presenceV25.markDeviceOffline(userId, deviceId, { reason: 'SMS_SERVICE_OFF' });
        } catch (e) {
          console.warn('[PresenceV25] markDeviceOffline SMS_SERVICE_OFF failed:', e.message);
          await watchdog.deactivateDeviceBindings(userId, deviceId);
        }
      } else {
        await watchdog.deactivateDeviceBindings(userId, deviceId);
      }
      watchdog.cancelDeviceWatch(userId, deviceId);
      return res.json({
        success: true,
        skipped: true,
        message: 'SMS service inactive — offline signal applied',
        forceSync: false,
        templateVersion,
        ...policy,
      });
    }

    const numbers = Array.isArray(req.body.numbers) ? req.body.numbers : [];
    const batteryPercent = req.body.battery_percent ?? req.body.batteryPercent;

    const result = await numberHealth.recordHeartbeat(
      userId,
      deviceId,
      numbers,
      { batteryPercent },
    );

    // Ensure bindings stay active while heartbeat is healthy
    const watchdog = require('../services/deviceDisconnectWatchdog');
    const presenceV25 = require('../services/presenceV25');
    const v2On = await presenceV25.isPresenceV2Enabled(profile?.id || 'personal');

    if (!v2On) {
      await watchdog.reactivateDeviceBindings(userId, deviceId, numbers);
      watchdog.cancelDeviceWatch(userId, deviceId);
      watchdog.scheduleDeviceWatch(userId, deviceId, profile);
    }

    // Phase 2: Presence Engine alive entry-point (heartbeat success only)
    try {
      const trigger = String(req.body.presence_trigger || req.body.presenceTrigger || '').trim();
      let source = 'HB_SUCCESS';
      if (trigger === 'boot_completed') source = 'BOOT_SUCCESS';
      else if (trigger === 'network_restored') source = 'NETWORK_RESTORED';

      await presenceV25.markDeviceAlive(userId, deviceId, {
        source,
        packageKey: profile?.id,
      });
    } catch (e) {
      console.warn('[PresenceV25] markDeviceAlive heartbeat failed:', e.message);
    }

    const forceSync = false;

    return res.json({
      success: true,
      server_time: result.serverTime,
      numbers: result.updated,
      states: result.states,
      forceSync,
      templateVersion,
      message: null,
      ...policy,
      thresholds: {
        ...policy.thresholds,
        stale_sec: numberHealth.THRESHOLDS.STALE_MS / 1000,
      },
    });
  } catch (error) {
    console.error('[Heartbeat] error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/**
 * GET /api/gateway/numbers/health — optional debug / device UI
 */
async function getNumbersHealth(req, res) {
  try {
    const userId = req.user.userId;
    const raw = req.query.numbers || req.query.number || '';
    const list = String(raw).split(',').map((s) => s.trim()).filter(Boolean);

    if (!list.length) {
      return res.status(400).json({ success: false, error: 'numbers query param আবশ্যক' });
    }

    const states = {};
    for (const phone of list) {
      states[phone] = await numberHealth.getNumberState(userId, phone);
    }

    return res.json({ success: true, states });
  } catch (error) {
    console.error('[Heartbeat] getNumbersHealth error:', error);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  postHeartbeat,
  getNumbersHealth,
};
