/**
 * scripts/recover-gateway-methods.js
 *
 * One-shot recovery tool for the "ghost gateway_methods" syndrome:
 *   catalog (INNODB_SYS_TABLES) holds a row for `gateway_methods`
 *   but the underlying .frm / .ibd files are missing on disk, so
 *   every `DESCRIBE gateway_methods` returns ERROR 1146.
 *
 * Strategy:
 *   1. refuse to run unless DB_USER=root (orphan cleanup needs
 *      SUPER / PROCESS privileges)
 *   2. SET FOREIGN_KEY_CHECKS=0
 *   3. delete the orphan row from INNODB_SYS_TABLES
 *      (and dependent rows in INNODB_SYS_INDEXES,
 *       INNODB_SYS_COLUMNS, INNODB_SYS_FIELDS)
 *   4. CREATE TABLE gateway_methods (...) with the agreed schema
 *   5. SET FOREIGN_KEY_CHECKS=1
 *   6. DESCRIBE gateway_methods to print final column list
 *
 * Usage:
 *   cd backend
 *   node scripts/recover-gateway-methods.js
 *
 * Exit codes:
 *   0  recovered successfully
 *   1  unexpected MySQL error
 *   2  DB_USER is not root — skipped
 */

const mysql = require('mysql2/promise');
require('dotenv').config();

const DB_HOST = process.env.DB_HOST || '127.0.0.1';
const DB_PORT = parseInt(process.env.DB_PORT || '3306', 10);
const DB_USER = process.env.DB_USER || 'root';
const DB_PASS = process.env.DB_PASS || '';
const DB_NAME = process.env.DB_NAME || 'paychek_online_v2';

const TARGET_TABLE = `${DB_NAME}/gateway_methods`;

const CREATE_SQL = `
CREATE TABLE \`gateway_methods\` (
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
`.trim();

function header(s) { console.log(`\n========== ${s} ==========`); }

(async () => {
  // ── 1. privilege gate ────────────────────────────────────────────────────
  if (DB_USER !== 'root') {
    console.error(`[RECOVER] DB_USER is "${DB_USER}", not "root".`);
    console.error(`[RECOVER] Orphan cleanup requires SUPER/PROCESS privileges.`);
    console.error(`[RECOVER] Set DB_USER=root in backend/.env and re-run.`);
    process.exit(2);
  }

  const conn = await mysql.createConnection({
    host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS, database: DB_NAME,
    multipleStatements: false,
  });

  try {
    header('PHASE 1: locate orphan in INNODB_SYS_TABLES');
    const [rows] = await conn.query(
      `SELECT TABLE_ID, NAME, SPACE, FLAG, N_COLS
         FROM information_schema.INNODB_SYS_TABLES
        WHERE NAME = ?`,
      [TARGET_TABLE]
    );

    if (rows.length === 0) {
      console.log(`[RECOVER] No catalog entry for "${TARGET_TABLE}". Nothing to clean.`);
    } else {
      const tableId = rows[0].TABLE_ID;
      console.log(`[RECOVER] Found orphan TABLE_ID=${tableId}`);

      header('PHASE 2: delete dependent dictionary rows');
      await conn.query('SET FOREIGN_KEY_CHECKS=0');

      // Delete in dependency order: FIELDS → COLUMNS → INDEXES → TABLES
      const [idxRows] = await conn.query(
        `SELECT INDEX_ID FROM information_schema.INNODB_SYS_INDEXES WHERE TABLE_ID = ?`,
        [tableId]
      );
      const indexIds = idxRows.map(r => r.INDEX_ID);
      console.log(`[RECOVER] Found ${indexIds.length} dependent index row(s).`);

      if (indexIds.length) {
        const [fRes] = await conn.query(
          `DELETE FROM information_schema.INNODB_SYS_FIELDS WHERE INDEX_ID IN (?)`,
          [indexIds]
        );
        console.log(`[RECOVER] Deleted ${fRes.affectedRows} row(s) from INNODB_SYS_FIELDS.`);
      }

      const [iRes] = await conn.query(
        `DELETE FROM information_schema.INNODB_SYS_INDEXES WHERE TABLE_ID = ?`,
        [tableId]
      );
      console.log(`[RECOVER] Deleted ${iRes.affectedRows} row(s) from INNODB_SYS_INDEXES.`);

      const [cRes] = await conn.query(
        `DELETE FROM information_schema.INNODB_SYS_COLUMNS WHERE TABLE_ID = ?`,
        [tableId]
      );
      console.log(`[RECOVER] Deleted ${cRes.affectedRows} row(s) from INNODB_SYS_COLUMNS.`);

      const [tRes] = await conn.query(
        `DELETE FROM information_schema.INNODB_SYS_TABLES WHERE TABLE_ID = ?`,
        [tableId]
      );
      console.log(`[RECOVER] Deleted ${tRes.affectedRows} row(s) from INNODB_SYS_TABLES.`);
    }

    header('PHASE 3: drop & recreate table on disk');
    try { await conn.query('DROP TABLE IF EXISTS `gateway_methods`'); }
    catch (e) { console.log(`[RECOVER] DROP skipped: ${e.code} ${e.sqlMessage || e.message}`); }

    await conn.query(CREATE_SQL);
    console.log(`[RECOVER] ✅ Fresh gateway_methods table created.`);

    await conn.query('SET FOREIGN_KEY_CHECKS=1');

    header('PHASE 4: DESCRIBE verify');
    const [cols] = await conn.query('DESCRIBE `gateway_methods`');
    console.log(`Field                  Type                Null   Key   Default   Extra`);
    console.log(`----------------------------------------------------------------------`);
    for (const c of cols) {
      const f = String(c.Field).padEnd(22);
      const t = String(c.Type).padEnd(20);
      const n = String(c.Null).padEnd(6);
      const k = String(c.Key || '').padEnd(5);
      const d = String(c.Default ?? 'NULL').padEnd(9);
      console.log(`${f}${t}${n}${k}${d}${c.Extra || ''}`);
    }

    header('DONE');
    console.log(`[RECOVER] ✅ gateway_methods is healthy. ${cols.length} columns visible.`);
    process.exit(0);
  } catch (err) {
    console.error(`[RECOVER] ❌ Fatal: ${err.code || ''} ${err.sqlMessage || err.message}`);
    process.exit(1);
  } finally {
    await conn.end();
  }
})();