// backend/services/DeviceBindingService.js

/**
 * Service handling device binding validation and binding operations.
 * Utilizes the existing DB connection via `query` helper.
 */
const { query } = require('../db/connection');

/**
 * Validate that the given deviceId is bound to the specified userId (if provided).
 * Returns an object { valid: boolean, reason?: string, userId?: number }.
 *
 * - If deviceId is missing, validation passes (caller may decide otherwise).
 * - If the device is found but is trial locked or expired, returns valid: false.
 * - If device exists and belongs to a different user, returns valid: false.
 */
async function validateDevice(deviceId, userId = null) {
  if (!deviceId) {
    return { valid: true };
  }
  const rows = await query(
    `SELECT user_id, trial_expires_at, is_trial_locked FROM registered_devices WHERE device_id = ? LIMIT 1`,
    [deviceId]
  );
  if (!rows || rows.length === 0) {
    // No binding record – treat as unbound (caller can decide to bind later)
    return { valid: true };
  }
  const dev = rows[0];
  const expired = dev.trial_expires_at && new Date(dev.trial_expires_at) < new Date();
  if (dev.is_trial_locked || expired) {
    return { valid: false, reason: 'TRIAL_EXPIRED_FOR_DEVICE', userId: dev.user_id };
  }
  if (userId && dev.user_id !== userId) {
    return { valid: false, reason: 'DEVICE_BOUND_TO_OTHER_USER', userId: dev.user_id };
  }
  return { valid: true, userId: dev.user_id };
}

/**
 * Bind a device to a user. If a record already exists for the device, it will be updated.
 * Creates a new record otherwise.
 */
async function bindDevice(userId, deviceId, deviceName = 'My Phone') {
  // Upsert pattern: try update first, then insert if affectedRows == 0
  const updateResult = await query(
    `UPDATE registered_devices SET user_id = ?, device_name = ?, status = 'active', is_trial_locked = 0 WHERE device_id = ?`,
    [userId, deviceName, deviceId]
  );
  if (updateResult && updateResult.affectedRows && updateResult.affectedRows > 0) {
    return { updated: true };
  }
  // Insert new record
  await query(
    `INSERT INTO registered_devices (user_id, device_id, device_name, status, is_parent) VALUES (?, ?, ?, 'active', 0)`,
    [userId, deviceId, deviceName]
  );
  return { inserted: true };
}

module.exports = {
  validateDevice,
  bindDevice,
};
