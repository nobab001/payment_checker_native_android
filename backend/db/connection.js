const mysql = require('mysql2/promise');
require('dotenv').config();

// Create connection pool targeting the MySQL database (XAMPP or VPS)
const pool = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASS || '',
  database: process.env.DB_NAME || 'paychek_online_v2',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  timezone: '+06:00' // BD Local Timezone (+06:00)
});

// Helper to execute queries safely
async function query(sql, params) {
  const [results] = await pool.execute(sql, params);
  return results;
}

module.exports = {
  pool,
  query
};
