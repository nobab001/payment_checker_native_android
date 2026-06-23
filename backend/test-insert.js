const prisma = require('./db/prisma');
async function test() {
  try {
    const res = await prisma.$queryRawUnsafe('INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))', '01711223344', '123456');
    console.log('Success:', res);
  } catch (e) {
    console.error('Error:', e);
  }
}
test();
