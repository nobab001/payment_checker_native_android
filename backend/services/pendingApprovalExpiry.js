/**
 * Pending child-device approval requests auto-expire after TTL.
 * Soft-rejects (status=rejected) so the same phone can re-login and reopen as pending.
 */
const { query } = require('../db/connection');

const PENDING_APPROVAL_TTL_MINUTES = Math.max(
  1,
  Number(process.env.PENDING_APPROVAL_TTL_MINUTES) || 12
);

/**
 * Soft-expire stale pending approval rows.
 * @param {number|null} userId optional — limit to one account
 * @returns {Promise<number>} rows expired
 */
async function expireStalePendingApprovals(userId = null) {
  const params = [PENDING_APPROVAL_TTL_MINUTES];
  let userClause = '';
  if (userId != null) {
    userClause = ' AND user_id = ?';
    params.push(Number(userId));
  }

  const result = await query(
    `UPDATE registered_devices
     SET status = 'rejected',
         is_approved = 0,
         device_role = 'pending',
         custom_device_name = CASE
           WHEN custom_device_name IS NULL OR custom_device_name = '' THEN custom_device_name
           WHEN custom_device_name LIKE '%(expired)%' THEN custom_device_name
           ELSE CONCAT(custom_device_name, ' (expired)')
         END
     WHERE is_parent = 0
       AND is_approved = 0
       AND status = 'pending'
       AND COALESCE(last_seen_at, created_at) < DATE_SUB(NOW(), INTERVAL ? MINUTE)
       ${userClause}`,
    params
  );

  const affected = Number(result?.affectedRows || result?.affected_rows || 0);
  if (affected > 0) {
    console.log(
      `[AUTH] Expired ${affected} pending approval(s) after ${PENDING_APPROVAL_TTL_MINUTES}m`
      + (userId != null ? ` user=${userId}` : '')
    );
  }
  return affected;
}

module.exports = {
  PENDING_APPROVAL_TTL_MINUTES,
  expireStalePendingApprovals,
};
