require('dotenv').config();
const prisma = require('../db/prisma');

(async () => {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT id, user_id, device_id, status, is_approved, is_parent, device_role,
            device_model, device_name, custom_name, custom_device_name, created_at
     FROM registered_devices WHERE user_id = 24 ORDER BY id`
  );
  console.log('devices:', JSON.stringify(rows, null, 2));

  const pending = await prisma.$queryRawUnsafe(
    `SELECT id, device_id, status, is_approved, device_role
     FROM registered_devices
     WHERE user_id = 24 AND is_approved = 0 AND status = 'pending'`
  );
  console.log('pending query:', JSON.stringify(pending, null, 2));

  await prisma.$disconnect();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
