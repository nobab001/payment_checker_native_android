/**
 * users.is_trial — canonical trial flag (not plan name).
 */
const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');
const { getTrialPlanName, DEFAULT_TRIAL_PLAN_NAME } = require('../trialPlanService');

async function setUserTrialFlag(userId, isTrial) {
  await ensureSubscriptionV3Schema();
  await prisma.$executeRaw`
    UPDATE users SET is_trial = ${isTrial ? 1 : 0} WHERE id = ${Number(userId)}
  `;
}

async function isUserOnTrial(userId) {
  try {
    const rows = await prisma.$queryRaw`
      SELECT COALESCE(is_trial, 0) AS is_trial FROM users WHERE id = ${Number(userId)} LIMIT 1
    `;
    return Number(rows[0]?.is_trial || 0) === 1;
  } catch (_) {
    // Column may not exist yet — treat as non-trial for hot path safety.
    return false;
  }
}

async function backfillIsTrialFlags() {
  await ensureSubscriptionV3Schema();
  const row = await prisma.global_config.findUnique({
    where: { config_key: 'is_trial_backfill_done' },
  });
  if (row?.config_value === '1') return;

  const trialName = await getTrialPlanName();
  const names = [...new Set([trialName, DEFAULT_TRIAL_PLAN_NAME])].filter(Boolean);

  for (const name of names) {
    await prisma.$executeRaw`
      UPDATE users u
      SET is_trial = 1
      WHERE u.active_plan_name = ${name}
        AND COALESCE(u.is_trial, 0) = 0
        AND NOT EXISTS (
          SELECT 1 FROM user_subscriptions s
          WHERE s.user_id = u.id AND s.status = 'active'
        )
    `;
  }

  await prisma.$executeRaw`
    UPDATE users u
    SET is_trial = 0
    WHERE COALESCE(u.is_trial, 0) = 1
      AND EXISTS (
        SELECT 1 FROM user_subscriptions s
        WHERE s.user_id = u.id AND s.status = 'active'
      )
  `;

  await prisma.global_config.upsert({
    where: { config_key: 'is_trial_backfill_done' },
    update: { config_value: '1' },
    create: { config_key: 'is_trial_backfill_done', config_value: '1' },
  });
}

module.exports = {
  setUserTrialFlag,
  isUserOnTrial,
  backfillIsTrialFlags,
};
