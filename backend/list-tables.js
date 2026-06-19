// list-tables.js — Lists all tables in the configured DB + row counts
require('dotenv').config();
const mysql = require('mysql2/promise');

const HOST = process.env.DB_HOST || '127.0.0.1';
const PORT = parseInt(process.env.DB_PORT || '3306', 10);
const USER = process.env.DB_USER || 'root';
const PASS = process.env.DB_PASS || '';
const NAME = process.env.DB_NAME || 'paychek_online_v2';

(async () => {
  const conn = await mysql.createConnection({ host: HOST, port: PORT, user: USER, password: PASS, database: NAME });
  const [rows] = await conn.query(`SHOW TABLES`);
  const tableKey = `Tables_in_${NAME}`;
  console.log(`[TABLES] ${NAME} has ${rows.length} tables:`);
  for (const r of rows) {
    const t = r[tableKey];
    const [c] = await conn.query(`SELECT COUNT(*) AS n FROM \`${t}\``);
    console.log(`   - ${t.padEnd(35)} rows=${c[0].n}`);
  }
  await conn.end();
})().catch(err => { console.error('[TABLES] Fatal:', err.message); process.exit(1); });