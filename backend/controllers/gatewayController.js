const { query } = require('../db/connection');

// =============================================================================
// GET /api/gateway/methods
// ব্যবহারকারীর সব গেটওয়ে মেথড লোড করে (priority অনুযায়ী সাজানো)
// =============================================================================
async function getGatewayMethods(req, res) {
  try {
    const userId = req.user.userId;

    const rows = await query(
      `SELECT id, sim_slot, provider, number, display_name, is_enabled, priority
         FROM gateway_methods
        WHERE user_id = ?
        ORDER BY priority ASC, sim_slot ASC`,
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
    const { number, display_name } = req.body;

    if (number === undefined && display_name === undefined) {
      return res.status(400).json({ error: 'number বা display_name প্রয়োজন' });
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

module.exports = { getGatewayMethods, updatePriority, toggleMethod, updateMethod };
