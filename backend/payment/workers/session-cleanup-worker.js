/**
 * @file Expire stale payment sessions (every 5 min).
 */

const PaymentSessionEngine = require('../session/payment-session');
const { logPayment } = require('../logging/trace-logger');

async function runSessionCleanup() {
  const count = await PaymentSessionEngine.bulkExpireOpenSessions();
  if (count > 0) {
    logPayment(null, 'Worker', 'session.cleanup', { expiredCount: count });
  }
  return count;
}

module.exports = { runSessionCleanup };
