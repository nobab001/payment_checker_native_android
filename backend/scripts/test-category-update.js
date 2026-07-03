const prisma = require('../db/prisma');

async function main() {
  const result = await prisma.sms_templates.updateMany({
    where: { id: 34 },
    data: { category: 'CASH_OUT', updated_at: new Date() },
  });
  console.log('update result:', result);
  const row = await prisma.sms_templates.findUnique({
    where: { id: 34 },
    select: { id: true, category: true, template_name: true },
  });
  console.log('row:', row);
}

main()
  .catch((e) => {
    console.error('ERR:', e.message);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
