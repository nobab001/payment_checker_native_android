const prisma = require('../../db/prisma');
const { ensureSubscriptionV3Schema } = require('./schema');

/** Global invoice serial starts at 201 → INV-201, INV-202, … */
const GLOBAL_SEQ_KEY = '__GLOBAL';
const INVOICE_SEQ_FLOOR = 200; // next() yields 201

async function nextInvoiceNo() {
  await ensureSubscriptionV3Schema();

  await prisma.$executeRaw`
    INSERT INTO subscription_invoice_seq (seq_date, last_seq) VALUES (${GLOBAL_SEQ_KEY}, ${INVOICE_SEQ_FLOOR})
    ON DUPLICATE KEY UPDATE last_seq = last_seq
  `;

  // Never go below floor (handles fresh rows or old low counters)
  await prisma.$executeRaw`
    UPDATE subscription_invoice_seq
    SET last_seq = ${INVOICE_SEQ_FLOOR}
    WHERE seq_date = ${GLOBAL_SEQ_KEY} AND last_seq < ${INVOICE_SEQ_FLOOR}
  `;

  // If legacy INV-NNN numbers exist above the counter, jump ahead
  const maxRows = await prisma.$queryRaw`
    SELECT MAX(CAST(SUBSTRING(invoice_no, 5) AS UNSIGNED)) AS max_n
    FROM subscription_purchases
    WHERE invoice_no REGEXP '^INV-[0-9]+$'
  `;
  const maxExisting = Number(maxRows?.[0]?.max_n || 0);
  if (maxExisting > INVOICE_SEQ_FLOOR) {
    await prisma.$executeRaw`
      UPDATE subscription_invoice_seq
      SET last_seq = GREATEST(last_seq, ${maxExisting})
      WHERE seq_date = ${GLOBAL_SEQ_KEY}
    `;
  }

  await prisma.$executeRaw`
    UPDATE subscription_invoice_seq SET last_seq = last_seq + 1 WHERE seq_date = ${GLOBAL_SEQ_KEY}
  `;
  const rows = await prisma.$queryRaw`
    SELECT last_seq FROM subscription_invoice_seq WHERE seq_date = ${GLOBAL_SEQ_KEY} LIMIT 1
  `;
  const seq = Number(rows[0]?.last_seq || INVOICE_SEQ_FLOOR + 1);
  return `INV-${seq}`;
}

module.exports = { nextInvoiceNo };
