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
  };
}

function safeParseMeta(json) {
  try { return JSON.parse(json); } catch (_) { return {}; }
}

const PaymentSessionEngine = {
  /**
   * @param {CreateSessionInput} input
   */
  async createSession(input) {
    const token = genPaymentToken();
    const timeoutSec = input.timeoutSec ?? 1800;
    const expiresAt = new Date(Date.now() + timeoutSec * 1000);
    const meta = input.traceId ? { traceId: input.traceId } : null;

    const row = await prisma.payment_sessions.create({
      data: {
        session_token: token,
        website_id: input.websiteId,
        user_id: input.userId,
        amount: input.amount,
        currency: input.currency || 'BDT',
        channel: 'official',
        official_provider: String(input.officialProvider).toLowerCase(),
        order_id: input.orderId || null,
        status: 'created',
        success_url: input.successUrl || null,
        cancel_url: input.cancelUrl || null,
        callback_url: input.callbackUrl || null,
        expires_at: expiresAt,
        meta_json: meta ? JSON.stringify(meta) : null,
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
};

module.exports = PaymentSessionEngine;
