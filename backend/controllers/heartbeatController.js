/**
 * POST /api/gateway/heartbeat — sparse fallback ping while Socket.IO is disconnected.
 */

const numberHealth = require('../services/numberHealthService');

async function postHeartbeat(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';

    if (!deviceId) {
      return res.status(400).json({ success: false, error: 'X-Device-Id header আবশ্যক' });
    }

    const smsActive = req.body.sms_service_active !== false
      && req.body.smsServiceActive !== false;

    if (!smsActive) {
      return res.json({
        success: true,
        skipped: true,
        message: 'SMS service inactive — heartbeat skipped',
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

    return res.json({
      success: true,
      server_time: result.serverTime,
      numbers: result.updated,
      states: result.states,
      thresholds: {
        online_sec: numberHealth.THRESHOLDS.ONLINE_MS / 1000,
        grace_sec: numberHealth.THRESHOLDS.GRACE_MS / 1000,
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
