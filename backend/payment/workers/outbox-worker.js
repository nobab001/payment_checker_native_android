/**
 * @file Outbox worker — processes merchant_callback_outbox pending rows.
 * @module payment/workers/outbox-worker
 */

const os = require('os');
const { processOutboxBatch } = require('../reliability/merchant-callback-outbox');

const WORKER_ID = `outbox@${os.hostname()}:${process.pid}`;

async function runOutboxWorker({ limit = 30 } = {}) {
  const outcome = await processOutboxBatch({ limit, workerId: WORKER_ID });
  if (outcome.processed > 0) {
    console.log(`[OUTBOX] processed ${outcome.processed} row(s)`);
  }
  return outcome;
}

module.exports = { runOutboxWorker, WORKER_ID };
