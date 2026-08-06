'use strict';

/**
 * Admin-editable UI copy for the per-SIM "Custom ALL" chip + denied popup.
 * Stored in global_config so devices and entitlements APIs can read the same values.
 */

const prisma = require('../db/prisma');

const KEY_CHIP_LABEL = 'custom_all_chip_label';
const KEY_POPUP_NOTICE = 'custom_all_popup_notice';
const KEY_POPUP_TITLE = 'custom_all_popup_title';

const DEFAULTS = {
  chip_label: 'কাস্টম অল',
  popup_title: 'কাস্টম অল এসএমএস লকড',
  popup_notice:
    'আপনার এরকম প্যাকেজ অ্যাক্টিভ নেই যার কারণে আপনি কাস্টম অল এসএমএস মার্ক করতে পারছেন না। এটি ব্যবহার করতে পার্সোনাল / কাস্টম সেন্ডার প্যাকেজ কিনুন।',
};

async function readKey(key, fallback) {
  try {
    const row = await prisma.global_config.findUnique({ where: { config_key: key } });
    const v = row?.config_value != null ? String(row.config_value).trim() : '';
    return v || fallback;
  } catch (_) {
    return fallback;
  }
}

async function writeKey(key, value) {
  const v = String(value ?? '').trim();
  await prisma.global_config.upsert({
    where: { config_key: key },
    create: { config_key: key, config_value: v },
    update: { config_value: v },
  });
  return v;
}

async function getCustomAllUiConfig() {
  const [chip_label, popup_title, popup_notice] = await Promise.all([
    readKey(KEY_CHIP_LABEL, DEFAULTS.chip_label),
    readKey(KEY_POPUP_TITLE, DEFAULTS.popup_title),
    readKey(KEY_POPUP_NOTICE, DEFAULTS.popup_notice),
  ]);
  return { chip_label, popup_title, popup_notice };
}

async function setCustomAllUiConfig({ chip_label, popup_title, popup_notice } = {}) {
  const current = await getCustomAllUiConfig();
  const next = {
    chip_label:
      chip_label !== undefined ? String(chip_label).trim() || DEFAULTS.chip_label : current.chip_label,
    popup_title:
      popup_title !== undefined
        ? String(popup_title).trim() || DEFAULTS.popup_title
        : current.popup_title,
    popup_notice:
      popup_notice !== undefined
        ? String(popup_notice).trim() || DEFAULTS.popup_notice
        : current.popup_notice,
  };
  await Promise.all([
    writeKey(KEY_CHIP_LABEL, next.chip_label),
    writeKey(KEY_POPUP_TITLE, next.popup_title),
    writeKey(KEY_POPUP_NOTICE, next.popup_notice),
  ]);
  return next;
}

module.exports = {
  KEY_CHIP_LABEL,
  KEY_POPUP_NOTICE,
  KEY_POPUP_TITLE,
  DEFAULTS,
  getCustomAllUiConfig,
  setCustomAllUiConfig,
};
