const mysql = require('mysql2/promise');
require('dotenv').config();

const DB_HOST = process.env.DB_HOST || '127.0.0.1';
const DB_PORT = parseInt(process.env.DB_PORT || '3306', 10);
const DB_USER = process.env.DB_USER || 'root';
const DB_PASS = process.env.DB_PASS || '';
const DB_NAME = process.env.DB_NAME || 'paychek_online_v2';

// Ensure the target database exists before any pool query is attempted.
// This protects the server from "Unknown database" crashes on first run
// or after a fresh MySQL install.
async function ensureDatabaseExists() {
  let bootstrap;
  try {
    bootstrap = await mysql.createConnection({
      host: DB_HOST,
      port: DB_PORT,
      user: DB_USER,
      password: DB_PASS,
    });
    await bootstrap.query(
      `CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
    );
    console.log(`[DB] ✅ Database "${DB_NAME}" is ready.`);
  } catch (err) {
    console.error(`[DB] ❌ Could not ensure database "${DB_NAME}" exists:`, err.message);
    throw err;
  } finally {
    if (bootstrap) await bootstrap.end();
  }
}

// Create connection pool targeting the MySQL database (XAMPP or VPS)
const pool = mysql.createPool({
  host: DB_HOST,
  port: DB_PORT,
  user: DB_USER,
  password: DB_PASS,
  database: DB_NAME,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  timezone: '+06:00', // BD Local Timezone (+06:00)
});

// Helper to execute queries safely
async function query(sql, params) {
  const [results] = await pool.execute(sql, params);
  return results;
}

// ─────────────────────────────────────────────────────────────────────────────
// ensureGatewayMethodsTable()
// Startup guard that protects against the "ghost gateway_methods" syndrome.
// Tries a DESCRIBE first; if the table is missing (ERROR 1146) or corrupt,
// defensively runs CREATE TABLE IF NOT EXISTS to bring it back online.
// No-op when the table is healthy.
// ─────────────────────────────────────────────────────────────────────────────
async function ensureGatewayMethodsTable() {
  try {
    await query('DESCRIBE `gateway_methods`');
    console.log('[DB] ✅ gateway_methods table verified.');
    return true;
  } catch (err) {
    if (err.errno === 1146) {
      console.warn('[DB] ⚠  gateway_methods missing (ERROR 1146) — recreating defensively.');
    } else {
      console.warn(`[DB] ⚠  gateway_methods check failed (${err.code}): ${err.sqlMessage || err.message}`);
    }
    try {
      await query(`
        CREATE TABLE IF NOT EXISTS \`gateway_methods\` (
          \`id\`           INT AUTO_INCREMENT PRIMARY KEY,
          \`user_id\`      VARCHAR(255) NOT NULL,
          \`sim_slot\`     INT NOT NULL DEFAULT 1,
          \`provider\`     VARCHAR(50)  NOT NULL,
          \`number\`       VARCHAR(20)  NOT NULL,
          \`display_name\` VARCHAR(100) NULL,
          \`is_enabled\`   TINYINT(1)   NOT NULL DEFAULT 0,
          \`priority\`     INT          NOT NULL DEFAULT 0,
          \`template_id\`  INT          NULL,
          \`created_at\`   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          \`updated_at\`   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          INDEX \`idx_user\`     (\`user_id\`),
          INDEX \`idx_template\` (\`template_id\`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
      `);
      console.log('[DB] ✅ gateway_methods table created via startup guard.');
      return true;
    } catch (createErr) {
      console.error('[DB] ❌ Could not auto-create gateway_methods:', createErr.sqlMessage || createErr.message);
      return false;
    }
  }

  return true;
}

module.exports = {
  pool,
  query,
  ensureDatabaseExists,
  ensureGatewayMethodsTable,
};
