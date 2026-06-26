
const prisma = require('./db/prisma');

async function check() {
    const data = await prisma.sms_templates.findMany();
    console.log(JSON.stringify(data, null, 2));
    await prisma.$disconnect();
}
check();

