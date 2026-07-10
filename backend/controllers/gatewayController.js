const prisma = require('../db/prisma');
const dataSyncCache = require('../services/dataSyncCache');
const layoutHelper = require('../services/checkoutLayoutHelper');

let simBindingsTableReady = false;

async function ensureSimBindingsTable() {
  if (simBindingsTableReady) return;
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS sim_slot_bindings (
      id INT NOT NULL AUTO_INCREMENT,
      user_id VARCHAR(255) NOT NULL,
      device_id VARCHAR(255) NOT NULL DEFAULT '',
      sim_slot TINYINT NOT NULL DEFAULT 1,
      phone_number VARCHAR(20) NOT NULL DEFAULT '',
      is_active TINYINT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_user_device_slot (user_id, device_id, sim_slot),
      INDEX idx_user_number_active (user_id, phone_number, is_active)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS sim_number_profiles (
      id INT NOT NULL AUTO_INCREMENT,
      user_id VARCHAR(255) NOT NULL,
      device_id VARCHAR(255) NOT NULL DEFAULT '',
      sim_slot TINYINT NOT NULL DEFAULT 1,
      phone_number VARCHAR(20) NOT NULL DEFAULT '',
      profile_json TEXT NOT NULL,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_user_device_slot_number (user_id, device_id, sim_slot, phone_number)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);
  simBindingsTableReady = true;
}

function normalizePhoneNumber(raw) {
  if (!raw || typeof raw !== 'string') return '';
  return raw.replace(/\D/g, '').slice(-11);
}

async function getDeviceDisplayName(userId, deviceId) {
  const row = await prisma.registered_devices.findFirst({
    where: { user_id: Number(userId), device_id: String(deviceId) },
    select: { custom_device_name: true, device_model: true },
  });
  if (!row) return 'অন্য ডিভাইস';
  return row.custom_device_name || row.device_model || 'অন্য ডিভাইস';
}

async function fetchMethodsProfileForNumber(userId, phoneNumber) {
  await ensureSimBindingsTable();

  const activeOwner = await prisma.$queryRaw`
    SELECT device_id, sim_slot
    FROM sim_slot_bindings
    WHERE user_id = ${String(userId)}
      AND phone_number = ${phoneNumber}
      AND is_active = 1
    ORDER BY updated_at DESC
    LIMIT 1
  `;

  if (activeOwner[0]) {
    const owner = activeOwner[0];
    return prisma.gateway_methods.findMany({
      where: {
        user_id: String(userId),
        device_id: String(owner.device_id),
        sim_slot: Number(owner.sim_slot),
        number: phoneNumber,
      },
      orderBy: [{ priority: 'asc' }],
    });
  }

  const anchor = await prisma.gateway_methods.findFirst({
    where: { user_id: String(userId), number: phoneNumber },
    orderBy: { updated_at: 'desc' },
  });
  if (!anchor) return [];

  return prisma.gateway_methods.findMany({
    where: {
      user_id: String(userId),
      device_id: anchor.device_id,
      sim_slot: anchor.sim_slot,
      number: phoneNumber,
    },
    orderBy: [{ priority: 'asc' }],
  });
}

async function fetchDeviceSlotProfile(userId, deviceId, simSlot, phoneNumber) {
  await ensureSimBindingsTable();
  const rows = await prisma.$queryRaw`
    SELECT profile_json
    FROM sim_number_profiles
    WHERE user_id = ${String(userId)}
      AND device_id = ${String(deviceId)}
      AND sim_slot = ${simSlot}
      AND phone_number = ${phoneNumber}
    LIMIT 1
  `;
  if (!rows[0]?.profile_json) return null;
  try {
    const parsed = JSON.parse(rows[0].profile_json);
    return Array.isArray(parsed) ? parsed : null;
  } catch (e) {
    return null;
  }
}

async function fetchGlobalProfileForNumber(userId, phoneNumber) {
  await ensureSimBindingsTable();
  const rows = await prisma.$queryRaw`
    SELECT profile_json
    FROM sim_number_profiles
    WHERE user_id = ${String(userId)}
      AND phone_number = ${phoneNumber}
    ORDER BY updated_at DESC
    LIMIT 1
  `;
  if (!rows[0]?.profile_json) return null;
  try {
    const parsed = JSON.parse(rows[0].profile_json);
    return Array.isArray(parsed) ? parsed : null;
  } catch (e) {
    return null;
  }
}

async function saveDeviceSlotProfile(userId, deviceId, simSlot, phoneNumber) {
  if (!phoneNumber || phoneNumber.length !== 11) return;
  await ensureSimBindingsTable();
  const methods = await prisma.gateway_methods.findMany({
    where: {
      user_id: String(userId),
      device_id: String(deviceId),
      sim_slot: simSlot,
      number: phoneNumber,
    },
    orderBy: [{ priority: 'asc' }],
  });
  const profile = methods.map((m) => ({
    template_id: m.template_id,
    provider: m.provider,
    is_enabled: m.is_enabled,
    display_name: m.display_name || null,
  }));
  const json = JSON.stringify(profile);
  await prisma.$executeRaw`
    INSERT INTO sim_number_profiles (user_id, device_id, sim_slot, phone_number, profile_json)
    VALUES (${String(userId)}, ${String(deviceId)}, ${simSlot}, ${phoneNumber}, ${json})
    ON DUPLICATE KEY UPDATE profile_json = VALUES(profile_json), updated_at = CURRENT_TIMESTAMP
  `;
}

async function findActiveConflictOnOtherDevice(userId, deviceId, phoneNumber) {
  const bindingConflict = await findActiveConflictBinding(userId, deviceId, phoneNumber);
  if (bindingConflict) return bindingConflict;

  const otherEnabled = await prisma.gateway_methods.findFirst({
    where: {
      user_id: String(userId),
      number: phoneNumber,
      is_enabled: 1,
      device_id: { not: String(deviceId) },
    },
    orderBy: { updated_at: 'desc' },
  });
  if (!otherEnabled) return null;

  return {
    device_id: otherEnabled.device_id,
    sim_slot: otherEnabled.sim_slot,
    phone_number: phoneNumber,
    is_active: 1,
    source: 'gateway_methods',
  };
}

async function findActiveConflictBinding(userId, deviceId, phoneNumber) {
  await ensureSimBindingsTable();
  const rows = await prisma.$queryRaw`
    SELECT id, user_id, device_id, sim_slot, phone_number, is_active
    FROM sim_slot_bindings
    WHERE user_id = ${String(userId)}
      AND phone_number = ${phoneNumber}
      AND is_active = 1
      AND device_id <> ${String(deviceId)}
    LIMIT 1
  `;
  return rows[0] || null;
}

async function findSlotBinding(userId, deviceId, simSlot) {
  await ensureSimBindingsTable();
  const rows = await prisma.$queryRaw`
    SELECT id, user_id, device_id, sim_slot, phone_number, is_active
    FROM sim_slot_bindings
    WHERE user_id = ${String(userId)}
      AND device_id = ${String(deviceId)}
      AND sim_slot = ${simSlot}
    LIMIT 1
  `;
  return rows[0] || null;
}

async function findBindingsForPhoneOnOtherDevices(userId, deviceId, phoneNumber) {
  await ensureSimBindingsTable();
  return prisma.$queryRaw`
    SELECT id, user_id, device_id, sim_slot, phone_number, is_active
    FROM sim_slot_bindings
    WHERE user_id = ${String(userId)}
      AND phone_number = ${phoneNumber}
      AND device_id <> ${String(deviceId)}
  `;
}

async function upsertSlotBinding(userId, deviceId, simSlot, phoneNumber, isActive) {
  await ensureSimBindingsTable();
  const activeVal = isActive ? 1 : 0;
  await prisma.$executeRaw`
    INSERT INTO sim_slot_bindings (user_id, device_id, sim_slot, phone_number, is_active)
    VALUES (${String(userId)}, ${String(deviceId)}, ${simSlot}, ${phoneNumber}, ${activeVal})
    ON DUPLICATE KEY UPDATE
      phone_number = VALUES(phone_number),
      is_active = VALUES(is_active),
      updated_at = CURRENT_TIMESTAMP
  `;
}

async function deactivatePhoneGlobally(userId, phoneNumber) {
  if (!phoneNumber) return;
  await ensureSimBindingsTable();
  await prisma.$executeRaw`
    UPDATE sim_slot_bindings
    SET is_active = 0, updated_at = CURRENT_TIMESTAMP
    WHERE user_id = ${String(userId)} AND phone_number = ${phoneNumber}
  `;
}

async function deactivateBindingById(bindingId) {
  await prisma.$executeRaw`
    UPDATE sim_slot_bindings
    SET is_active = 0, updated_at = CURRENT_TIMESTAMP
    WHERE id = ${bindingId}
  `;
}

async function resolveProfileForPhoneNumber(userId, deviceId, simSlot, phoneNumber) {
  const deviceSlot = await fetchDeviceSlotProfile(userId, deviceId, simSlot, phoneNumber);
  if (deviceSlot?.length) return deviceSlot;

  const global = await fetchGlobalProfileForNumber(userId, phoneNumber);
  if (global?.length) return global;

  const localMethods = await prisma.gateway_methods.findMany({
    where: {
      user_id: String(userId),
      device_id: String(deviceId),
      sim_slot: simSlot,
      number: phoneNumber,
    },
    orderBy: [{ priority: 'asc' }],
  });
  if (localMethods.length) return localMethods;

  return fetchMethodsProfileForNumber(userId, phoneNumber);
}

async function fetchLiveMethodsForBinding(userId, binding, phoneNumber) {
  if (!binding) return [];
  return prisma.gateway_methods.findMany({
    where: {
      user_id: String(userId),
      device_id: String(binding.device_id),
      sim_slot: Number(binding.sim_slot),
      number: phoneNumber,
    },
    orderBy: [{ priority: 'asc' }],
  });
}

async function applyProfileToSlot(userId, deviceId, simSlot, phoneNumber, profileMethods, enabledDefault = 1) {
  await prisma.gateway_methods.deleteMany({
    where: {
      user_id: String(userId),
      device_id: String(deviceId),
      sim_slot: simSlot,
    },
  });

  if (!profileMethods.length) return;

  const maxPriorityRow = await prisma.gateway_methods.aggregate({
    where: { user_id: String(userId), device_id: String(deviceId) },
    _max: { priority: true },
  });
  let nextPriority = (maxPriorityRow._max.priority || 0) + 1;

  for (const src of profileMethods) {
    await prisma.gateway_methods.create({
      data: {
        user_id: String(userId),
        device_id: String(deviceId),
        template_id: src.template_id || null,
        sim_slot: simSlot,
        provider: src.provider,
        number: phoneNumber,
        display_name: src.display_name || null,
        is_enabled: src.is_enabled !== undefined && src.is_enabled !== null ? src.is_enabled : enabledDefault,
        priority: nextPriority++,
        custom_patterns: src.custom_patterns || null,
      },
    });
  }
}

async function emitGatewaySync(req, userId, deviceId, data) {
  const io = req.app.get('io');
  if (io && deviceId) {
    io.to(`${userId}:${deviceId}`).emit('sync_gateway_methods', data);
  }
}

async function touchDeviceSync(userId, deviceId, opts = {}) {
  await dataSyncCache.bumpDeviceSyncVersion(userId, deviceId);
  if (opts.userCustomChanged) {
    await dataSyncCache.bumpUserCustomTemplateVersion(userId);
  }
}

async function fetchGatewayMethodsForUser(userId, deviceId) {
  if (deviceId) {
    try {
      await prisma.gateway_methods.updateMany({
        where: { user_id: String(userId), device_id: '' },
        data: { device_id: String(deviceId) }
      });
    } catch(e) { console.error('Fallback update error', e); }
  }
  const rows = await prisma.$queryRaw`
    SELECT gm.id, gm.sim_slot, gm.provider, gm.number, gm.display_name, gm.is_enabled, gm.priority, gm.template_id, gm.custom_patterns, gm.created_at,
            t.sender_id, t.sender_number, t.matching_keyword, t.regex_pattern AS regex_pattern, 
            COALESCE(t.is_official, 1) AS is_official, COALESCE(t.is_parseable, 1) AS is_parseable,
            cvt.single_number_instruction, cvt.multiple_number_instruction
       FROM gateway_methods gm
  LEFT JOIN sms_templates t ON gm.template_id = t.id
  LEFT JOIN checkout_view_templates cvt ON cvt.sms_template_id = t.id
      WHERE gm.user_id = ${userId} AND gm.device_id = ${deviceId}
        AND (gm.template_id IS NULL OR t.id IS NOT NULL)
      ORDER BY gm.priority ASC, gm.sim_slot ASC
  `;

  return rows.map(r => {
    const obj = {};
    for (const key in r) {
      if (typeof r[key] === 'bigint') obj[key] = Number(r[key]);
      else obj[key] = r[key];
    }
    // Parse JSON array from DB text
    if (obj.custom_patterns) {
      try { obj.custom_patterns = JSON.parse(obj.custom_patterns); }
      catch(e) { obj.custom_patterns = []; }
    } else {
      obj.custom_patterns = [];
    }
    return obj;
  });
}

// =============================================================================
// GET /api/gateway/methods
// ব্যবহারকারীর সব গেটওয়ে মেথড লোড করে (priority অনুযায়ী সাজানো)
// =============================================================================
async function getGatewayMethods(req, res) {
  try {
    const userId = req.user.userId;

    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const lastSync = parseInt(req.headers['x-gateway-last-sync'] || '0', 10);
    const serverVersion = await dataSyncCache.getDeviceSyncVersion(userId, deviceId);

    if (dataSyncCache.isClientSyncCurrent(lastSync, serverVersion)) {
      return res.json({
        success: true,
        data: null,
        data_version: serverVersion,
        unchanged: true,
      });
    }

    const data = await fetchGatewayMethodsForUser(userId, deviceId);

    return res.json({ success: true, data, data_version: serverVersion, unchanged: false });
  } catch (error) {
    console.error('[GATEWAY] getGatewayMethods error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// PATCH /api/gateway/priority
// Drag & Drop-এর পর সব মেথডের priority ক্রম একসাথে আপডেট করে
//
// Body: { items: [{ id, priority }, ...] }
// =============================================================================
async function updatePriority(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const { items } = req.body;

    if (!Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: 'items array প্রয়োজন' });
    }

    // প্রতিটি item-এর জন্য আলাদা UPDATE — user_id দিয়ে সুরক্ষিত
    for (const item of items) {
      if (typeof item.id !== 'number' || typeof item.priority !== 'number') continue;

      await prisma.gateway_methods.updateMany({
        where: { id: item.id, user_id: String(userId), device_id: String(deviceId) },
        data: { priority: item.priority }
      });
    }

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Priority updated for ${items.length} items | User: ${userId}`);
    
    const io = req.app.get('io');
    const targetDeviceId = req.body.deviceId || req.user.deviceId;
    if (io && targetDeviceId) {
        io.to(`${userId}:${targetDeviceId}`).emit("sync_gateway_methods", data);
    } else if (io) {
        io.emit("sync_gateway_methods", data);
    }

    await touchDeviceSync(userId, deviceId);

    return res.json({ success: true, message: 'Priority ক্রম সফলভাবে আপডেট হয়েছে।', data });

  } catch (error) {
    console.error('[GATEWAY] updatePriority error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// PATCH /api/gateway/methods/:id/toggle
// একটি মেথড চালু অথবা বন্ধ করা
//
// Body: { is_enabled: 0 | 1 }
// =============================================================================
async function toggleMethod(req, res) {
  try {
    const userId   = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const methodId = parseInt(req.params.id);
    const { is_enabled } = req.body;

    if (![0, 1, false, true].includes(is_enabled)) {
      return res.status(400).json({ error: 'is_enabled অবশ্যই boolean বা 0/1 হতে হবে' });
    }

    const enabledBool = !!is_enabled;

    const result = await prisma.gateway_methods.updateMany({
      where: { id: methodId, user_id: String(userId), device_id: String(deviceId) },
      data: { is_enabled: enabledBool ? 1 : 0 }
    });

    if (result.count === 0) {
      return res.status(404).json({ error: 'মেথড পাওয়া যায়নি' });
    }

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    const status = enabledBool ? 'চালু' : 'বন্ধ';
    console.log(`[GATEWAY] Method ${methodId} ${status} | User: ${userId}`);

    const io = req.app.get('io');
    const targetDeviceId = req.body.deviceId || req.user.deviceId;
    if (io && targetDeviceId) {
        io.to(`${userId}:${targetDeviceId}`).emit("sync_gateway_methods", data);
    }

    await touchDeviceSync(userId, deviceId);

    const toggledMethod = await prisma.gateway_methods.findFirst({
      where: { id: methodId, user_id: String(userId), device_id: String(deviceId) },
    });
    if (toggledMethod?.number) {
      await saveDeviceSlotProfile(userId, deviceId, toggledMethod.sim_slot, toggledMethod.number);
    }

    return res.json({ success: true, message: `মেথড ${status} করা হয়েছে।`, data });

  } catch (error) {
    console.error('[GATEWAY] toggleMethod error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// PATCH /api/gateway/methods/:id
// মেথডের ফোন নম্বর ও প্রদর্শন নাম আপডেট করা
//
// Body: { number?, display_name? }
// =============================================================================
async function updateMethod(req, res) {
  try {
    const userId   = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const methodId = parseInt(req.params.id);
    const { number, display_name, template_id } = req.body;

    if (number === undefined && display_name === undefined && template_id === undefined) {
      return res.status(400).json({ error: 'number, display_name বা template_id প্রয়োজন' });
    }

    const data = {};
    if (number !== undefined) data.number = number || null;
    if (display_name !== undefined) data.display_name = display_name || null;
    if (template_id !== undefined) data.template_id = template_id || null;

    const result = await prisma.gateway_methods.updateMany({
      where: { id: methodId, user_id: String(userId), device_id: String(deviceId) },
      data
    });

    if (result.count === 0) {
      return res.status(404).json({ error: 'মেথড পাওয়া যায়নি' });
    }

    const updatedData = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Method ${methodId} updated | User: ${userId}`);

    const io = req.app.get('io');
    const targetDeviceId = req.body.deviceId || req.user.deviceId;
    if (io && targetDeviceId) {
        io.to(`${userId}:${targetDeviceId}`).emit("sync_gateway_methods", updatedData);
    }

    await touchDeviceSync(userId, deviceId);

    return res.json({ success: true, message: 'মেথড আপডেট হয়েছে।', data: updatedData });

  } catch (error) {
    console.error('[GATEWAY] updateMethod error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// GET /api/gateway/templates
// সব সক্রিয় টেমপ্লেট লোড করা
// =============================================================================
async function getTemplates(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';

    // Repair legacy custom templates that were incorrectly marked parseable
    await prisma.sms_templates.updateMany({
      where: { user_id: userId, is_official: 0, is_parseable: 1 },
      data: { is_parseable: 0 },
    });

    const lastSync = parseInt(req.headers['x-gateway-last-sync'] || '0', 10);
    const serverVersion = await dataSyncCache.getDeviceSyncVersion(userId, deviceId);

    if (dataSyncCache.isClientSyncCurrent(lastSync, serverVersion)) {
      return res.json({
        success: true,
        templates: null,
        data_version: serverVersion,
        unchanged: true,
      });
    }

    const officialRows = await dataSyncCache.getOfficialTemplatesForAdmin();
    const customRows = await prisma.sms_templates.findMany({
      where: { is_official: 0, user_id: userId },
      select: {
        id: true,
        template_name: true,
        sender_id: true,
        sender_number: true,
        matching_keyword: true,
        regex_pattern: true,
        is_official: true,
        is_active: true,
        device_id: true,
        is_parseable: true,
      }
    });

    const rows = [...officialRows, ...customRows];

    // Per-template provider logos (uploaded via the admin Global Checkout Design)
    // so each template can show its brand logo on the device page.
    const { providerBranding: savedBranding } = await layoutHelper.loadGlobalCheckoutDefaults();

    const formatted = rows.map(r => {
      const isOther = r.is_official === 0 && r.device_id !== deviceId;
      return {
        id: r.id,
        template_name: r.template_name,
        sender_id: r.sender_id,
        sender_number: r.sender_number,
        matching_keyword: r.matching_keyword,
        regex_pattern: r.regex_pattern || '',
        is_official: r.is_official,
        is_active: isOther ? 0 : r.is_active,
        is_parseable: r.is_parseable ?? 1,
        is_other_device: isOther,
        is_admin_archive: r.is_official === 1 && (r.is_parseable ?? 1) === 0,
        logo_url: (savedBranding && savedBranding[layoutHelper.providerKeyForTemplate(r.id)]?.logoUrl) || null,
      };
    });

    return res.json({ success: true, templates: formatted, data_version: serverVersion, unchanged: false });
  } catch (error) {
    console.error('[GATEWAY] getTemplates error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/methods
// নতুন গেটওয়ে মেথড যোগ করা
// =============================================================================
async function addGatewayMethod(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const { sim_slot, provider, template_id, number } = req.body;

    if (!sim_slot || !provider) {
      return res.status(400).json({ error: 'sim_slot এবং provider আবশ্যক' });
    }

    const maxPriorityRow = await prisma.gateway_methods.aggregate({
      where: { user_id: String(userId), device_id: String(deviceId) },
      _max: { priority: true }
    });
    
    const nextPriority = (maxPriorityRow._max.priority || 0) + 1;

    const result = await prisma.gateway_methods.create({
      data: {
        user_id: String(userId),
        device_id: String(deviceId),
        template_id: template_id || null,
        sim_slot,
        provider,
        number: number || null,
        is_enabled: 1,
        priority: nextPriority
      }
    });

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Gateway method added | User: ${userId} | Slot: ${sim_slot} | Provider: ${provider}`);

    const io = req.app.get('io');
    const targetDeviceId = req.body.deviceId || req.user.deviceId;
    if (io && targetDeviceId) {
        io.to(`${userId}:${targetDeviceId}`).emit("sync_gateway_methods", data);
    }

    await touchDeviceSync(userId, deviceId);

    if (number) {
      await saveDeviceSlotProfile(userId, deviceId, sim_slot, normalizePhoneNumber(number));
    }

    return res.json({ success: true, id: result.id, message: 'মেথড সফলভাবে যোগ করা হয়েছে।', data });
  } catch (error) {
    console.error('[GATEWAY] addGatewayMethod error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/methods/:id/custom-templates
// অ্যাডমিন প্যানেল থেকে কাস্টম এসএমএস বডি রিসিভ করে ডায়নামিক রেজেক্স বানানো
//
// Body: { full_sms: "..." }
// =============================================================================
function generateCustomRegex(smsText) {
    if (!smsText) return '';
    
    // Split the text by matching {xxx} tags
    const tokens = smsText.split(/(\{[a-zA-Z0-9_]+\})/g);
    
    const result = tokens.map(token => {
        if (token.startsWith('{') && token.endsWith('}')) {
            const tag = token.slice(1, -1).toLowerCase();
            if (tag === 'amount') return '(?<amount>[\\d,\\.]+)';
            if (tag === 'sender') return '(?<sender>[\\d*xX]+)';
            if (tag === 'trxid') return '(?<trxid>[A-Za-z0-9]+)';
            if (tag === 'random') return '(.*)';
            return '(.*?)'; // Fallback for custom tags like {fee}, {balance}
        } else {
            // Escape special regex characters in plain text
            return token.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
        }
    }).join('');
    
    return `^${result}$`;
}

async function addCustomTemplate(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const methodId = parseInt(req.params.id);
    const { full_sms } = req.body;

    if (!full_sms) {
      return res.status(400).json({ error: 'full_sms আবশ্যক' });
    }

    const method = await prisma.gateway_methods.findFirst({
      where: { id: methodId, user_id: String(userId), device_id: String(deviceId) }
    });

    if (!method) {
      return res.status(404).json({ error: 'মেথড পাওয়া যায়নি' });
    }

    const newPattern = generateCustomRegex(full_sms.trim());
    let patterns = [];
    if (method.custom_patterns) {
      try { patterns = JSON.parse(method.custom_patterns); } catch (e) {}
    }

    // Limit to max 5 templates
    if (patterns.length >= 5) {
      return res.status(400).json({ error: 'সর্বোচ্চ ৫টি কাস্টম টেমপ্লেট যোগ করা যাবে' });
    }

    patterns.push(newPattern);

    await prisma.$executeRaw`UPDATE gateway_methods SET custom_patterns = ${JSON.stringify(patterns)} WHERE id = ${methodId}`;

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Custom template added to method ${methodId} | User: ${userId}`);

    const io = req.app.get('io');
    const targetDeviceId = req.body.deviceId || req.user.deviceId;
    if (io && targetDeviceId) {
        io.to(`${userId}:${targetDeviceId}`).emit("sync_gateway_methods", data);
    }

    await touchDeviceSync(userId, deviceId, { userCustomChanged: true });

    return res.json({ success: true, message: 'কাস্টম টেমপ্লেট যোগ করা হয়েছে।', data });
  } catch (error) {
    console.error('[GATEWAY] addCustomTemplate error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function findOfficialArchiveBySender(senderId) {
  const rows = await prisma.$queryRaw`
    SELECT id, template_name, sender_id, sender_number, matching_keyword, regex_pattern,
           is_official, is_active, is_parseable, user_id, device_id
    FROM sms_templates
    WHERE is_official = 1 AND is_parseable = 0 AND LOWER(sender_id) = LOWER(${senderId})
    LIMIT 1
  `;
  return rows[0] || null;
}

async function findUserPersonalBySender(userId, senderId) {
  const rows = await prisma.$queryRaw`
    SELECT id, template_name, sender_id, sender_number, matching_keyword, regex_pattern,
           is_official, is_active, is_parseable, user_id, device_id
    FROM sms_templates
    WHERE user_id = ${userId} AND is_official = 0 AND LOWER(sender_id) = LOWER(${senderId})
    ORDER BY id ASC
    LIMIT 1
  `;
  return rows[0] || null;
}

async function gatewayMethodExistsForSender(userId, deviceId, simSlot, senderId) {
  const rows = await prisma.$queryRaw`
    SELECT gm.id
    FROM gateway_methods gm
    INNER JOIN sms_templates t ON gm.template_id = t.id
    WHERE gm.user_id = ${String(userId)}
      AND gm.device_id = ${String(deviceId)}
      AND gm.sim_slot = ${simSlot}
      AND LOWER(t.sender_id) = LOWER(${senderId})
    LIMIT 1
  `;
  return rows[0] ? Number(rows[0].id) : null;
}

/**
 * GET /api/gateway/custom-sender/suggestions?q=gp
 * Admin archive templates (is_parseable=0) visible to all users.
 */
async function getCustomSenderSuggestions(req, res) {
  try {
    const q = String(req.query.q || '').trim();
    if (!q) {
      return res.json({ success: true, suggestions: [] });
    }
    const like = `%${q.toLowerCase()}%`;
    const rows = await prisma.$queryRaw`
      SELECT id, template_name, sender_id, sender_number, is_parseable, is_official
      FROM sms_templates
      WHERE is_official = 1 AND is_parseable = 0
        AND (LOWER(sender_id) LIKE ${like} OR LOWER(template_name) LIKE ${like})
      ORDER BY template_name ASC
      LIMIT 8
    `;
    return res.json({
      success: true,
      suggestions: rows.map((r) => ({
        id: Number(r.id),
        template_name: r.template_name,
        sender_id: r.sender_id,
        sender_number: r.sender_number || '',
        is_parseable: Number(r.is_parseable ?? 0),
        is_official: 1,
        is_admin_archive: true,
      })),
    });
  } catch (error) {
    console.error('[GATEWAY] getCustomSenderSuggestions error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function addCustomSender(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const { sim_slot, sender_id, official_template_id, create_personal } = req.body;
    const cleanSenderId = typeof sender_id === 'string' ? sender_id.trim() : '';
    const simSlotNum = parseInt(sim_slot, 10);
    const officialTemplateId = official_template_id ? parseInt(official_template_id, 10) : null;
    const forcePersonal = create_personal === true || create_personal === 1 || create_personal === '1';

    if (!Number.isInteger(simSlotNum) || simSlotNum < 1 || simSlotNum > 2) {
      return res.status(400).json({ error: 'sim_slot ১ বা ২ হতে হবে' });
    }
    if (!cleanSenderId) {
      return res.status(400).json({ error: 'sender_id আবশ্যক' });
    }

    // Verify user authorization for custom sender feature:
    // User must either have has_custom_sender_addon === 1 OR their active subscription plan must allow custom sender.
    const user = await prisma.users.findUnique({
      where: { id: userId },
      select: { is_paid: true, active_plan_name: true, role: true, has_custom_sender_addon: true, custom_sender_ends_at: true }
    });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    let isAllowed = false;
    if (user.role === 'admin') {
      isAllowed = true;
    } else if (user.has_custom_sender_addon === 1) {
      if (user.custom_sender_ends_at) {
        const endsAt = new Date(user.custom_sender_ends_at);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        isAllowed = endsAt >= today;
      } else {
        isAllowed = true;
      }
    }

    if (!isAllowed) {
      return res.status(403).json({
        success: false,
        error: 'FEATURE_GATED',
        message: 'কাস্টম সেন্ডার আইডি ব্যবহার করতে হলে আপনার প্যাকেজ আপগ্রেড করুন অথবা অ্যাড-অন কিনুন।'
      });
    }

    const duplicateMethodId = await gatewayMethodExistsForSender(userId, deviceId, simSlotNum, cleanSenderId);
    if (duplicateMethodId) {
      return res.status(400).json({
        success: false,
        error: 'SENDER_ALREADY_ON_SLOT',
        message: 'এই সেন্ডার আইডি ইতিমধ্যেই এই সিম স্লটে যুক্ত আছে।',
      });
    }

    let template = null;
    let templateSource = 'personal';

    if (officialTemplateId) {
      template = await prisma.sms_templates.findFirst({
        where: { id: officialTemplateId, is_official: 1, is_parseable: 0 },
      });
      if (!template) {
        return res.status(404).json({
          success: false,
          error: 'OFFICIAL_TEMPLATE_NOT_FOUND',
          message: 'এডমিন আর্কাইভ টেমপ্লেট খুঁজে পাওয়া যায়নি।',
        });
      }
      templateSource = 'official';
    } else {
      const personalExisting = await findUserPersonalBySender(userId, cleanSenderId);
      const officialExisting = await findOfficialArchiveBySender(cleanSenderId);

      if (personalExisting) {
        template = personalExisting;
        templateSource = 'personal';
      } else if (officialExisting && !forcePersonal) {
        template = officialExisting;
        templateSource = 'official';
      } else if (forcePersonal || !officialExisting) {
        template = await prisma.sms_templates.create({
          data: {
            user_id: userId,
            device_id: deviceId,
            template_name: `Custom-${cleanSenderId}`,
            sender_id: cleanSenderId,
            sender_number: cleanSenderId,
            regex_pattern: '^(.*)$',
            matching_keyword: '',
            is_official: 0,
            is_active: 0,
            is_parseable: 0,
          },
        });
        templateSource = 'personal';
      } else {
        template = officialExisting;
        templateSource = 'official';
      }
    }

    const providerLabel = templateSource === 'official'
      ? (template.template_name || `Custom-${cleanSenderId}`)
      : `Custom-${cleanSenderId}`;

    // Check if gateway method already exists for this template on slot
    const existingMethod = await prisma.gateway_methods.findFirst({
      where: {
        user_id: String(userId),
        device_id: String(deviceId),
        template_id: template.id,
        sim_slot: simSlotNum,
      },
    });

    if (existingMethod) {
      if (existingMethod.is_enabled === 0) {
        await prisma.gateway_methods.update({
          where: { id: existingMethod.id },
          data: { is_enabled: 1 }
        });
      }
      const data = await fetchGatewayMethodsForUser(userId, deviceId);
      return res.json({
        success: true,
        message: 'কাস্টম সেন্ডার সফলভাবে যুক্ত করা হয়েছে।',
        data
      });
    }

    // Get max priority to append
    const maxPriorityRow = await prisma.gateway_methods.aggregate({
      where: { user_id: String(userId), device_id: String(deviceId) },
      _max: { priority: true }
    });
    const nextPriority = (maxPriorityRow._max.priority || 0) + 1;

    // Create gateway method
    await prisma.gateway_methods.create({
      data: {
        user_id: String(userId),
        device_id: String(deviceId),
        template_id: template.id,
        sim_slot: simSlotNum,
        provider: providerLabel,
        number: '',
        is_enabled: 1,
        priority: nextPriority,
      },
    });

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Custom sender ${cleanSenderId} added for user ${userId} on slot ${sim_slot} (source=${templateSource}, template=${template.id})`);

    const io = req.app.get('io');
    if (io && deviceId) {
      io.to(`${userId}:${deviceId}`).emit("sync_gateway_methods", data);
    }

    await touchDeviceSync(userId, deviceId, { userCustomChanged: true });

    return res.json({
      success: true,
      message: templateSource === 'official'
        ? 'এডমিন আর্কাইভ সেন্ডার সফলভাবে যুক্ত করা হয়েছে।'
        : 'কাস্টম সেন্ডার সফলভাবে যুক্ত করা হয়েছে।',
      template_source: templateSource,
      data,
    });
  } catch (error) {
    console.error('[GATEWAY] addCustomSender error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function deleteGatewayMethod(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const methodId = parseInt(req.params.id);

    const method = await prisma.gateway_methods.findFirst({
      where: { id: methodId, user_id: String(userId), device_id: String(deviceId) }
    });

    if (!method) {
      return res.status(404).json({ error: 'মেথড পাওয়া যায়নি' });
    }

    // Delete the gateway method
    await prisma.gateway_methods.delete({
      where: { id: methodId }
    });

    // Clean up custom template if not used anywhere else
    let customTemplateDeleted = false;
    if (method.template_id) {
      const template = await prisma.sms_templates.findUnique({
        where: { id: method.template_id }
      });
      if (template && template.is_official === 0) {
        const otherUses = await prisma.gateway_methods.count({
          where: { template_id: method.template_id }
        });
        if (otherUses === 0) {
          await prisma.sms_templates.delete({
            where: { id: method.template_id }
          });
          customTemplateDeleted = true;
        }
      }
    }

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Gateway method ${methodId} deleted | User: ${userId}`);

    const io = req.app.get('io');
    if (io && deviceId) {
      io.to(`${userId}:${deviceId}`).emit("sync_gateway_methods", data);
    }

    await touchDeviceSync(userId, deviceId, { userCustomChanged: customTemplateDeleted });

    return res.json({ success: true, message: 'মেথড সফলভাবে ডিলিট করা হয়েছে।', data });
  } catch (error) {
    console.error('[GATEWAY] deleteGatewayMethod error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/slot/lookup
// Phone number input → conflict check + cached template profile
// Body: { sim_slot, phone_number }
// =============================================================================
async function lookupSlotNumber(req, res) {
  try {
    req.body.sim_slot = req.body.sim_slot ?? req.body.slotIndex;
    req.body.phone_number = req.body.phone_number ?? req.body.phoneNumber;

    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const simSlot = parseInt(req.body.sim_slot, 10);
    const cleanNum = normalizePhoneNumber(req.body.phone_number);

    if (!Number.isInteger(simSlot) || simSlot < 1 || simSlot > 2) {
      return res.status(400).json({ error: 'sim_slot ১ বা ২ হতে হবে' });
    }
    if (cleanNum.length !== 11) {
      return res.status(400).json({ error: '১১ ডিজিটের বৈধ মোবাইল নম্বর দিন' });
    }

    await ensureSimBindingsTable();

    const conflictBinding = await findActiveConflictOnOtherDevice(userId, deviceId, cleanNum);

    if (conflictBinding) {
      const runningDeviceName = await getDeviceDisplayName(userId, conflictBinding.device_id);
      return res.json({
        success: true,
        has_conflict: true,
        running_device_name: runningDeviceName,
      });
    }

    const currentBinding = await findSlotBinding(userId, deviceId, simSlot);
    const oldNum = currentBinding?.phone_number
      || (await prisma.gateway_methods.findFirst({
        where: { user_id: String(userId), device_id: String(deviceId), sim_slot: simSlot },
        orderBy: { updated_at: 'desc' },
      }))?.number
      || '';

    if (oldNum && oldNum.length === 11 && oldNum !== cleanNum) {
      await saveDeviceSlotProfile(userId, deviceId, simSlot, oldNum);
    }

    const savedProfile = await fetchDeviceSlotProfile(userId, deviceId, simSlot, cleanNum);
    let profileSource = savedProfile;
    if (!profileSource?.length) {
      profileSource = await fetchGlobalProfileForNumber(userId, cleanNum);
    }
    if (!profileSource?.length) {
      const localMethods = await prisma.gateway_methods.findMany({
        where: {
          user_id: String(userId),
          device_id: String(deviceId),
          sim_slot: simSlot,
          number: cleanNum,
        },
        orderBy: [{ priority: 'asc' }],
      });
      if (localMethods.length) {
        profileSource = localMethods;
      } else {
        profileSource = await fetchMethodsProfileForNumber(userId, cleanNum);
      }
    }

    const numberFilter = oldNum && oldNum.length === 11 ? { number: oldNum } : {};
    await prisma.gateway_methods.updateMany({
      where: {
        user_id: String(userId),
        device_id: String(deviceId),
        sim_slot: simSlot,
        ...numberFilter,
      },
      data: { number: cleanNum },
    });

    await upsertSlotBinding(userId, deviceId, simSlot, cleanNum, false);

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    const enrichedProfile = await enrichProfileEntries(profileSource);

    return res.json({
      success: true,
      has_conflict: false,
      apply_profile: enrichedProfile.length > 0,
      cached_methods: enrichedProfile,
      data,
    });
  } catch (error) {
    console.error('[GATEWAY] lookupSlotNumber error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

async function enrichProfileEntries(profileEntries) {
  if (!profileEntries?.length) return [];

  const templateIds = [...new Set(profileEntries.map((m) => m.template_id).filter(Boolean))];
  const templates = templateIds.length
    ? await prisma.sms_templates.findMany({ where: { id: { in: templateIds } } })
    : [];
  const templateMap = new Map(templates.map((t) => [t.id, t]));

  return profileEntries.map((m) => {
    const t = m.template_id ? templateMap.get(m.template_id) : null;
    return {
      template_id: m.template_id || null,
      provider: m.provider,
      is_enabled: m.is_enabled ?? 0,
      sender_id: t?.sender_id || null,
      sender_number: t?.sender_number || null,
      matching_keyword: t?.matching_keyword || null,
      regex_pattern: t?.regex_pattern || null,
    };
  });
}

async function enrichMethodsWithTemplateMeta(methodRows) {
  if (!methodRows.length) return [];

  const templateIds = [...new Set(methodRows.map((m) => m.template_id).filter(Boolean))];
  const templates = templateIds.length
    ? await prisma.sms_templates.findMany({ where: { id: { in: templateIds } } })
    : [];
  const templateMap = new Map(templates.map((t) => [t.id, t]));

  return methodRows.map((m) => {
    const t = m.template_id ? templateMap.get(m.template_id) : null;
    return {
      id: m.id,
      sim_slot: m.sim_slot,
      provider: m.provider,
      number: m.number,
      template_id: m.template_id,
      is_enabled: m.is_enabled,
      sender_id: t?.sender_id || null,
      sender_number: t?.sender_number || null,
      matching_keyword: t?.matching_keyword || null,
      regex_pattern: t?.regex_pattern || null,
    };
  });
}

// =============================================================================
// POST /api/gateway/slot/force-shift
// User approved conflict shift — bind number to current slot as active
// Body: { sim_slot, phone_number, force_shift: true }
// =============================================================================
async function forceShiftSlot(req, res) {
  try {
    req.body.sim_slot = req.body.sim_slot ?? req.body.slotIndex;
    req.body.phone_number = req.body.phone_number ?? req.body.phoneNumber;

    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const simSlot = parseInt(req.body.sim_slot, 10);
    const cleanNum = normalizePhoneNumber(req.body.phone_number);

    if (!req.body.force_shift) {
      return res.status(400).json({ error: 'force_shift approval required' });
    }
    if (!Number.isInteger(simSlot) || simSlot < 1 || simSlot > 2) {
      return res.status(400).json({ error: 'sim_slot ১ বা ২ হতে হবে' });
    }
    if (cleanNum.length !== 11) {
      return res.status(400).json({ error: '১১ ডিজিটের বৈধ মোবাইল নম্বর দিন' });
    }

    await ensureSimBindingsTable();

    const currentSlotBinding = await findSlotBinding(userId, deviceId, simSlot);
    const otherDeviceBindings = await findBindingsForPhoneOnOtherDevices(userId, deviceId, cleanNum);

    // Capture template profile BEFORE deactivating anything (disabled rows lose is_enabled).
    for (const binding of otherDeviceBindings) {
      await saveDeviceSlotProfile(userId, binding.device_id, binding.sim_slot, cleanNum);
    }

    let profileMethods = await resolveProfileForPhoneNumber(userId, deviceId, simSlot, cleanNum);
    if (!profileMethods.length && otherDeviceBindings[0]) {
      profileMethods = await fetchLiveMethodsForBinding(userId, otherDeviceBindings[0], cleanNum);
    }

    if (currentSlotBinding?.phone_number && currentSlotBinding.phone_number !== cleanNum) {
      await deactivatePhoneGlobally(userId, currentSlotBinding.phone_number);
    }

    await deactivatePhoneGlobally(userId, cleanNum);

    for (const binding of otherDeviceBindings) {
      await prisma.gateway_methods.updateMany({
        where: {
          user_id: String(userId),
          device_id: binding.device_id,
          sim_slot: binding.sim_slot,
        },
        data: { is_enabled: 0 },
      });
      await deactivateBindingById(Number(binding.id));
      await touchDeviceSync(userId, binding.device_id);
    }

    await applyProfileToSlot(userId, deviceId, simSlot, cleanNum, profileMethods, 1);
    await upsertSlotBinding(userId, deviceId, simSlot, cleanNum, true);
    await saveDeviceSlotProfile(userId, deviceId, simSlot, cleanNum);

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Force shift: ${cleanNum} → Device ${deviceId} slot ${simSlot} | User: ${userId}`);

    await emitGatewaySync(req, userId, deviceId, data);
    await touchDeviceSync(userId, deviceId);

    for (const binding of otherDeviceBindings) {
      const oldData = await fetchGatewayMethodsForUser(userId, binding.device_id);
      await emitGatewaySync(req, userId, binding.device_id, oldData);
    }

    return res.json({
      success: true,
      has_conflict: false,
      message: 'সিম সফলভাবে এই ডিভাইসে স্থানান্তরিত হয়েছে।',
      data,
    });
  } catch (error) {
    console.error('[GATEWAY] forceShiftSlot error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/slot/active
// Manual SIM toggle — sets is_active on slot binding (no heartbeat)
// Body: { sim_slot, phone_number, is_active: 0|1 }
// =============================================================================
async function setSlotActive(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const simSlot = parseInt(req.body.sim_slot, 10);
    const cleanNum = normalizePhoneNumber(req.body.phone_number);
    const isActive = !!req.body.is_active;

    if (!Number.isInteger(simSlot) || simSlot < 1 || simSlot > 2) {
      return res.status(400).json({ error: 'sim_slot ১ বা ২ হতে হবে' });
    }
    if (cleanNum.length !== 11) {
      return res.status(400).json({ error: '১১ ডিজিটের বৈধ মোবাইল নম্বর দিন' });
    }

    await ensureSimBindingsTable();

    if (isActive) {
      const conflictBinding = await findActiveConflictOnOtherDevice(userId, deviceId, cleanNum);

      if (conflictBinding) {
        const runningDeviceName = await getDeviceDisplayName(userId, conflictBinding.device_id);
        return res.json({
          success: false,
          has_conflict: true,
          running_device_name: runningDeviceName,
        });
      }

      await deactivatePhoneGlobally(userId, cleanNum);
    }

    await upsertSlotBinding(userId, deviceId, simSlot, cleanNum, isActive);

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    await emitGatewaySync(req, userId, deviceId, data);
    await touchDeviceSync(userId, deviceId);

    return res.json({
      success: true,
      has_conflict: false,
      is_active: isActive,
      data,
    });
  } catch (error) {
    console.error('[GATEWAY] setSlotActive error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/methods/bulk-sync
// Batch upsert methods for a slot (called when SIM toggle turns ON)
// Body: { sim_slot, phone_number, methods: [{ template_id, provider, is_enabled }] }
// =============================================================================
async function bulkSyncSlotMethods(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const simSlot = parseInt(req.body.sim_slot, 10);
    const cleanNum = normalizePhoneNumber(req.body.phone_number);
    const items = Array.isArray(req.body.methods) ? req.body.methods : [];
    const replaceSlot = !!req.body.replace_slot;
    const activateBinding = req.body.activate_binding !== false;

    if (!Number.isInteger(simSlot) || simSlot < 1 || simSlot > 2) {
      return res.status(400).json({ error: 'sim_slot ১ বা ২ হতে হবে' });
    }
    if (cleanNum.length !== 11) {
      return res.status(400).json({ error: '১১ ডিজিটের বৈধ মোবাইল নম্বর দিন' });
    }

    await ensureSimBindingsTable();

    if (activateBinding) {
      const conflictBinding = await findActiveConflictOnOtherDevice(userId, deviceId, cleanNum);
      if (conflictBinding) {
        const runningDeviceName = await getDeviceDisplayName(userId, conflictBinding.device_id);
        return res.json({
          success: false,
          has_conflict: true,
          running_device_name: runningDeviceName,
        });
      }
      await deactivatePhoneGlobally(userId, cleanNum);
    }

    if (replaceSlot) {
      const keepTemplateIds = items.map((i) => i.template_id).filter((id) => id != null);
      if (keepTemplateIds.length > 0) {
        await prisma.gateway_methods.deleteMany({
          where: {
            user_id: String(userId),
            device_id: String(deviceId),
            sim_slot: simSlot,
            OR: [
              { template_id: { notIn: keepTemplateIds } },
              { template_id: null },
            ],
          },
        });
      } else {
        await prisma.gateway_methods.deleteMany({
          where: {
            user_id: String(userId),
            device_id: String(deviceId),
            sim_slot: simSlot,
          },
        });
      }
    }

    const maxPriorityRow = await prisma.gateway_methods.aggregate({
      where: { user_id: String(userId), device_id: String(deviceId) },
      _max: { priority: true },
    });
    let nextPriority = (maxPriorityRow._max.priority || 0) + 1;

    for (const item of items) {
      if (!item.provider) continue;
      const templateId = item.template_id || null;
      const enabled = item.is_enabled === undefined ? 1 : (item.is_enabled ? 1 : 0);

      const existing = await prisma.gateway_methods.findFirst({
        where: {
          user_id: String(userId),
          device_id: String(deviceId),
          sim_slot: simSlot,
          template_id: templateId,
        },
      });

      if (existing) {
        await prisma.gateway_methods.update({
          where: { id: existing.id },
          data: {
            number: cleanNum,
            provider: item.provider,
            is_enabled: enabled,
          },
        });
      } else {
        await prisma.gateway_methods.create({
          data: {
            user_id: String(userId),
            device_id: String(deviceId),
            template_id: templateId,
            sim_slot: simSlot,
            provider: item.provider,
            number: cleanNum,
            is_enabled: enabled,
            priority: nextPriority++,
          },
        });
      }
    }

    await upsertSlotBinding(userId, deviceId, simSlot, cleanNum, activateBinding);
    await saveDeviceSlotProfile(userId, deviceId, simSlot, cleanNum);

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    await emitGatewaySync(req, userId, deviceId, data);
    await touchDeviceSync(userId, deviceId);

    return res.json({
      success: true,
      message: 'স্লট কনফিগারেশন সিঙ্ক হয়েছে।',
      data,
    });
  } catch (error) {
    console.error('[GATEWAY] bulkSyncSlotMethods error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/sim-swap  (legacy — delegates to lookup / force-shift)
// =============================================================================
async function syncAndValidateSimSwap(req, res) {
  try {
    const forceShift = !!req.body.force_shift;
    if (forceShift) {
      return forceShiftSlot(req, res);
    }
    return lookupSlotNumber(req, res);
  } catch (error) {
    console.error('[GATEWAY] syncAndValidateSimSwap error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = {
  fetchGatewayMethodsForUser,
  getGatewayMethods,
  updatePriority,
  toggleMethod,
  updateMethod,
  getTemplates,
  addGatewayMethod,
  addCustomTemplate,
  addCustomSender,
  getCustomSenderSuggestions,
  deleteGatewayMethod,
  syncAndValidateSimSwap,
  lookupSlotNumber,
  forceShiftSlot,
  setSlotActive,
  bulkSyncSlotMethods,
};
