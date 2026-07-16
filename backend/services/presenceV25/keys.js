/** Redis key helpers — Device Communication v2.5 */

function seenZset(userId) {
  return `presence:seen:${String(userId)}`;
}

function probeHash(userId, deviceId) {
  return `presence:probe:${String(userId)}:${String(deviceId)}`;
}

function lockKey(userId, deviceId) {
  return `presence:lock:${String(userId)}:${String(deviceId)}`;
}

function policyCache(packageKey) {
  return `presence:policy:${String(packageKey)}`;
}

/** Member in seen ZSET: deviceId (scoped per userId in key). */
function seenMember(deviceId) {
  return String(deviceId);
}

module.exports = {
  seenZset,
  probeHash,
  lockKey,
  policyCache,
  seenMember,
};
