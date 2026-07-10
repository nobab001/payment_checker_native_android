/**
 * @file Database-level duplicate transaction guard.
 * @module payment/session/duplicate-guard
 */

const prisma = require('../../db/prisma');
const { PaymentError, PAY_ERROR_CODES } = require('../errors/error-registry');

/**
 * Block same provider trx_id completing on a different session for same website.
 * @param {string} sessionToken
 * @param {string|null} providerTransactionId
 * @param {number} websiteId
 */
async function assertUniqueProviderTransaction(sessionToken, providerTransactionId, websiteId) {
  if (!providerTransactionId || !websiteId) return;

  const duplicate = await prisma.payment_sessions.findFirst({
    where: {
      website_id: websiteId,
      trx_id: String(providerTransactionId),
      status: 'completed',
      NOT: { session_token: sessionToken },
    },
    select: { session_token: true },
  });

  if (duplicate) {
    throw new PaymentError(PAY_ERROR_CODES.DUPLICATE_CALLBACK, {
      existingSession: duplicate.session_token,
      providerTransactionId,
    });
  }
}

module.exports = {
  assertUniqueProviderTransaction,
};
