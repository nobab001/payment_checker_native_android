/**
 * @file Merchant Callback Outbox — durable delivery queue (Phase-3B.5).
 * @module payment/reliability/merchant-callback-outbox
 *
 * Flow: Payment SUCCESS → DB transaction → outbox row → worker → HTTP callback
 */

const crypto = require('crypto');
const prisma = require('../../db/prisma');
const audit = require('../../services/auditLog');
const { deliverHttpMerchantCallback } = require('../callback/merchant-callback-http');
const { logPayment } = require('../logging/trace-logger');

const LOCK_STALE_MS = 5 * 60 * 1000;
const MAX_OUTBOX_ATTEMPTS = 5;

function deliveryKey(sessionToken, targetUrl) {
  const hash = crypto.createHash('sha256').update(targetUrl).digest('hex').slice(0, 16);
  return `mcb:${sessionToken}:${hash}`;
}

function client(tx) {
  return tx || prisma;
}

/**
 * Insert outbox rows inside a Prisma transaction (idempotent per session+url).
 * @param {import('@prisma/client').Prisma.TransactionClient} [tx]
 */
async function enqueueMerchantCallbacks(tx, { sessionToken, websiteId, traceId, targets, payload }) {
  const db = client(tx);
  const rows = [];

  for (const url of targets) {
    const key = deliveryKey(sessionToken, url);
    try {
      const row = await db.merchant_callback_outbox.create({
        data: {
          delivery_key: key,
          session_token: sessionToken,
          website_id: websiteId,
          target_url: url,
          payload_json: JSON.stringify(payload),
          trace_id: traceId,
          status: 'pending',
        },
      });
      rows.push(row);
    } catch (err) {
      if (err.code === 'P2002') {
        const existing = await db.merchant_callback_outbox.findUnique({
          where: { delivery_key: key },
        });
        if (existing) rows.push(existing);
      } else {
        throw err;
      }
    }
  }

  if (rows.length) {
    audit.log({
      eventType: 'outbox.merchant_callback.enqueued',
      entityType: 'payment_session',
      entityId: sessionToken,
      websiteId,
      status: 'pending',
      detail: { traceId, targets: targets.length },
    });
  }

  return rows;
}

async function claimPendingRows(limit, workerId, sessionToken = null) {
  const staleBefore = new Date(Date.now() - LOCK_STALE_MS);
  const where = {
    attempts: { lt: MAX_OUTBOX_ATTEMPTS },
    OR: [
      { status: 'pending' },
      { status: 'processing', locked_at: { lt: staleBefore } },
    ],
  };
  if (sessionToken) where.session_token = sessionToken;

  const candidates = await prisma.merchant_callback_outbox.findMany({
    where,
    orderBy: { created_at: 'asc' },
    take: limit,
  });

  const claimed = [];
  for (const row of candidates) {
    const updated = await prisma.merchant_callback_outbox.updateMany({
      where: {
        id: row.id,
        attempts: { lt: MAX_OUTBOX_ATTEMPTS },
        OR: [
          { status: 'pending' },
          { status: 'processing', locked_at: { lt: staleBefore } },
        ],
      },
      data: {
        status: 'processing',
        locked_at: new Date(),
        locked_by: workerId,
        attempts: { increment: 1 },
      },
    });

    if (updated.count === 1) {
      const fresh = await prisma.merchant_callback_outbox.findUnique({ where: { id: row.id } });
      if (fresh) claimed.push(fresh);
    }
  }
  return claimed;
}

async function processOutboxRow(row) {
  const traceId = row.trace_id || `ptr_outbox_${row.id}`;
  let payload;
  try {
    payload = JSON.parse(row.payload_json);
  } catch (err) {
    await prisma.merchant_callback_outbox.update({
      where: { id: row.id },
      data: { status: 'dead', last_error: 'INVALID_PAYLOAD_JSON', locked_at: null, locked_by: null },
    });
    return { ok: false, dead: true, reason: 'INVALID_PAYLOAD_JSON' };
  }

  const website = await prisma.gateway_layouts.findUnique({
    where: { id: row.website_id },
    select: {
      id: true, user_id: true, merchant_id: true, api_secret: true,
      callback_url: true, webhook_url: true,
    },
  });

  const outcome = await deliverHttpMerchantCallback({
    url: row.target_url,
    payload,
    website,
    traceId,
    sessionToken: row.session_token,
  });

  if (outcome.ok) {
    await prisma.merchant_callback_outbox.update({
      where: { id: row.id },
      data: {
        status: 'sent',
        sent_at: new Date(),
        last_error: null,
        locked_at: null,
        locked_by: null,
      },
    });
    logPayment(traceId, 'Outbox', 'sent', { sessionToken: row.session_token, url: row.target_url });
    return outcome;
  }

  if (outcome.dead || row.attempts >= MAX_OUTBOX_ATTEMPTS) {
    await prisma.merchant_callback_outbox.update({
      where: { id: row.id },
      data: {
        status: 'dead',
        last_error: outcome.reason || 'max_attempts',
        locked_at: null,
        locked_by: null,
      },
    });
    audit.log({
      eventType: 'outbox.merchant_callback.dead',
      entityType: 'payment_session',
      entityId: row.session_token,
      websiteId: row.website_id,
      status: 'dead',
      detail: { traceId, url: row.target_url, reason: outcome.reason },
    });
    return outcome;
  }

  await prisma.merchant_callback_outbox.update({
    where: { id: row.id },
    data: {
      status: 'pending',
      last_error: outcome.reason || 'delivery_failed',
      locked_at: null,
      locked_by: null,
    },
  });
  return outcome;
}

/**
 * Process a batch of pending outbox rows (inline after payment or cron worker).
 */
async function processOutboxBatch({ limit = 20, workerId = 'outbox-worker', sessionToken = null } = {}) {
  const rows = await claimPendingRows(limit, workerId, sessionToken);
  const results = [];
  for (const row of rows) {
    results.push(await processOutboxRow(row));
  }
  return { processed: rows.length, results };
}

async function getPendingCount() {
  return prisma.merchant_callback_outbox.count({
    where: { status: { in: ['pending', 'processing'] } },
  });
}

async function getDeadCount() {
  return prisma.merchant_callback_outbox.count({ where: { status: 'dead' } });
}

module.exports = {
  deliveryKey,
  enqueueMerchantCallbacks,
  processOutboxBatch,
  processOutboxRow,
  getPendingCount,
  getDeadCount,
  MAX_OUTBOX_ATTEMPTS,
};
