const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');

async function logAudit({
  userId = null,
  adminId = null,
  action,
  oldPackage = null,
  newPackage = null,
  reason = null,
  ipAddress = null,
  meta = null,
}) {
  await ensureSubscriptionV3Schema();
  await prisma.$executeRaw`
    INSERT INTO subscription_audit_log (
      user_id, admin_id, action, old_package, new_package, reason, ip_address, meta_json
    ) VALUES (
      ${userId ? Number(userId) : null},
      ${adminId ? Number(adminId) : null},
      ${action},
      ${oldPackage},
      ${newPackage},
      ${reason},
      ${ipAddress},
      ${meta ? JSON.stringify(meta) : null}
    )
  `;
}

module.exports = { logAudit };
