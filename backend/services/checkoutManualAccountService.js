/**
 * Manual bank/card display accounts for checkout (no SMS template / SIM binding).
 * Merchants add account numbers here — customers copy & pay; verify via TrxID as usual.
 *
 * Live API credentials remain in merchant_accounts (provider=bank|card + keys).
 */

const prisma = require('../db/prisma');

let tableReady = false;

const VALID_TABS = new Set(['bank', 'card']);

async function ensureTable() {
  if (tableReady) return;
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS checkout_manual_accounts (
      id INT NOT NULL AUTO_INCREMENT,
      website_id INT NOT NULL,
      tab VARCHAR(16) NOT NULL DEFAULT 'bank',
      bank_name VARCHAR(120) NOT NULL,
      account_holder VARCHAR(120) NULL,
      account_number VARCHAR(64) NOT NULL,
      branch_name VARCHAR(120) NULL,
      routing_number VARCHAR(32) NULL,
      logo_url VARCHAR(512) NULL,
      instruction TEXT NULL,
      is_active TINYINT NOT NULL DEFAULT 1,
      sort_order INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      INDEX idx_cma_website_tab_active (website_id, tab, is_active),
      INDEX idx_cma_sort (website_id, sort_order)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);
  tableReady = true;
}

function toDto(row) {
  if (!row) return null;
  return {
    id: row.id,
    websiteId: row.website_id,
    tab: row.tab,
    bankName: row.bank_name,
    accountHolder: row.account_holder || '',
    accountNumber: row.account_number,
    branchName: row.branch_name || '',
    routingNumber: row.routing_number || '',
    logoUrl: row.logo_url || '',
    instruction: row.instruction || '',
    isActive: !!row.is_active,
    sortOrder: row.sort_order || 0,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function sanitizeTab(raw) {
  const t = String(raw || 'bank').trim().toLowerCase();
  return VALID_TABS.has(t) ? t : 'bank';
}

function buildData(body, isCreate) {
  const data = {};
  if (body.tab !== undefined) data.tab = sanitizeTab(body.tab);
  if (body.bank_name !== undefined || body.bankName !== undefined) {
    data.bank_name = String(body.bank_name ?? body.bankName ?? '').trim();
  }
  if (body.account_holder !== undefined || body.accountHolder !== undefined) {
    const v = body.account_holder ?? body.accountHolder;
    data.account_holder = v ? String(v).trim() : null;
  }
  if (body.account_number !== undefined || body.accountNumber !== undefined) {
    data.account_number = String(body.account_number ?? body.accountNumber ?? '').trim();
  }
  if (body.branch_name !== undefined || body.branchName !== undefined) {
    const v = body.branch_name ?? body.branchName;
    data.branch_name = v ? String(v).trim() : null;
  }
  if (body.routing_number !== undefined || body.routingNumber !== undefined) {
    const v = body.routing_number ?? body.routingNumber;
    data.routing_number = v ? String(v).trim() : null;
  }
  if (body.logo_url !== undefined || body.logoUrl !== undefined) {
    const v = body.logo_url ?? body.logoUrl;
    data.logo_url = v ? String(v).trim() : null;
  }
  if (body.instruction !== undefined) {
    data.instruction = body.instruction ? String(body.instruction).trim() : null;
  }
  if (body.sort_order !== undefined || body.sortOrder !== undefined) {
    data.sort_order = Number(body.sort_order ?? body.sortOrder) || 0;
  }
  if (!isCreate && body.is_active !== undefined) {
    data.is_active = body.is_active === false || body.is_active === 0 ? 0 : 1;
  }
  return data;
}

async function listForWebsite(websiteId, { activeOnly = false } = {}) {
  await ensureTable();
  const rows = await prisma.$queryRawUnsafe(
    `SELECT * FROM checkout_manual_accounts
      WHERE website_id = ?
      ${activeOnly ? 'AND is_active = 1' : ''}
      ORDER BY sort_order ASC, id ASC`,
    websiteId,
  );
  return rows.map(toDto);
}

async function listForCheckout(websiteId) {
  return listForWebsite(websiteId, { activeOnly: true });
}

async function create(websiteId, body) {
  await ensureTable();
  const data = buildData(body, true);
  if (!data.bank_name) throw new Error('BANK_NAME_REQUIRED');
  if (!data.account_number) throw new Error('ACCOUNT_NUMBER_REQUIRED');
  data.tab = data.tab || 'bank';
  await prisma.$executeRawUnsafe(
    `INSERT INTO checkout_manual_accounts
      (website_id, tab, bank_name, account_holder, account_number, branch_name, routing_number, logo_url, instruction, is_active, sort_order)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)`,
    websiteId,
    data.tab,
    data.bank_name,
    data.account_holder || null,
    data.account_number,
    data.branch_name || null,
    data.routing_number || null,
    data.logo_url || null,
    data.instruction || null,
    data.sort_order || 0,
  );
  const rows = await prisma.$queryRawUnsafe(
    'SELECT * FROM checkout_manual_accounts WHERE website_id = ? ORDER BY id DESC LIMIT 1',
    websiteId,
  );
  return toDto(rows[0]);
}

async function update(websiteId, accountId, body) {
  await ensureTable();
  const existing = await prisma.$queryRawUnsafe(
    'SELECT id FROM checkout_manual_accounts WHERE id = ? AND website_id = ? LIMIT 1',
    accountId,
    websiteId,
  );
  if (!existing.length) return null;
  const data = buildData(body, false);
  const sets = [];
  const vals = [];
  for (const [col, val] of Object.entries(data)) {
    sets.push(`${col} = ?`);
    vals.push(val);
  }
  if (!sets.length) return getById(websiteId, accountId);
  vals.push(accountId, websiteId);
  await prisma.$executeRawUnsafe(
    `UPDATE checkout_manual_accounts SET ${sets.join(', ')} WHERE id = ? AND website_id = ?`,
    ...vals,
  );
  return getById(websiteId, accountId);
}

async function getById(websiteId, accountId) {
  await ensureTable();
  const rows = await prisma.$queryRawUnsafe(
    'SELECT * FROM checkout_manual_accounts WHERE id = ? AND website_id = ? LIMIT 1',
    accountId,
    websiteId,
  );
  return toDto(rows[0]);
}

async function toggle(websiteId, accountId, active) {
  return update(websiteId, accountId, { is_active: active ? 1 : 0 });
}

async function remove(websiteId, accountId) {
  await ensureTable();
  await prisma.$executeRawUnsafe(
    'DELETE FROM checkout_manual_accounts WHERE id = ? AND website_id = ?',
    accountId,
    websiteId,
  );
}

async function reorder(websiteId, items) {
  await ensureTable();
  if (!Array.isArray(items)) return;
  for (const item of items) {
    const id = parseInt(item.id, 10);
    if (!Number.isFinite(id)) continue;
    const sort = Number(item.sortOrder ?? item.sort_order) || 0;
    await prisma.$executeRawUnsafe(
      'UPDATE checkout_manual_accounts SET sort_order = ? WHERE id = ? AND website_id = ?',
      sort,
      id,
      websiteId,
    );
  }
}

module.exports = {
  ensureTable,
  listForWebsite,
  listForCheckout,
  create,
  update,
  getById,
  toggle,
  remove,
  reorder,
  toDto,
};
