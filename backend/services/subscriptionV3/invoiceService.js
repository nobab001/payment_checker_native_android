const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');

async function nextInvoiceNo() {
  await ensureSubscriptionV3Schema();
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  const seqDate = `${y}${m}${d}`;

  await prisma.$executeRaw`
    INSERT INTO subscription_invoice_seq (seq_date, last_seq) VALUES (${seqDate}, 0)
    ON DUPLICATE KEY UPDATE last_seq = last_seq
  `;
  await prisma.$executeRaw`
    UPDATE subscription_invoice_seq SET last_seq = last_seq + 1 WHERE seq_date = ${seqDate}
  `;
  const rows = await prisma.$queryRaw`
    SELECT last_seq FROM subscription_invoice_seq WHERE seq_date = ${seqDate} LIMIT 1
  `;
  const seq = String(rows[0]?.last_seq || 1).padStart(8, '0');
  return `INV-${seqDate}-${seq}`;
}

module.exports = { nextInvoiceNo };
