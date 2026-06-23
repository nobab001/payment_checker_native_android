// Quick DB creation utility — ensures payment_checker_db exists
// Uses the same credentials as .env (DB_HOST, DB_USER, DB_PASS)
const mysql = require('mysql2/promise');
const dotenv = require('dotenv');

dotenv.config();

const dbName = process.env.DB_NAME || 'payment_checker_db';

(async () => {
  let conn;
  try {
    conn = await mysql.createConnection({
      host: process.env.DB_HOST || '127.0.0.1',
      port: parseInt(process.env.DB_PORT || '3306', 10),
      user: process.env.DB_USER || 'root',
      password: process.env.DB_PASS || '',
    });

    console.log(`[create-db] Connected to MySQL at ${process.env.DB_HOST}:${process.env.DB_PORT}`);

    await conn.query(
      `CREATE DATABASE IF NOT EXISTS \`${dbName}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
    );
    console.log(`[create-db] ✅ Database "${dbName}" is ready.`);

    const [rows] = await conn.query('SHOW DATABASES');
    const found = rows.find(r => Object.values(r)[0] === dbName);
    if (found) {
      console.log(`[create-db] ✅ Verified: "${dbName}" exists in MySQL.`);
    } else {
      console.error(`[create-db] ❌ Failed to verify "${dbName}" creation.`);
      process.exit(1);
    }
  } catch (err) {
    console.error('[create-db] ❌ Error:', err.message);
    process.exit(1);
  } finally {
    if (conn) await conn.end();
  }
})();
