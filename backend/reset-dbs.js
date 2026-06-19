// reset-dbs.js — Drops orphan user DBs and recreates the target DB fresh
// (Used after a full MariaDB reset where file-per-table InnoDB .ibd files
//  reference deleted tablespace IDs.)
require('dotenv').config();
const mysql = require('mysql2/promise');

const HOST  = process.env.DB_HOST || '127.0.0.1';
const PORT  = parseInt(process.env.DB_PORT || '3306', 10);
const USER  = process.env.DB_USER || 'root';
const PASS  = process.env.DB_PASS || '';
const NAME  = process.env.DB_NAME || 'paychek_online_v2';

(async () => {
  const conn = await mysql.createConnection({ host: HOST, port: PORT, user: USER, password: PASS });
  const dbsToDrop = [NAME, 'payment_checker', 'payment_checker_db'];
  for (const d of dbsToDrop) {
    try {
      await conn.query(`DROP DATABASE IF EXISTS \`${d}\``);
      console.log(`[RESET] dropped: ${d}`);
    } catch (e) {
      console.error(`[RESET] drop failed for ${d}:`, e.message);
    }
  }
  await conn.query(`CREATE DATABASE \`${NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
  console.log(`[RESET] recreated: ${NAME}`);
  await conn.end();
})().catch(err => { console.error('[RESET] Fatal:', err); process.exit(1); });