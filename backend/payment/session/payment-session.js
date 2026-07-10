/**
 * @file Payment Session Engine — sole owner of payment_sessions persistence.
 * @module payment/session/payment-session
 */

const prisma = require('../../db/prisma');
const { genPaymentToken } = require('../shared/provider-utils');
const { toCanonicalStatus } = require('../core/payment-status');
const { assertTransition, toLegacyStatus } = require('../state/payment-state-machine');
const { emitPaymentEvent, PAYMENT_EVENTS } = require('../events/event-bus');

/**
 * @typedef {Object} CreateSessionInput
 * @property {number} websiteId
 * @property {number} userId
 * @property {string} officialProvider — DB provider key (lowercase)
 * @property {number} amount
 * @property {string} [currency]
 * @property {string} [orderId]
 * @property {string} [successUrl]
 * @property {string} [cancelUrl]
 * @property {string} [callbackUrl]
 * @property {number} [timeoutSec]
 * @property {string} [traceId]
 * @property {string} [channel]
 * @property {string} [customerNumber]
 * @property {string} [webhookUrl]
 * @property {Object} [meta]
 */

function mapRow(row) {
  if (!row) return null;
  return {
    id: row.id,
    sessionToken: row.session_token,
    websiteId: row.website_id,
    userId: row.user_id,
    officialProvider: row.official_provider,
    amount: Number(row.amount),
    currency: row.currency,
    status: toCanonicalStatus(row.status),
    legacyStatus: row.status,
    orderId: row.order_id,
    successUrl: row.success_url,
    cancelUrl: row.cancel_url,
    expiresAt: row.expires_at,
    completedAt: row.completed_at,
    traceId: row.meta_json ? safeParseMeta(row.meta_json)?.traceId : null,
    meta: row.meta_json ? safeParseMeta(row.meta_json) : {},
    webhookUrl: row.webhook_url,
    callbackUrl: row.callback_url,
    customerNumber: row.customer_number,
    channel: row.channel,
    trxId: row.trx_id,
  };
}

function safeParseMeta(json) {
  try { return JSON.parse(json); } catch (_) { return {}; }
}

function mergeMeta(existingJson, patch) {
  const base = existingJson ? safeParseMeta(existingJson) : {};
  return JSON.stringify({ ...base, ...patch });
}

const PaymentSessionEngine = {
  /**
   * @param {CreateSessionInput} input
   */
  async createSession(input) {
    const token = genPaymentToken();
    const timeoutSec = input.timeoutSec ?? 1800;
    const expiresAt = new Date(Date.now() + timeoutSec * 1000);
    const meta = {};
    if (input.traceId) meta.traceId = input.traceId;
    if (input.meta && typeof input.meta === 'object') Object.assign(meta, input.meta);

    const row = await prisma.payment_sessions.create({
      data: {
        session_token: token,
        website_id: input.websiteId,
        user_id: input.userId,
        amount: input.amount,
        currency: input.currency || 'BDT',
        channel: input.channel || 'official',
        official_provider: input.officialProvider
          ? String(input.officialProvider).toLowerCase()
          : null,
        order_id: input.orderId || null,
        customer_number: input.customerNumber || null,
        status: 'created',
        success_url: input.successUrl || null,
        cancel_url: input.cancelUrl || null,
        callback_url: input.callbackUrl || null,
        webhook_url: input.webhookUrl || null,
        expires_at: expiresAt,
        meta_json: Object.keys(meta).length ? JSON.stringify(meta) : null,
      },
    });

    return mapRow(row);
  },

  async getSession(sessionToken) {
    const row = await prisma.payment_sessions.findUnique({
      where: { session_token: sessionToken },
    });
    return mapRow(row);
  },

  /** @deprecated use getSession */
  async getByToken(sessionToken) {
    return this.getSession(sessionToken);
  },

  async updateStatus(sessionToken, status, extra = {}) {
    const current = await this.getSession(sessionToken);
    if (!current) {
      throw new Error(`PaymentSessionEngine: session not found: ${sessionToken}`);
    }

    assertTransition(current.status, status);
    const legacy = toLegacyStatus(status);
    const row = await prisma.payment_sessions.update({
      where: { session_token: sessionToken },
      data: {
        status: legacy,
        ...extra,
      },
    });
    const mapped = mapRow(row);

    const eventMap = {
      completed: PAYMENT_EVENTS.PAYMENT_COMPLETED,
      failed: PAYMENT_EVENTS.PAYMENT_FAILED,
      expired: PAYMENT_EVENTS.PAYMENT_EXPIRED,
      redirected: PAYMENT_EVENTS.PAYMENT_REDIRECTED,
    };
    const evt = eventMap[legacy];
    if (evt) {
      emitPaymentEvent(evt, {
        traceId: mapped.traceId,
        sessionToken,
        status: mapped.status,
        websiteId: mapped.websiteId,
      });
    }

    return mapped;
  },

  async expireSession(sessionToken) {
    return this.updateStatus(sessionToken, 'expired');
  },

  async completeSession(sessionToken, extra = {}) {
    return this.updateStatus(sessionToken, 'completed', {
      completed_at: new Date(),
      ...extra,
    });
  },

  async cancelSession(sessionToken) {
    return this.updateStatus(sessionToken, 'failed', {
      completed_at: new Date(),
    });
  },

  async markRedirected(sessionToken) {
    return this.updateStatus(sessionToken, 'redirected');
  },

  async updateMeta(sessionToken, patch) {
    const row = await prisma.payment_sessions.findUnique({
      where: { session_token: sessionToken },
    });
    if (!row) return null;
    const updated = await prisma.payment_sessions.update({
      where: { session_token: sessionToken },
      data: { meta_json: mergeMeta(row.meta_json, patch) },
    });
    return mapRow(updated);
  },

  async bulkExpireOpenSessions() {
    const rows = await prisma.payment_sessions.findMany({
      where: {
        status: { in: ['created', 'redirected'] },
        expires_at: { lt: new Date() },
      },
      select: { session_token: true },
    });

    let count = 0;
    for (const row of rows) {
      try {
        await this.expireSession(row.session_token);
        count += 1;
      } catch (_) { /* already terminal */ }
    }
    return count;
  },
};

module.exports = PaymentSessionEngine;
