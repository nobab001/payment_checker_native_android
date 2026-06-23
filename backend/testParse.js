const prisma = require('./db/prisma');

async function parseRawSms(rawBody, senderHint = '') {
  console.log('1. Starting with rawBody:', rawBody, 'senderHint:', senderHint);
  const cleanBody   = rawBody.trim();
  const cleanSender = senderHint.trim().toLowerCase();
  
  const templates = await prisma.sms_templates.findMany({
    where: { is_active: 1 }
  });
  console.log('2. Templates length:', templates.length);

  for (const template of templates) {
    console.log('3. Checking template:', template.template_name, 'sender_id:', template.sender_id);
    if (cleanSender && template.sender_id) {
      const senderPattern = new RegExp(template.sender_id, 'i');
      if (!senderPattern.test(cleanSender)) {
        console.log(' -> Sender failed match:', cleanSender, 'vs', template.sender_id);
        continue;
      }
    }
    
    console.log('4. Testing regex');
    const regex = /Tk\s*([\d,]+(?:\.\d+)?)\s*from\s*([\d*Xx]+).*?TrxID:?\s*([A-Z0-9]{6,})/is;
    const match = regex.exec(cleanBody);
    if (!match) {
      console.log(' -> Regex failed');
      continue;
    }

    const amountRaw    = match[1]?.replace(/,/g, '') ?? '0';
    const senderNumber = match[2] ?? 'Unknown';
    const trxId        = match[3]?.toUpperCase() ?? '';

    const amount = parseFloat(amountRaw);
    console.log('5. Match extracted:', { amountRaw, senderNumber, trxId, amount });

    if (!trxId || isNaN(amount)) {
      console.log(' -> trxId or amount invalid');
      continue;
    }

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
  }

  return {
    success: false,
    error:   'No matching SMS template found for provided rawBody'
  };
}

async function run() {
  const body1 = 'You have received Tk 660.00 from 01704468538. Fee Tk 0.00. Balance Tk 7,998.35. TrxID DFM8L8AJDW at 22/06/2026 21:55';
  console.log('RESULT 1:', await parseRawSms(body1, 'bKash'));
}
run();
