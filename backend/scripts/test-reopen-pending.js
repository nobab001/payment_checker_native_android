require('dotenv').config();
const prisma = require('../db/prisma');

async function reopenRejectedDeviceAsPending(deviceId) {
  await prisma.$executeRawUnsafe(
    `UPDATE registered_devices
     SET status = 'pending',
         is_approved = 0,
         device_role = 'pending',
         custom_device_name = CASE
           WHEN custom_device_name IS NULL OR custom_device_name = ''
                OR custom_device_name LIKE '%(removed)%'
           THEN COALESCE(NULLIF(device_name, ''), 'Co-Parent Device')
           ELSE REPLACE(custom_device_name, ' (removed)', '')
         END,
         last_seen_at = NOW()
     WHERE id = ? AND is_parent = 0 AND status = 'rejected'`,
    deviceId
  );
}

(async () => {
  await reopenRejectedDeviceAsPending(38);
  const pending = await prisma.$queryRawUnsafe(
    `SELECT id, device_id, status, is_approved, custom_device_name
     FROM registered_devices
     WHERE user_id = 24 AND is_approved = 0 AND status = 'pending'`
  );
  console.log('pending after reopen:', JSON.stringify(pending, null, 2));
  await prisma.$disconnect();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
