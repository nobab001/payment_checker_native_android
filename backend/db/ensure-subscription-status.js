/**
 * Additive schema guard: users.subscription_status ENUM.
 * Safe to run on every boot — no-op when column already exists.
 */
const { ensureSubscriptionStatusSchema } = require('../services/subscriptionStatusService');

async function ensureSubscriptionStatusColumn() {
  await ensureSubscriptionStatusSchema();
}

module.exports = { ensureSubscriptionStatusColumn };
