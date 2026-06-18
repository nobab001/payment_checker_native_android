/**
 * parseRawSms.js — Pure RAW SMS Cryptographic Parse Utility
 * =============================================================================
 * Purpose  : ক্লায়েন্টের দেওয়া parsed data অন্ধভাবে বিশ্বাস না করে,
 *            server-side এ পিওর RAW SMS body থেকে regex দিয়ে নিজেই
 *            ট্রানজেকশন ডেটা বের করে। এটি সম্পূর্ণ independent module।
 *
 * Security : এই মডিউল শুধুমাত্র rawBody string input নেয়।
 *            কোনো user-provided parsed field (amount, trxId) ব্যবহার করে না।
 *
 * Supported: bKash Personal, bKash Agent, Nagad Personal, Nagad Agent,
 *            Rocket Personal, Rocket Agent, Upay Personal, Upay Agent
 * =============================================================================
 */

// ── Regex Templates (DB-seeded templates এর সাথে sync করা) ──────────────────
const PARSE_TEMPLATES = [
  {
    provider: 'bKash',
    type: 'Personal',
    senderPattern: /bkash/i,
    matchKeywords: ['You have received', 'Tk.', 'Ref:'],
    regex: /You have received Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'Nagad',
    type: 'Personal',
    senderPattern: /nagad/i,
    matchKeywords: ['received cash in Tk', 'TrxID:'],
    regex: /received cash in Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID:\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'Rocket',
    type: 'Personal',
    senderPattern: /rocket|16216/i,
    matchKeywords: ['received Tk', 'TrxID:'],
    regex: /received Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID:\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'Upay',
    type: 'Personal',
    senderPattern: /upay/i,
    matchKeywords: ['received Tk', 'TrxID'],
    regex: /received Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'bKash',
    type: 'Agent',
    senderPattern: /bkash/i,
    matchKeywords: ['Cash In', 'Tk.', 'Ref:'],
    regex: /Cash In Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'Nagad',
    type: 'Agent',
    senderPattern: /nagad/i,
    matchKeywords: ['Cash in received', 'Tk.', 'TrxID:'],
    regex: /Cash in received Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID:\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'Rocket',
    type: 'Agent',
    senderPattern: /rocket|16216/i,
    matchKeywords: ['Cash In received', 'Tk.', 'TrxID:'],
    regex: /Cash In received Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID:\s*([A-Z0-9]{6,})/is
  },
  {
    provider: 'Upay',
    type: 'Agent',
    senderPattern: /upay/i,
    matchKeywords: ['Cash In received', 'Tk.', 'TrxID'],
    regex: /Cash In received Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID\s*([A-Z0-9]{6,})/is
  }
];

/**
 * parseRawSms — RAW SMS body থেকে server-side এ payment data extract করে।
 *
 * @param {string} rawBody     — Original SMS text (HMAC verify এর পরে ব্যবহার)
 * @param {string} [senderHint]— Optional: SMS sender address (match accuracy বাড়ায়)
 * @returns {{
 *   success: boolean,
 *   provider?: string,
 *   amount?: number,
 *   trxId?: string,
 *   senderNumber?: string,
 *   error?: string
 * }}
 */
function parseRawSms(rawBody, senderHint = '') {
  if (!rawBody || typeof rawBody !== 'string' || rawBody.trim().length === 0) {
    return { success: false, error: 'rawBody is empty or invalid' };
  }

  const cleanBody   = rawBody.trim();
  const cleanSender = senderHint.trim().toLowerCase();

  for (const template of PARSE_TEMPLATES) {
    // Keyword pre-filter: সব keyword থাকলেই regex চালানো হবে
    const keywordsMatch = template.matchKeywords.every(kw =>
      cleanBody.toLowerCase().includes(kw.toLowerCase())
    );
    if (!keywordsMatch) continue;

    // Optional sender hint filter
    if (cleanSender && !template.senderPattern.test(cleanSender)) continue;

    const match = template.regex.exec(cleanBody);
    if (!match) continue;

    const amountRaw    = match[1]?.replace(/,/g, '') ?? '0';
    const senderNumber = match[2] ?? 'Unknown';
    const trxId        = match[3]?.toUpperCase() ?? '';

    const amount = parseFloat(amountRaw);

    if (!trxId || isNaN(amount)) continue;

    return {
      success:      true,
      provider:     template.provider,
      type:         template.type,
      amount:       amount,
      trxId:        trxId,
      senderNumber: senderNumber
    };
  }

  // Fallback: provider শনাক্ত হয়নি বা regex মিলেনি
  return {
    success: false,
    error:   'No matching SMS template found for provided rawBody'
  };
}

module.exports = { parseRawSms };
