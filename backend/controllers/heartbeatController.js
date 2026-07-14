/**
 * POST /api/gateway/heartbeat — package-tiered liveness + Comm Policy response.
 */

const numberHealth = require('../services/numberHealthService');
const commPolicy = require('../services/commPolicyService');
const dataSyncCache = require('../services/dataSyncCache');

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
      // Monitoring Stop → Offline signal path
      if (profile.deactivateOnOffline) {
        const watchdog = require('../services/deviceDisconnectWatchdog');
        await watchdog.deactivateDeviceBindings(userId, deviceId);
      }
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
    await watchdog.reactivateDeviceBindings(userId, deviceId, numbers);
    watchdog.cancelDeviceWatch(userId, deviceId);
    // Re-arm offline timer from this successful heartbeat
    watchdog.scheduleDeviceWatch(userId, deviceId, profile);

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
