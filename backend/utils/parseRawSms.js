const { query } = require('../db/connection');

/**
 * parseRawSms — RAW SMS body থেকে server-side এ payment data extract করে।
 *
 * @param {string} rawBody     — Original SMS text (HMAC verify এর পরে ব্যবহার)
 * @param {string} [senderHint]— Optional: SMS sender address (match accuracy বাড়ায়)
 * @returns {Promise<{
 *   success: boolean,
 *   provider?: string,
 *   type?: string,
 *   amount?: number,
 *   trxId?: string,
 *   senderNumber?: string,
 *   error?: string
 * }>}
 */
async function parseRawSms(rawBody, senderHint = '') {
  if (!rawBody || typeof rawBody !== 'string' || rawBody.trim().length === 0) {
    return { success: false, error: 'rawBody is empty or invalid' };
  }

  const cleanBody   = rawBody.trim();
  const cleanSender = senderHint.trim().toLowerCase();

  // Query active templates from database
  const templates = await query('SELECT * FROM sms_templates WHERE is_active = 1');

  for (const template of templates) {
    // keyword parsing
    const matchKeywords = template.matching_keyword
      ? template.matching_keyword.split(',').map(kw => kw.trim()).filter(Boolean)
      : [];

    // Keyword pre-filter: সব keyword থাকলেই regex চালানো হবে
    const keywordsMatch = matchKeywords.every(kw =>
      cleanBody.toLowerCase().includes(kw.toLowerCase())
    );
    if (!keywordsMatch) continue;

    // Optional sender hint filter
    if (cleanSender && template.sender_id) {
      const senderPattern = new RegExp(template.sender_id, 'i');
      if (!senderPattern.test(cleanSender)) continue;
    }

    try {
      // Create RegExp object from regex_pattern
      const regex = new RegExp(template.regex_pattern, 'is');
      const match = regex.exec(cleanBody);
      if (!match) continue;

      const amountRaw    = match[1]?.replace(/,/g, '') ?? '0';
      const senderNumber = match[2] ?? 'Unknown';
      const trxId        = match[3]?.toUpperCase() ?? '';

      const amount = parseFloat(amountRaw);

      if (!trxId || isNaN(amount)) continue;

      // Extract provider and type from template_name (e.g. "bKash Personal" -> provider: "bKash", type: "Personal")
      const nameParts = template.template_name ? template.template_name.split(' ') : [];
      const provider = nameParts[0] || 'Unknown';
      const type = nameParts[1] || '';

      return {
        success:      true,
        provider:     provider,
        type:         type,
        amount:       amount,
        trxId:        trxId,
        senderNumber: senderNumber
      };
    } catch (err) {
      console.error(`Error processing template id ${template.id}:`, err);
    }
  }

  // Fallback: provider শনাক্ত হয়নি বা regex মিলেনি
  return {
    success: false,
    error:   'No matching SMS template found for provided rawBody'
  };
}

module.exports = { parseRawSms };
