const prisma = require('../db/prisma');

/**
 * parseRawSms — RAW SMS body থেকে server-side এ payment data extract করে।
 *
 * Flow:
 *  1. Active templates থেকে regex pattern match করে
 *  2. Named groups (amount, sender, trxid) পেলে সেগুলো ব্যবহার করে
 *  3. Named groups না পেলে (সব {random} দিলে), Smart Extractor দিয়ে
 *     জেনেরিক প্যাটার্ন ব্যবহার করে SMS থেকে ডাটা বের করে
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
async function parseRawSms(rawBody, senderHint = '', providerTag = '') {
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

    // 0. Check if providerTag matches template name
    if (providerTag && template.template_name && template.template_name.toLowerCase() === providerTag.toLowerCase()) {
      isMatch = true;
    }

    // 1. Check if Sender ID matches
    if (!isMatch && cleanSender && template.sender_id) {
      const senderPattern = new RegExp(template.sender_id, 'i');
      if (senderPattern.test(cleanSender)) {
        isMatch = true;
      }
    }

    // 2. Check if Keywords match (Fallback if sender is weird phone number)
    if (!isMatch && template.matching_keyword) {
      const keywords = template.matching_keyword.split(',').map(k => k.trim().toLowerCase()).filter(Boolean);
      const cleanBodyLower = cleanBody.toLowerCase();
      if (keywords.length > 0 && keywords.some(k => cleanBodyLower.includes(k))) {
        isMatch = true;
      }
    }

    // 3. If neither sender nor keywords matched (and template has them), skip this template
    if (!isMatch && (template.sender_id || template.matching_keyword)) {
      continue;
    }

    try {
      if (!template.regex_pattern) continue;

      const patterns = template.regex_pattern.split('|||');
      let matched = false;
      let amountRaw = '';
      let extractedSender = '';
      let extractedTrxId = '';

      for (const patternStr of patterns) {
        if (!patternStr.trim()) continue;
        const regex = new RegExp(patternStr, 'is');
        const match = regex.exec(cleanBody);

        if (match) {
          matched = true;
          if (match.groups) {
            amountRaw = match.groups.amount || '';
            extractedSender = match.groups.sender || '';
            extractedTrxId = match.groups.trxid || '';
          }
          break;
        }
      }

      if (!matched) continue;

      // ── Smart Extractor ─────────────────────────────────────────────
      // Named groups (amount, sender, trxid) থেকে ডাটা না পেলে,
      // জেনেরিক প্যাটার্ন দিয়ে SMS body থেকে নিজে খুঁজে বের করবে।
      // এতে সব {random} দিলেও সার্ভার সঠিক ডাটা Extract করতে পারবে।
      // ────────────────────────────────────────────────────────────────

      if (!amountRaw) {
        amountRaw = smartExtractAmount(cleanBody);
      }
      if (!extractedSender) {
        extractedSender = smartExtractSender(cleanBody, senderHint);
      }
      if (!extractedTrxId) {
        extractedTrxId = smartExtractTrxId(cleanBody);
      }

      const amount = parseFloat((amountRaw || '0').replace(/,/g, ''));

      // Extract provider and type from template_name (e.g. "bKash Personal" -> provider: "bKash", type: "Personal")
      const nameParts = template.template_name ? template.template_name.split(' ') : [];
      const provider = nameParts[0] || 'Unknown';
      const type = nameParts[1] || '';

      return {
        success:      true,
        provider:     provider,
        type:         type,
        amount:       isNaN(amount) ? 0 : amount,
        trxId:        extractedTrxId || '',
        senderNumber: extractedSender || senderHint || 'Unknown'
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


// =============================================================================
// Smart Extractor Functions — জেনেরিক প্যাটার্ন দিয়ে ডাটা Extract করে
// =============================================================================

/**
 * SMS body থেকে Amount (টাকার পরিমাণ) খুঁজে বের করে।
 * 
 * সাপোর্টেড ফরম্যাট:
 *  - Tk 1,200.00 / Tk1,200.00 / Tk 500
 *  - Taka 500.00
 *  - BDT 1000
 *  - Amount: 500.00
 */
function smartExtractAmount(body) {
  // প্রথম Tk/Taka/BDT এর পরের সংখ্যাটি সাধারণত মূল Amount হয়
  const patterns = [
    /(?:Tk|Taka|BDT)\s*([\d,]+(?:\.\d{1,2})?)/i,
    /(?:amount|received|paid|sent|cash\s*in)\s*(?:Tk|Taka|BDT)?\s*([\d,]+(?:\.\d{1,2})?)/i,
  ];

  for (const regex of patterns) {
    const match = regex.exec(body);
    if (match && match[1]) {
      return match[1];
    }
  }
  return '';
}

/**
 * SMS body থেকে Sender Number/Account খুঁজে বের করে।
 * 
 * সাপোর্টেড ফরম্যাট:
 *  - from 01711223344
 *  - from A/C:***151
 *  - from 0197*****60
 */
function smartExtractSender(body, senderHint) {
  const patterns = [
    /from\s+(?:A\/C[:\s]*)?([0-9*xX\/]+(?:\d{2,}))/i,
    /from\s+([\d\*xX]{5,})/i,
  ];

  for (const regex of patterns) {
    const match = regex.exec(body);
    if (match && match[1]) {
      return match[1];
    }
  }
  return senderHint || '';
}

/**
 * SMS body থেকে Transaction ID খুঁজে বের করে।
 * 
 * সাপোর্টেড ফরম্যাট:
 *  - TrxID ABC123XYZ
 *  - TxnId:6667018563
 *  - Transaction ID: ABC123
 *  - Trx ID ABC123
 */
function smartExtractTrxId(body) {
  const patterns = [
    /Tr(?:x|an|ansaction)\s*(?:ID|Id|id)?[:\s]*([A-Za-z0-9]+)/i,
    /TxnId[:\s]*([A-Za-z0-9]+)/i,
    /Txn[:\s]*([A-Za-z0-9]+)/i,
  ];

  for (const regex of patterns) {
    const match = regex.exec(body);
    if (match && match[1]) {
      return match[1];
    }
  }
  return '';
}


module.exports = { parseRawSms };
