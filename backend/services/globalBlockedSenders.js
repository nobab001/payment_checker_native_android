'use strict';

const prisma = require('../db/prisma');

const CONFIG_KEY = 'global_blocked_senders';

function normalizeSenderList(raw) {
  const list = Array.isArray(raw) ? raw : [];
  const out = [];
  const seen = new Set();
  for (const item of list) {
    const s = String(item || '').trim();
    if (!s) continue;
    const key = s.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(s);
  }
  return out;
}

async function getGlobalBlockedSenders() {
  try {
    const row = await prisma.global_config.findUnique({
      where: { config_key: CONFIG_KEY },
    });
    if (!row?.config_value) return [];
    try {
      return normalizeSenderList(JSON.parse(row.config_value));
    } catch (_) {
      return [];
    }
  } catch (e) {
    console.error('[globalBlockedSenders] read failed:', e.message);
    return [];
  }
}

async function setGlobalBlockedSenders(senders) {
  const normalized = normalizeSenderList(senders);
  await prisma.global_config.upsert({
    where: { config_key: CONFIG_KEY },
    create: { config_key: CONFIG_KEY, config_value: JSON.stringify(normalized) },
    update: { config_value: JSON.stringify(normalized) },
  });
  return normalized;
}

module.exports = {
  CONFIG_KEY,
  getGlobalBlockedSenders,
  setGlobalBlockedSenders,
  normalizeSenderList,
};
