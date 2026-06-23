const prisma = require('../db/prisma');

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
  const templates = await prisma.sms_templates.findMany({
    where: { is_active: 1 }
  });

  for (const template of templates) {
    let isMatch = false;

    // 1. Check if Sender ID matches
    if (cleanSender && template.sender_id) {
      const senderPattern = new RegExp(template.sender_id, 'i');
      if (senderPattern.test(cleanSender)) {
        isMatch = true;
      }
    }

    // 2. Check if Keywords match (Fallback if sender is weird phone number)
    if (!isMatch && template.matching_keyword) {
      const keywords = template.matching_keyword.split(',').map(k => k.trim().toLowerCase()).filter(Boolean);
      const cleanBodyLower = cleanBody.toLowerCase();
      if (keywords.length > 0 && keywords.every(k => cleanBodyLower.includes(k))) {
        isMatch = true;
      }
    }

    // 3. If neither sender nor keywords matched (and template has them), skip this template
    if (!isMatch && (template.sender_id || template.matching_keyword)) {
      continue;
    }

    try {
      // Use unified hardcoded payment parser regex instead of dynamic database regex_pattern
      const regex = /Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID:?\s*([A-Z0-9]{6,})/is;
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
