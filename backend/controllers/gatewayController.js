const prisma = require('../db/prisma');

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
    SELECT gm.id, gm.sim_slot, gm.provider, gm.number, gm.display_name, gm.is_enabled, gm.priority, gm.template_id, gm.custom_patterns,
            t.sender_id, t.sender_number, t.matching_keyword, t.regex_pattern AS regex_pattern, COALESCE(t.is_official, 1) AS is_official,
            cvt.single_number_instruction, cvt.multiple_number_instruction
       FROM gateway_methods gm
  LEFT JOIN sms_templates t ON gm.template_id = t.id AND t.is_active = 1
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
    const data = await fetchGatewayMethodsForUser(userId, deviceId);

    return res.json({ success: true, data });
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
    const rows = await prisma.sms_templates.findMany({
      where: { is_active: 1 },
      select: {
        id: true,
        template_name: true,
        sender_id: true,
        sender_number: true,
        matching_keyword: true,
        regex_pattern: true,
        is_official: true,
        is_active: true
      }
    });

    const formatted = rows.map(r => ({
      ...r,
      regex_pattern: r.regex_pattern || ''
    }));

    return res.json({ success: true, templates: formatted });
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

    return res.json({ success: true, message: 'কাস্টম টেমপ্লেট যোগ করা হয়েছে।', data });
  } catch (error) {
    console.error('[GATEWAY] addCustomTemplate error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = { getGatewayMethods, updatePriority, toggleMethod, updateMethod, getTemplates, addGatewayMethod, addCustomTemplate };
