const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();

async function main() {
    // 1. bKash Personal sender_number update
    await prisma.sms_templates.update({
        where: { id: 1 },
        data: { sender_number: 'bKash' }
    });
    console.log("✅ bKash Personal sender_number fixed successfully.");

    // 2. Global config sync timestamp update
    await prisma.global_config.upsert({
        where: { config_key: 'templates_last_updated' },
        update: { config_value: String(Date.now()), updated_at: new Date() },
        create: { config_key: 'templates_last_updated', config_value: String(Date.now()), updated_at: new Date() }
    });
    console.log("🚀 Global template sync timestamp updated.");
}

main()
  .catch(e => console.error(e))
  .finally(() => prisma.$disconnect());
