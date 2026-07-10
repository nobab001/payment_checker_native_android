/**
 * Role reads/writes via SQL so stale Prisma clients (missing `role` field) still work.
 */
const prisma = require('../../db/prisma');
const { ROLES } = require('./roles');

function toCount(val) {
  if (val == null) return 0;
  return Number(val);
}

async function getRoleByUserId(userId) {
  try {
    const rows = await prisma.$queryRawUnsafe(
      `SELECT role FROM demo_merchant_users WHERE id = ${Number(userId)} LIMIT 1`,
    );
    const role = rows?.[0]?.role;
    return role === ROLES.ADMIN ? ROLES.ADMIN : ROLES.USER;
  } catch {
    return ROLES.USER;
  }
}

async function countAdmins() {
  try {
    const rows = await prisma.$queryRawUnsafe(
      "SELECT COUNT(*) AS c FROM demo_merchant_users WHERE role = 'admin'",
    );
    return toCount(rows?.[0]?.c);
  } catch {
    return 0;
  }
}

async function setRole(userId, role) {
  const safeRole = role === ROLES.ADMIN ? ROLES.ADMIN : ROLES.USER;
  try {
    await prisma.$executeRawUnsafe(
      `UPDATE demo_merchant_users SET role = '${safeRole}' WHERE id = ${Number(userId)} LIMIT 1`,
    );
  } catch (err) {
    console.error('[DemoMerchant] setRole failed:', err.message);
  }
}

async function resolveFirstUserRole() {
  const admins = await countAdmins();
  if (admins === 0) {
    const total = await prisma.demo_merchant_users.count().catch(() => 0);
    if (total === 0) return ROLES.ADMIN;
  }
  return ROLES.USER;
}

module.exports = {
  getRoleByUserId,
  countAdmins,
  setRole,
  resolveFirstUserRole,
};
