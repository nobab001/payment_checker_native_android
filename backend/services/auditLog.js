/**
 * auditLog.js — append-only audit trail writer (Phase 8).
 *
 * All writes are fire-and-forget and swallow errors: auditing must never break
 * or slow down the request path. Use `log()` for structured events.
 */

const prisma = require('../db/prisma');

/**
 * @param {object} e
 * @param {string} e.eventType   e.g. 'payment.init', 'payment.completed', 'callback.delivered'
 * @param {string} [e.entityType] e.g. 'payment_session', 'website'
 * @param {string|number} [e.entityId]
 * @param {number} [e.websiteId]
 * @param {number} [e.userId]
 * @param {string} [e.ip]
 * @param {string} [e.status]     e.g. 'success' | 'failed'
 * @param {object} [e.detail]     JSON-serializable extra context
 */
function log(e) {
  const data = {
    event_type: String(e.eventType || 'unknown').slice(0, 60),
    entity_type: e.entityType ? String(e.entityType).slice(0, 40) : null,
    entity_id: e.entityId != null ? String(e.entityId).slice(0, 80) : null,
    website_id: Number.isFinite(e.websiteId) ? e.websiteId : null,
    user_id: Number.isFinite(e.userId) ? e.userId : null,
    ip: e.ip ? String(e.ip).slice(0, 64) : null,
    status: e.status ? String(e.status).slice(0, 20) : null,
    detail_json: e.detail ? safeStringify(e.detail) : null,
  };
  // Fire-and-forget: never await, never throw.
  prisma.audit_logs.create({ data }).catch((err) => {
    console.warn('[AUDIT] write failed:', err.message);
  });
}

function safeStringify(obj) {
  try { return JSON.stringify(obj).slice(0, 60000); } catch (_) { return null; }
}

module.exports = { log };
