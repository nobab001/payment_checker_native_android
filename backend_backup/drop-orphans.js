// drop-orphans.js — Drops orphan/legacy DBs after MariaDB reset
require('dotenv').config();
const mysql = require('mysql2/promise');

const HOST = process.env.DB_HOST || '127.0.0.1';
const PORT = parseInt(process.env.DB_PORT || '3306', 10);
const USER = process.env.DB_USER || 'root';
const PASS = process.env.DB_PASS || '';

(async () => {
  const conn = await mysql.createConnection({ host: HOST, port: PORT, user: USER, password: PASS });
  const orphans = ['payment_checker', 'payment_checker_db'];
  for (const d of orphans) {
    try {
      await conn.query(`DROP DATABASE IF EXISTS \`${d}\``);
      console.log(`[ORPHAN] dropped: ${d}`);
    } catch (e) {
      console.error(`[ORPHAN] drop failed for ${d}:`, e.message);
    }
  }
  const [rows] = await conn.query(`SHOW DATABASES`);
  console.log('[ORPHAN] remaining databases:');
  for (const r of rows) console.log('   -', Object.values(r)[0]);
  await conn.end();
})().catch(err => { console.error('[ORPHAN] Fatal:', err); process.exit(1); });