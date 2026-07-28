/**
 * Manual bank/card accounts + checkout helpline — merchant website settings.
 */
const prisma = require('../db/prisma');
const manualAccounts = require('../services/checkoutManualAccountService');
const helplineService = require('../services/checkoutHelplineService');
const merchantCache = require('../services/merchantCache');

async function findOwnedWebsite(id, userId) {
  const websiteId = parseInt(id, 10);
  if (!Number.isFinite(websiteId)) return null;
  const row = await prisma.gateway_layouts.findUnique({ where: { id: websiteId } });
  if (!row) return null;
  const uid = Number(userId);
  if (!Number.isFinite(uid) || row.user_id !== uid) return null;
  return row;
}

function parseLayoutConfig(raw) {
  if (!raw) return {};
  if (typeof raw === 'object') return raw;
  try { return JSON.parse(raw); } catch (_) { return {}; }
}

function parseHelpline(raw) {
  return helplineService.parseHelplineForCheckout(raw);
}

function normalizeHelplineBody(body) {
  const b = body?.helpline && typeof body.helpline === 'object' && !Array.isArray(body.helpline)
    ? body.helpline
    : body;
  const icon = String(b?.icon || 'whatsapp').toLowerCase();
  const value = String(b?.value ?? b?.url ?? b?.link ?? '').trim();
  const label = String(b?.label ?? b?.title ?? '').trim();
  const enabled = b?.enabled !== false && b?.enabled !== 0 && b?.isActive !== false;
  if (!value && !enabled) {
    return { enabled: false, icon: 'whatsapp', label: '', value: '', url: '' };
  }
  const cfg = helplineService.normalizeHelplineConfig({ enabled, icon, label, value });
  return cfg;
}

async function listManualAccounts(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const tab = req.query.tab;
    let list = await manualAccounts.listForWebsite(row.id);
    if (tab) list = list.filter((a) => a.tab === String(tab).toLowerCase());
    return res.json({ success: true, manualAccounts: list });
  } catch (e) {
    console.error('[ManualAccounts] list:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function createManualAccount(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const created = await manualAccounts.create(row.id, req.body || {});
    merchantCache.invalidate(row.api_key);
    return res.status(201).json({ success: true, manualAccount: created });
  } catch (e) {
    if (e.message === 'BANK_NAME_REQUIRED' || e.message === 'ACCOUNT_NUMBER_REQUIRED') {
      return res.status(400).json({ success: false, error: e.message });
    }
    console.error('[ManualAccounts] create:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function updateManualAccount(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const accountId = parseInt(req.params.accountId, 10);
    const updated = await manualAccounts.update(row.id, accountId, req.body || {});
    if (!updated) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, manualAccount: updated });
  } catch (e) {
    console.error('[ManualAccounts] update:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function toggleManualAccount(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const accountId = parseInt(req.params.accountId, 10);
    const active = req.body?.isActive !== false && req.body?.is_active !== 0;
    const updated = await manualAccounts.toggle(row.id, accountId, active);
    if (!updated) return res.status(404).json({ success: false, error: 'ACCOUNT_NOT_FOUND' });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, manualAccount: updated });
  } catch (e) {
    console.error('[ManualAccounts] toggle:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function deleteManualAccount(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const accountId = parseInt(req.params.accountId, 10);
    await manualAccounts.remove(row.id, accountId);
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true });
  } catch (e) {
    console.error('[ManualAccounts] delete:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

async function reorderManualAccounts(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    await manualAccounts.reorder(row.id, req.body?.items || req.body?.manualAccounts || []);
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true });
  } catch (e) {
    console.error('[ManualAccounts] reorder:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** GET checkout helpline (single config). */
async function getCheckoutHelpline(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const cfg = parseLayoutConfig(row.layout_config);
    const helpline = helplineService.normalizeHelplineConfig(cfg.checkout_helpline);
    return res.json({ success: true, helpline });
  } catch (e) {
    console.error('[Helpline] get:', e.message);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

/** PUT checkout helpline — { enabled, icon, label, value } */
async function saveCheckoutHelpline(req, res) {
  try {
    const row = await findOwnedWebsite(req.params.id, req.user.userId);
    if (!row) return res.status(404).json({ success: false, error: 'WEBSITE_NOT_FOUND' });
    const helpline = normalizeHelplineBody(req.body || {});
    const cfg = parseLayoutConfig(row.layout_config);
    cfg.checkout_helpline = {
      enabled: helpline.enabled,
      icon: helpline.icon,
      label: helpline.label,
      value: helpline.value,
    };
    await prisma.gateway_layouts.update({
      where: { id: row.id },
      data: { layout_config: JSON.stringify(cfg), updated_at: new Date() },
    });
    merchantCache.invalidate(row.api_key);
    return res.json({ success: true, helpline });
  } catch (e) {
    console.error('[Helpline] save:', e.message, e.stack);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
}

module.exports = {
  listManualAccounts,
  createManualAccount,
  updateManualAccount,
  toggleManualAccount,
  deleteManualAccount,
  reorderManualAccounts,
  getCheckoutHelpline,
  saveCheckoutHelpline,
  parseHelpline,
};
