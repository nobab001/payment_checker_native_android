const { query } = require('../db/connection');

// =============================================================================
// GET /api/gateway/methods
// ব্যবহারকারীর সব গেটওয়ে মেথড লোড করে (priority অনুযায়ী সাজানো)
// =============================================================================
async function getGatewayMethods(req, res) {
  try {
    const userId = req.user.userId;

    const rows = await query(
      `SELECT gm.id, gm.sim_slot, gm.provider, gm.number, gm.display_name, gm.is_enabled, gm.priority, gm.template_id,
              t.sender_id, t.matching_keyword, '' AS regex_pattern, COALESCE(t.is_official, 1) AS is_official,
              cvt.single_number_instruction, cvt.multiple_number_instruction
         FROM gateway_methods gm
    LEFT JOIN sms_templates t ON gm.template_id = t.id
    LEFT JOIN checkout_view_templates cvt ON cvt.sms_template_id = t.id
        WHERE gm.user_id = ?
        ORDER BY gm.priority ASC, gm.sim_slot ASC`,
      [userId]
    );

    return res.json({ success: true, data: rows });
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
    const { items } = req.body;

    if (!Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: 'items array প্রয়োজন' });
    }

    // প্রতিটি item-এর জন্য আলাদা UPDATE — user_id দিয়ে সুরক্ষিত
    for (const item of items) {
      if (typeof item.id !== 'number' || typeof item.priority !== 'number') continue;

      await query(
        `UPDATE gateway_methods SET priority = ? WHERE id = ? AND user_id = ?`,
        [item.priority, item.id, userId]
      );
    }

    console.log(`[GATEWAY] Priority updated for ${items.length} items | User: ${userId}`);
    return res.json({ success: true, message: 'Priority ক্রম সফলভাবে আপডেট হয়েছে।' });

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
    const methodId = parseInt(req.params.id);
    const { is_enabled } = req.body;

    if (![0, 1].includes(is_enabled)) {
      return res.status(400).json({ error: 'is_enabled অবশ্যই 0 বা 1 হতে হবে' });
    }

    const result = await query(
      `UPDATE gateway_methods SET is_enabled = ? WHERE id = ? AND user_id = ?`,
      [is_enabled, methodId, userId]
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ error: 'মেথড পাওয়া যায়নি' });
    }

    const status = is_enabled === 1 ? 'চালু' : 'বন্ধ';
    console.log(`[GATEWAY] Method ${methodId} ${status} | User: ${userId}`);
    return res.json({ success: true, message: `মেথড ${status} করা হয়েছে।` });

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
    const methodId = parseInt(req.params.id);
    const { number, display_name, template_id } = req.body;

    if (number === undefined && display_name === undefined && template_id === undefined) {
      return res.status(400).json({ error: 'number, display_name বা template_id প্রয়োজন' });
    }

    const setClauses = [];
    const values     = [];

    if (number !== undefined) {
      setClauses.push('number = ?');
      values.push(number || null);
    }
    if (display_name !== undefined) {
      setClauses.push('display_name = ?');
      values.push(display_name || null);
    }
    if (template_id !== undefined) {
      setClauses.push('template_id = ?');
      values.push(template_id || null);
    }
    values.push(methodId, userId);

    const result = await query(
      `UPDATE gateway_methods SET ${setClauses.join(', ')} WHERE id = ? AND user_id = ?`,
      values
    );

    if (result.affectedRows === 0) {
      return res.status(404).json({ error: 'মেথড পাওয়া যায়নি' });
    }

    console.log(`[GATEWAY] Method ${methodId} updated | User: ${userId}`);
    return res.json({ success: true, message: 'মেথড আপডেট হয়েছে।' });

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
    const rows = await query(
      `SELECT id, template_name, sender_id, matching_keyword, '' AS regex_pattern, is_official, is_active
         FROM sms_templates
        WHERE is_active = 1`
    );
    return res.json({ success: true, templates: rows });
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
    const { sim_slot, provider, template_id, number } = req.body;

    if (!sim_slot || !provider) {
      return res.status(400).json({ error: 'sim_slot এবং provider আবশ্যক' });
    }

    // priority সেট করার জন্য সর্বোচ্চ priority বের করা
    const maxPriorityResult = await query(
      'SELECT COALESCE(MAX(priority), 0) AS max_p FROM gateway_methods WHERE user_id = ?',
      [userId]
    );
    const nextPriority = maxPriorityResult[0].max_p + 1;

    const result = await query(
      `INSERT INTO gateway_methods (user_id, template_id, sim_slot, provider, number, priority, is_enabled)
       VALUES (?, ?, ?, ?, ?, ?, 1)`,
      [userId, template_id || null, sim_slot, provider, number || null, nextPriority]
    );

    console.log(`[GATEWAY] Gateway method added | User: ${userId} | Slot: ${sim_slot} | Provider: ${provider}`);
    return res.json({ success: true, id: result.insertId, message: 'মেথড সফলভাবে যোগ করা হয়েছে।' });
  } catch (error) {
    console.error('[GATEWAY] addGatewayMethod error:', error);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

module.exports = { getGatewayMethods, updatePriority, toggleMethod, updateMethod, getTemplates, addGatewayMethod };
