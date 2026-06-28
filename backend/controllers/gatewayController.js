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
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';

    const rows = await prisma.sms_templates.findMany({
      where: {
        OR: [
          { is_official: 1, is_active: { in: [0, 1] } },
          { is_official: 0, user_id: userId }
        ]
      },
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

async function addCustomSender(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const { sim_slot, sender_id } = req.body;

    if (!sim_slot || !sender_id) {
      return res.status(400).json({ error: 'sim_slot এবং sender_id আবশ্যক' });
    }

    const cleanSenderId = sender_id.trim();

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
        if (endsAt >= today) {
          isAllowed = true;
        }
      } else {
        isAllowed = true;
      }
    } else if (user.is_paid && user.active_plan_name) {
      if (user.active_plan_name === 'Trial Package') {
        const configVal = await prisma.global_config.findUnique({
          where: { config_key: 'trial_allow_custom_sender' }
        });
        if (configVal && parseInt(configVal.config_value, 10) === 1) {
          isAllowed = true;
        }
      } else {
        const plan = await prisma.subscription_plans.findFirst({
          where: { plan_name: user.active_plan_name }
        });
        if (plan && plan.is_custom_sender_allowed === 1) {
          isAllowed = true;
        }
      }
    }

    if (!isAllowed) {
      return res.status(403).json({
        success: false,
        error: 'FEATURE_GATED',
        message: 'কাস্টম সেন্ডার আইডি ব্যবহার করতে হলে আপনার প্যাকেজ আপগ্রেড করুন অথবা অ্যাড-অন কিনুন।'
      });
    }

    // Find or create user custom template
    let template = await prisma.sms_templates.findFirst({
      where: {
        user_id: userId,
        sender_id: cleanSenderId
      }
    });

    if (!template) {
      template = await prisma.sms_templates.create({
        data: {
          user_id: userId,
          device_id: deviceId, // Bound strictly to this device
          template_name: `Custom-${cleanSenderId}`,
          sender_id: cleanSenderId,
          sender_number: cleanSenderId,
          regex_pattern: '^(.*)$',
          matching_keyword: '',
          is_official: 0,
          is_active: 0, // Set to 0 (Value 0 - Hidden from Homepage suggestions)
          is_parseable: 1
        }
      });
    }

    // Check if gateway method already exists for this slot
    const existingMethod = await prisma.gateway_methods.findFirst({
      where: {
        user_id: String(userId),
        device_id: String(deviceId),
        template_id: template.id,
        sim_slot: parseInt(sim_slot, 10)
      }
    });

    if (existingMethod) {
      return res.status(400).json({ error: 'এই সেন্ডার আইডিটি ইতিমধ্যেই এই স্লটে যুক্ত করা আছে।' });
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
        sim_slot: parseInt(sim_slot, 10),
        provider: `Custom-${cleanSenderId}`,
        number: '',
        is_enabled: 1,
        priority: nextPriority
      }
    });

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Custom sender ${cleanSenderId} added for user ${userId} on slot ${sim_slot}`);

    const io = req.app.get('io');
    if (io && deviceId) {
      io.to(`${userId}:${deviceId}`).emit("sync_gateway_methods", data);
    }

    return res.json({ success: true, message: 'কাস্টম সেন্ডার সফলভাবে যুক্ত করা হয়েছে।', data });
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
        }
      }
    }

    const data = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] Gateway method ${methodId} deleted | User: ${userId}`);

    const io = req.app.get('io');
    if (io && deviceId) {
      io.to(`${userId}:${deviceId}`).emit("sync_gateway_methods", data);
    }

    return res.json({ success: true, message: 'মেথড সফলভাবে ডিলিট করা হয়েছে।', data });
  } catch (error) {
    console.error('[GATEWAY] deleteGatewayMethod error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

// =============================================================================
// POST /api/gateway/sim-swap
// সিম সোয়াপ / সিম ট্র্যাকিং সিঙ্ক ও রোমিং হ্যান্ডলার
// =============================================================================
async function syncAndValidateSimSwap(req, res) {
  try {
    const userId = req.user.userId;
    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const { phoneNumber, slotIndex } = req.body;

    if (!phoneNumber || slotIndex === undefined) {
      return res.status(400).json({ error: 'phoneNumber and slotIndex are required' });
    }

    const cleanNum = phoneNumber.trim();
    const slot = parseInt(slotIndex, 10);

    // 1. Find if this number exists in gateway_methods for this user
    const existingMethods = await prisma.gateway_methods.findMany({
      where: {
        user_id: String(userId),
        number: cleanNum
      }
    });

    if (existingMethods.length === 0) {
      // Case 1: Brand new SIM. Clear existing templates for this slot on this device
      await prisma.gateway_methods.deleteMany({
        where: {
          user_id: String(userId),
          device_id: String(deviceId),
          sim_slot: slot
        }
      });
      return res.json({
        success: true,
        is_known_sim: false,
        message: 'Brand new SIM card detected.'
      });
    }

    // Check if the number is already bound to this device/slot
    const currentDeviceMatch = existingMethods.filter(m => m.device_id === String(deviceId));

    if (currentDeviceMatch.length > 0) {
      // Case 2: SIM Memory Hit under same device. Retain/update to the current slot index
      // Overwrite the slot index if it was different
      await prisma.gateway_methods.updateMany({
        where: {
          user_id: String(userId),
          number: cleanNum
        },
        data: {
          sim_slot: slot,
          is_enabled: 1
        }
      });
      const updatedData = await fetchGatewayMethodsForUser(userId, deviceId);
      return res.json({
        success: true,
        is_known_sim: true,
        message: 'SIM card recognized from memory.',
        data: updatedData
      });
    }

    // Case 3: SIM Roaming (Moved to another Device)
    // First, delete any existing methods for the target slot on the current device to make room
    await prisma.gateway_methods.deleteMany({
      where: {
        user_id: String(userId),
        device_id: String(deviceId),
        sim_slot: slot
      }
    });

    // Update the roaming SIM's device_id and sim_slot to the new device & slot
    await prisma.gateway_methods.updateMany({
      where: {
        user_id: String(userId),
        number: cleanNum
      },
      data: {
        device_id: String(deviceId),
        sim_slot: slot,
        is_enabled: 1
      }
    });

    const updatedData = await fetchGatewayMethodsForUser(userId, deviceId);
    console.log(`[GATEWAY] SIM Roamed: ${cleanNum} moved to Device: ${deviceId}, Slot: ${slot}`);

    // Trigger real-time update
    const io = req.app.get('io');
    if (io) {
      io.to(`${userId}:${deviceId}`).emit("sync_gateway_methods", updatedData);
    }

    return res.json({
      success: true,
      is_known_sim: true,
      message: 'SIM card roamed successfully from another device.',
      data: updatedData
    });

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
  deleteGatewayMethod,
  syncAndValidateSimSwap
};
