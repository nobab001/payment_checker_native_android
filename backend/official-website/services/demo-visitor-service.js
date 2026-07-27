/**
 * Browser-based sandbox demo visitors — isolated from real merchants/users.
 */

const crypto = require('crypto');
const prisma = require('../../db/prisma');
const config = require('../config');
const { sanitizeOverrides, applyOverrides, defaultOverrides } = require('./session-store');

const COOKIE_NAME = 'pc_demo';
const STATUS = { ACTIVE: 'active', EXPIRED: 'expired', PURGED: 'purged' };
const HISTORY_COOKIE_MS = 90 * 24 * 60 * 60 * 1000;
const REFUND_RETENTION_MS = 5 * 24 * 60 * 60 * 1000;

function sha256(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex');
}

function randomPublicId() {
  return `tdv_${crypto.randomBytes(12).toString('hex')}`;
}

function randomToken() {
  return crypto.randomBytes(24).toString('hex');
}

function displayName() {
  const code = crypto.randomBytes(2).toString('hex').toUpperCase();
  return `Demo-${code}`;
}

function clientIp(req) {
  const xf = req.headers['x-forwarded-for'];
  if (typeof xf === 'string' && xf.length) return xf.split(',')[0].trim();
  return req.ip || req.socket?.remoteAddress || '';
}

function hashIp(req) {
  const ip = clientIp(req);
  if (!ip) return null;
  return sha256(`${ip}|${process.env.JWT_SECRET || 'demo'}`);
}

function hashUa(req) {
  const ua = req.headers['user-agent'] || '';
  if (!ua) return null;
  return sha256(ua.slice(0, 240));
}

function parseSettings(raw) {
  if (!raw) return defaultOverrides();
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return sanitizeOverrides({ ...defaultOverrides(), ...parsed });
  } catch {
    return defaultOverrides();
  }
}

function publicVisitor(row, meta = {}) {
  if (!row) return null;
  const settings = parseSettings(row.settings_json);
  const expiresAt = new Date(row.expires_at).getTime();
  const msLeft = Math.max(0, expiresAt - Date.now());
  return {
    publicId: row.public_id,
    displayName: row.display_name,
    status: row.status,
    expiresAt: row.expires_at,
    createdAt: row.created_at,
    lastSeenAt: row.last_seen_at,
    msLeft,
    hoursLeft: Math.round((msLeft / 3600000) * 10) / 10,
    settings,
    hostWebsiteId: row.host_website_id,
    canTransact: meta.canTransact ?? msLeft > 0,
    historyAccessible: meta.historyAccessible ?? true,
    hasOpenRefund: meta.hasOpenRefund ?? false,
    historyVisibleUntil: meta.historyVisibleUntil || null,
  };
}

function parseCookieHeader(cookieHeader) {
  const out = {};
  if (!cookieHeader) return out;
  String(cookieHeader).split(';').forEach((part) => {
    const idx = part.indexOf('=');
    if (idx < 0) return;
    const k = part.slice(0, idx).trim();
    const v = part.slice(idx + 1).trim();
    if (k) out[k] = decodeURIComponent(v);
  });
  return out;
}

function readDemoCookie(req) {
  const cookies = parseCookieHeader(req.headers.cookie);
  const raw = cookies[COOKIE_NAME];
  if (!raw) return null;
  const dot = raw.indexOf('.');
  if (dot < 1) return null;
  return {
    publicId: raw.slice(0, dot),
    token: raw.slice(dot + 1),
  };
}

function setDemoCookie(res, publicId, token, expiresAt) {
  const cookieUntil = Math.max(new Date(expiresAt).getTime(), Date.now() + HISTORY_COOKIE_MS);
  const maxAge = Math.max(60, Math.floor((cookieUntil - Date.now()) / 1000));
  const secure = String(process.env.APP_ENV || '').toLowerCase() === 'production' ? '; Secure' : '';
  const value = encodeURIComponent(`${publicId}.${token}`);
  res.setHeader(
    'Set-Cookie',
    `${COOKIE_NAME}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${secure}`,
  );
}

async function computeHistoryAccess(row) {
  const nowMs = Date.now();
  const expiresMs = new Date(row.expires_at).getTime();
  const canTransact = row.status === STATUS.ACTIVE && expiresMs > nowMs;

  const [openRefundCount, latestRefunded] = await Promise.all([
    prisma.demo_payments.count({
      where: {
        visitor_id: row.id,
        refund_status: { not: 'refunded' },
      },
    }),
    prisma.demo_payments.findFirst({
      where: {
        visitor_id: row.id,
        refund_status: 'refunded',
        refunded_at: { not: null },
      },
      orderBy: { refunded_at: 'desc' },
      select: { refunded_at: true },
    }),
  ]);

  const hasOpenRefund = openRefundCount > 0;
  let historyVisibleUntil = null;
  if (hasOpenRefund) {
    historyVisibleUntil = null;
  } else if (latestRefunded?.refunded_at) {
    historyVisibleUntil = new Date(latestRefunded.refunded_at.getTime() + REFUND_RETENTION_MS);
  } else {
    historyVisibleUntil = new Date(row.expires_at);
  }

  const historyAccessible = !historyVisibleUntil || historyVisibleUntil.getTime() > nowMs;
  return {
    canTransact,
    hasOpenRefund,
    historyAccessible,
    historyVisibleUntil: historyVisibleUntil ? historyVisibleUntil.toISOString() : null,
  };
}

function clearDemoCookie(res) {
  const secure = String(process.env.APP_ENV || '').toLowerCase() === 'production' ? '; Secure' : '';
  res.setHeader(
    'Set-Cookie',
    `${COOKIE_NAME}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secure}`,
  );
}

async function countRecentByIp(ipHash, sinceMs) {
  if (!ipHash) return 0;
  const since = new Date(Date.now() - sinceMs);
  return prisma.demo_visitors.count({
    where: {
      ip_hash: ipHash,
      created_at: { gte: since },
    },
  });
}

async function assertRateLimit(req) {
  const ipHash = hashIp(req);
  const hourCount = await countRecentByIp(ipHash, 60 * 60 * 1000);
  if (hourCount >= config.demoMaxAccountsPerHour) {
    const err = new Error('Too many demo accounts from this network. Try again later.');
    err.code = 'RATE_LIMIT';
    err.status = 429;
    throw err;
  }
  const dayCount = await countRecentByIp(ipHash, 24 * 60 * 60 * 1000);
  if (dayCount >= config.demoMaxAccountsPerDay) {
    const err = new Error('Daily demo account limit reached for this network.');
    err.code = 'RATE_LIMIT';
    err.status = 429;
    throw err;
  }
}

async function createVisitor(req, { forceNew = false } = {}) {
  await assertRateLimit(req);
  const publicId = randomPublicId();
  const token = randomToken();
  const now = new Date();
  const expiresAt = new Date(now.getTime() + config.demoTtlMs);
  const settings = defaultOverrides();

  const row = await prisma.demo_visitors.create({
    data: {
      public_id: publicId,
      display_name: displayName(),
      token_hash: sha256(token),
      host_website_id: config.hostWebsiteId || null,
      settings_json: JSON.stringify(settings),
      ip_hash: hashIp(req),
      user_agent_hash: hashUa(req),
      status: STATUS.ACTIVE,
      expires_at: expiresAt,
      last_seen_at: now,
    },
  });

  return { visitor: publicVisitor(row), token, forceNew };
}

async function findActiveByCookie(req) {
  const parsed = readDemoCookie(req);
  if (!parsed?.publicId || !parsed?.token) return null;

  const row = await prisma.demo_visitors.findUnique({
    where: { public_id: parsed.publicId },
  });
  if (!row) return null;
  if (row.token_hash !== sha256(parsed.token)) return null;
  if (new Date(row.expires_at).getTime() <= Date.now() && row.status === STATUS.ACTIVE) {
    await prisma.demo_visitors.update({
      where: { id: row.id },
      data: { status: STATUS.EXPIRED },
    }).catch(() => {});
  }

  const freshRow = row.status === STATUS.ACTIVE && new Date(row.expires_at).getTime() > Date.now()
    ? row
    : await prisma.demo_visitors.findUnique({ where: { id: row.id } });
  const access = await computeHistoryAccess(freshRow || row);
  if (!access.canTransact && !access.historyAccessible) return null;

  const updated = await prisma.demo_visitors.update({
    where: { id: row.id },
    data: { last_seen_at: new Date() },
  });
  return publicVisitor(updated, access);
}

async function getByPublicId(publicId, { allowExpired = false } = {}) {
  if (!publicId) return null;
  const row = await prisma.demo_visitors.findUnique({
    where: { public_id: String(publicId) },
  });
  if (!row) return null;
  const access = await computeHistoryAccess(row);
  if (!allowExpired && !access.canTransact && !access.historyAccessible) return null;
  return { row, visitor: publicVisitor(row, access) };
}

async function updateSettings(publicId, patch) {
  const found = await getByPublicId(publicId);
  if (!found) return null;
  const next = sanitizeOverrides({
    ...parseSettings(found.row.settings_json),
    ...patch,
  });
  const updated = await prisma.demo_visitors.update({
    where: { id: found.row.id },
    data: {
      settings_json: JSON.stringify(next),
      last_seen_at: new Date(),
    },
  });
  return publicVisitor(updated);
}

async function recordPayment(publicId, payment) {
  // Allow expired visitors so success webhook can still update refund history
  const found = await getByPublicId(publicId, { allowExpired: true });
  if (!found) return null;

  const orderId = payment.orderId || null;
  const sessionToken = payment.sessionToken || null;
  const now = new Date();

  // Prefer updating existing initiated row (same order/session) instead of duplicating on webhook.
  let existing = null;
  if (orderId) {
    existing = await prisma.demo_payments.findFirst({
      where: { visitor_id: found.row.id, order_id: orderId },
      orderBy: { id: 'desc' },
    });
  }
  if (!existing && sessionToken) {
    existing = await prisma.demo_payments.findFirst({
      where: { visitor_id: found.row.id, session_token: sessionToken },
      orderBy: { id: 'desc' },
    });
  }

  const data = {
    amount: payment.amount != null ? payment.amount : existing?.amount ?? 0,
    purpose: payment.purpose || existing?.purpose || 'pay',
    order_id: orderId || existing?.order_id || null,
    session_token: sessionToken || existing?.session_token || null,
    status: payment.status || existing?.status || 'initiated',
    trx_id: payment.trxId || existing?.trx_id || null,
    provider: payment.provider || existing?.provider || null,
    sender_number: payment.senderNumber || existing?.sender_number || null,
    receiver_number: payment.receiverNumber || existing?.receiver_number || null,
    full_sms: payment.fullSms || existing?.full_sms || null,
    visitor_public_id: found.row.public_id,
    visitor_display_name: found.row.display_name,
    updated_at: now,
  };

  if (existing) {
    await prisma.demo_payments.update({
      where: { id: existing.id },
      data,
    });
    return existing.id;
  }

  const created = await prisma.demo_payments.create({
    data: {
      visitor_id: found.row.id,
      ...data,
      refund_status: 'none',
    },
  });
  return created.id;
}

/**
 * Update demo payment by orderId / sessionToken (works after visitor soft-expiry).
 */
async function updatePaymentByRefs({ orderId, sessionToken, status, amount, trxId, provider, senderNumber, receiverNumber, fullSms }) {
  let row = null;
  if (orderId) {
    row = await prisma.demo_payments.findFirst({
      where: { order_id: String(orderId) },
      orderBy: { id: 'desc' },
    });
  }
  if (!row && sessionToken) {
    row = await prisma.demo_payments.findFirst({
      where: { session_token: String(sessionToken) },
      orderBy: { id: 'desc' },
    });
  }
  if (!row) return null;

  const updated = await prisma.demo_payments.update({
    where: { id: row.id },
    data: {
      status: status || row.status,
      amount: amount != null ? amount : row.amount,
      trx_id: trxId || row.trx_id,
      provider: provider || row.provider,
      sender_number: senderNumber || row.sender_number,
      receiver_number: receiverNumber || row.receiver_number,
      full_sms: fullSms || row.full_sms,
      updated_at: new Date(),
    },
  });
  return updated.id;
}

function mapPaymentRow(r) {
  return {
    id: r.id,
    amount: Number(r.amount),
    purpose: r.purpose,
    orderId: r.order_id,
    sessionToken: r.session_token,
    status: r.status,
    trxId: r.trx_id,
    provider: r.provider,
    senderNumber: r.sender_number,
    receiverNumber: r.receiver_number,
    fullSms: r.full_sms,
    visitorPublicId: r.visitor_public_id,
    visitorDisplayName: r.visitor_display_name,
    refundStatus: r.refund_status || 'none',
    refundNote: r.refund_note,
    refundedAt: r.refunded_at,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
  };
}

async function listPayments(publicId, limit = 20) {
  const found = await getByPublicId(publicId);
  if (!found) return [];
  const rows = await prisma.demo_payments.findMany({
    where: { visitor_id: found.row.id },
    orderBy: { created_at: 'desc' },
    take: Math.min(50, limit),
  });
  return rows.map(mapPaymentRow);
}

/**
 * Admin: all sandbox test payments with full refund history visibility.
 */
async function listAllPaymentsAdmin({ limit = 100, offset = 0, refundStatus } = {}) {
  const where = {};
  if (refundStatus && refundStatus !== 'all') {
    where.refund_status = String(refundStatus);
  }
  const take = Math.min(200, Math.max(1, Number(limit) || 100));
  const skip = Math.max(0, Number(offset) || 0);
  const [rows, total] = await Promise.all([
    prisma.demo_payments.findMany({
      where,
      orderBy: { created_at: 'desc' },
      take,
      skip,
      include: {
        visitor: {
          select: { public_id: true, display_name: true, status: true, expires_at: true },
        },
      },
    }),
    prisma.demo_payments.count({ where }),
  ]);

  return {
    total,
    payments: rows.map((r) => ({
      ...mapPaymentRow(r),
      visitorPublicId: r.visitor_public_id || r.visitor?.public_id || null,
      visitorDisplayName: r.visitor_display_name || r.visitor?.display_name || null,
      visitorStatus: r.visitor?.status || null,
    })),
  };
}

async function setRefundStatus(paymentId, { refundStatus, refundNote } = {}) {
  const id = Number(paymentId);
  if (!Number.isFinite(id) || id < 1) return null;
  const allowed = new Set(['none', 'pending', 'refunded']);
  const next = String(refundStatus || '').toLowerCase();
  if (!allowed.has(next)) {
    const err = new Error('refundStatus must be none|pending|refunded');
    err.status = 400;
    err.code = 'INVALID_REFUND_STATUS';
    throw err;
  }
  const existing = await prisma.demo_payments.findUnique({ where: { id } });
  if (!existing) return null;
  return prisma.demo_payments.update({
    where: { id },
    data: {
      refund_status: next,
      refund_note: refundNote != null ? String(refundNote).slice(0, 255) : existing.refund_note,
      refunded_at: next === 'refunded' ? new Date() : null,
      updated_at: new Date(),
    },
  }).then(mapPaymentRow);
}

/**
 * Soft-expire active visitors past TTL; hard-delete only after 30 days
 * so admins can still refund within the window.
 */
async function purgeExpired() {
  const now = new Date();
  const hardDeleteBefore = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
  const refundedDeleteBefore = new Date(now.getTime() - REFUND_RETENTION_MS);

  await prisma.demo_visitors.updateMany({
    where: {
      status: STATUS.ACTIVE,
      expires_at: { lte: now },
    },
    data: { status: STATUS.EXPIRED },
  });

  const stale = await prisma.demo_visitors.findMany({
    where: {
      status: { in: [STATUS.EXPIRED, STATUS.PURGED] },
    },
    select: {
      id: true,
      expires_at: true,
      payments: {
        select: { refund_status: true, refunded_at: true },
      },
    },
    take: 500,
  });
  const ids = stale
    .filter((row) => {
      if (!row.payments.length) {
        return new Date(row.expires_at).getTime() <= hardDeleteBefore.getTime();
      }
      if (row.payments.some((payment) => payment.refund_status !== 'refunded')) {
        return false;
      }
      const latestRefundedAt = row.payments
        .map((payment) => payment.refunded_at)
        .filter(Boolean)
        .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0];
      if (!latestRefundedAt) return false;
      return new Date(latestRefundedAt).getTime() <= refundedDeleteBefore.getTime();
    })
    .map((row) => row.id);
  if (!ids.length) return { deleted: 0, expired: 0 };
  const result = await prisma.demo_visitors.deleteMany({
    where: { id: { in: ids } },
  });
  return { deleted: result.count, expired: 0 };
}

/**
 * Express middleware — attaches req.demoVisitor when cookie valid.
 * Does not block; controllers decide.
 */
async function attachDemoVisitor(req, _res, next) {
  try {
    req.demoVisitor = await findActiveByCookie(req);
  } catch (e) {
    console.warn('[demo-visitor] attach failed:', e.message);
    req.demoVisitor = null;
  }
  next();
}

function requireDemoVisitor(req, res, next) {
  if (!req.demoVisitor) {
    return res.status(401).json({
      success: false,
      error: 'DEMO_AUTH_REQUIRED',
      message: 'ডেমো অ্যাকাউন্ট দিয়ে লগইন করুন',
    });
  }
  return next();
}

module.exports = {
  COOKIE_NAME,
  STATUS,
  createVisitor,
  findActiveByCookie,
  getByPublicId,
  updateSettings,
  recordPayment,
  updatePaymentByRefs,
  listPayments,
  listAllPaymentsAdmin,
  setRefundStatus,
  purgeExpired,
  setDemoCookie,
  clearDemoCookie,
  readDemoCookie,
  attachDemoVisitor,
  requireDemoVisitor,
  publicVisitor,
  parseSettings,
  applyOverrides,
};
