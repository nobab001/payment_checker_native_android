// backend/services/DeviceBindingService.js

/**
 * Service handling device binding validation and binding operations.
 * Utilizes Prisma.
 */
const prisma = require('../db/prisma');

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
  const dev = await prisma.registered_devices.findFirst({
    where: { device_id: deviceId },
    select: { user_id: true, trial_expires_at: true, is_trial_locked: true }
  });
  
  if (!dev) {
    // No binding record – treat as unbound (caller can decide to bind later)
    return { valid: true };
  }
  const expired = dev.trial_expires_at && new Date(dev.trial_expires_at) < new Date();
  if (dev.is_trial_locked || expired) {
    return { valid: false, reason: 'DEVICE_ALREADY_BOUND', userId: dev.user_id };
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
  const existing = await prisma.registered_devices.findFirst({
    where: { device_id: deviceId }
  });
  
  if (existing) {
    await prisma.registered_devices.update({
      where: { id: existing.id },
      data: {
        user_id: userId,
        device_name: deviceName,
        status: 'active',
        is_trial_locked: 0
      }
    });
    return { updated: true };
  } else {
    await prisma.registered_devices.create({
      data: {
        user_id: userId,
        device_id: deviceId,
        device_name: deviceName,
        status: 'active',
        is_parent: 0
      }
    });
    return { inserted: true };
  }
}

module.exports = {
  validateDevice,
  bindDevice,
};
