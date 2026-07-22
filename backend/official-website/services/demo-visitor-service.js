/**
 * Browser-based sandbox demo visitors — isolated from real merchants/users.
 */

const crypto = require('crypto');
const prisma = require('../../db/prisma');
const config = require('../config');
const { sanitizeOverrides, applyOverrides, defaultOverrides } = require('./session-store');

const COOKIE_NAME = 'pc_demo';
const STATUS = { ACTIVE: 'active', EXPIRED: 'expired', PURGED: 'purged' };

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

function publicVisitor(row) {
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
  const maxAge = Math.max(60, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
  const secure = String(process.env.APP_ENV || '').toLowerCase() === 'production' ? '; Secure' : '';
  const value = encodeURIComponent(`${publicId}.${token}`);
  res.setHeader(
    'Set-Cookie',
    `${COOKIE_NAME}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${maxAge}${secure}`,
  );
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
  if (!row || row.status !== STATUS.ACTIVE) return null;
  if (row.token_hash !== sha256(parsed.token)) return null;
  if (new Date(row.expires_at).getTime() <= Date.now()) {
    await prisma.demo_visitors.update({
      where: { id: row.id },
      data: { status: STATUS.EXPIRED },
    }).catch(() => {});
    return null;
  }

  const updated = await prisma.demo_visitors.update({
    where: { id: row.id },
    data: { last_seen_at: new Date() },
  });
  return publicVisitor(updated);
}

async function getByPublicId(publicId) {
  if (!publicId) return null;
  const row = await prisma.demo_visitors.findUnique({
    where: { public_id: String(publicId) },
  });
  if (!row || row.status !== STATUS.ACTIVE) return null;
  if (new Date(row.expires_at).getTime() <= Date.now()) return null;
  return { row, visitor: publicVisitor(row) };
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
  const found = await getByPublicId(publicId);
  if (!found) return null;
  await prisma.demo_payments.create({
    data: {
      visitor_id: found.row.id,
      amount: payment.amount,
      purpose: payment.purpose || 'pay',
      order_id: payment.orderId || null,
      session_token: payment.sessionToken || null,
      status: payment.status || 'initiated',
    },
  });
  return true;
}

async function listPayments(publicId, limit = 20) {
  const found = await getByPublicId(publicId);
  if (!found) return [];
  const rows = await prisma.demo_payments.findMany({
    where: { visitor_id: found.row.id },
    orderBy: { created_at: 'desc' },
    take: Math.min(50, limit),
  });
  return rows.map((r) => ({
    id: r.id,
    amount: Number(r.amount),
    purpose: r.purpose,
    orderId: r.order_id,
    status: r.status,
    createdAt: r.created_at,
  }));
}

async function purgeExpired() {
  const now = new Date();
  const expired = await prisma.demo_visitors.findMany({
    where: {
      OR: [
        { expires_at: { lte: now }, status: STATUS.ACTIVE },
        { status: STATUS.EXPIRED },
      ],
    },
    select: { id: true },
    take: 500,
  });
  if (!expired.length) return { deleted: 0 };
  const ids = expired.map((r) => r.id);
  // payments cascade
  const result = await prisma.demo_visitors.deleteMany({
    where: { id: { in: ids } },
  });
  return { deleted: result.count };
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
  listPayments,
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
