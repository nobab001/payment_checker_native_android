/**
 * scripts/run-schema.js
 *
 * One-shot helper that reads `backend/schema.sql`, strips out any
 * `CREATE DATABASE` / `USE` statements (we connect to the target DB directly
 * via the pool), splits the remaining SQL into individual statements, and
 * executes each one with multipleStatements-style splitting via a simple
 * state-machine parser.
 *
 * Includes FK-aware two-pass execution:
 *   Pass 1: run every statement in order, ignoring FK errors (errno 150/1216/1452)
 *   Pass 2: re-run the failed statements — by now all parents exist
 *
 * All CREATE TABLE statements use `IF NOT EXISTS`, so running this script
 * on an already-initialized database is safe — it's an idempotent migration
 * helper, designed to be invoked automatically on first server start.
 *
 * Usage:
 *   node scripts/run-schema.js
 */

const fs   = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
require('dotenv').config();

const SCHEMA_PATH = path.join(__dirname, '..', 'schema.sql');

const DB_HOST = process.env.DB_HOST || '127.0.0.1';
const DB_PORT = parseInt(process.env.DB_PORT || '3306', 10);
const DB_USER = process.env.DB_USER || 'root';
const DB_PASS = process.env.DB_PASS || '';
const DB_NAME = process.env.DB_NAME || 'payment_checker_db';

/**
 * Splits a SQL string into individual executable statements.
 * Handles single-quoted strings, double-quoted identifiers, and
 * backtick-quoted identifiers, plus `--` line comments.
 */
function splitSqlStatements(sql) {
  const statements = [];
  let buf = '';
  let i = 0;
  let inSingle = false;
  let inDouble = false;
  let inBacktick = false;
  let inLineComment = false;
  let inBlockComment = false;

  while (i < sql.length) {
    const ch = sql[i];
    const next = sql[i + 1];

    if (inLineComment) {
      if (ch === '\n') inLineComment = false;
      i++;
      continue;
    }
    if (inBlockComment) {
      if (ch === '*' && next === '/') { inBlockComment = false; i += 2; continue; }
      i++;
      continue;
    }
    if (inSingle) {
      buf += ch;
      if (ch === '\\' && next !== undefined) { buf += next; i += 2; continue; }
      if (ch === "'") inSingle = false;
      i++;
      continue;
    }
    if (inDouble) {
      buf += ch;
      if (ch === '\\' && next !== undefined) { buf += next; i += 2; continue; }
      if (ch === '"') inDouble = false;
      i++;
      continue;
    }
    if (inBacktick) {
      buf += ch;
      if (ch === '`') inBacktick = false;
      i++;
      continue;
    }

    if (ch === '-' && next === '-') { inLineComment = true; i += 2; continue; }
    if (ch === '/' && next === '*') { inBlockComment = true; i += 2; continue; }
    if (ch === "'") { inSingle = true; buf += ch; i++; continue; }
    if (ch === '"') { inDouble = true; buf += ch; i++; continue; }
    if (ch === '`') { inBacktick = true; buf += ch; i++; continue; }

    if (ch === ';') {
      const trimmed = buf.trim();
      if (trimmed.length > 0) statements.push(trimmed);
      buf = '';
      i++;
      continue;
    }

    buf += ch;
    i++;
  }

  const tail = buf.trim();
  if (tail.length > 0) statements.push(tail);

  return statements;
}

/**
 * Strip statements that would try to switch database or create one
 * (we manage the connection ourselves via the pool).
 */
function filterStatements(statements) {
  return statements.filter(stmt => {
    const s = stmt.toUpperCase();
    if (s.startsWith('CREATE DATABASE')) return false;
    if (s.startsWith('USE ')) return false;
    return true;
  });
}

/**
 * Best-effort reordering: put CREATE TABLE statements with no FK first,
 * then everything else in original order. This is a coarse heuristic —
 * the real safety net is the two-pass execution.
 */
function reorderForFks(statements) {
  const createsNoFk = [];
  const others      = [];
  const re = /CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?/i;

  for (const stmt of statements) {
    const m = stmt.match(re);
    if (m && !/FOREIGN\s+KEY/i.test(stmt)) {
      createsNoFk.push(stmt);
    } else {
      others.push(stmt);
    }
  }
  return [...createsNoFk, ...others];
}

/**
 * MySQL error codes that mean "table is fine or a retry will help".
 *  - ER_TABLE_EXISTS_ERROR (1050) — IF NOT EXISTS worked
 *  - ER_DUP_ENTRY (1062)            — INSERT ... ON DUPLICATE KEY re-ran
 *  - ER_DUP_KEYNAME (1061)          — CREATE INDEX duplicate
 *  - ER_FK_DUP_NAME (1826)          — duplicate FK constraint name
 */
function isBenignError(code) {
  return code === 'ER_TABLE_EXISTS_ERROR'
      || code === 'ER_DUP_ENTRY'
      || code === 'ER_DUP_KEYNAME'
      || code === 'ER_FK_DUP_NAME';
}

/**
 * Reads every *.sql file from backend/db/migrations/ in alphabetical order
 * (after the main schema). Each migration is also split into statements and
 * executed via the same two-pass runner.
 */
function collectMigrationStatements() {
  const migrationsDir = path.join(__dirname, '..', 'db', 'migrations');
  if (!fs.existsSync(migrationsDir)) return [];

  const files = fs.readdirSync(migrationsDir)
    .filter(f => f.toLowerCase().endsWith('.sql'))
    .sort();

  const out = [];
  for (const f of files) {
    const full = path.join(migrationsDir, f);
    const sql  = fs.readFileSync(full, 'utf8');
    const all  = splitSqlStatements(sql);
    const filtered = filterStatements(all);
    out.push({ file: f, statements: reorderForFks(filtered) });
  }
  return out;
}

async function main() {
  console.log('[SCHEMA] Reading', SCHEMA_PATH);
  const raw = fs.readFileSync(SCHEMA_PATH, 'utf8');

  const allStatements = splitSqlStatements(raw);
  const filtered      = filterStatements(allStatements);
  const statements    = reorderForFks(filtered);

  const migrations = collectMigrationStatements();

  console.log(`[SCHEMA] Found ${allStatements.length} total statements in schema.sql`);
  console.log(`[SCHEMA] After filtering: ${filtered.length} executable statements`);
  console.log(`[SCHEMA] Migrations: ${migrations.length} file(s) loaded`);

  // Make sure the target database exists (uses an out-of-pool connection)
  const bootstrap = await mysql.createConnection({
    host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
    multipleStatements: false,
  });
  await bootstrap.query(
    `CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
  );
  console.log(`[SCHEMA] ✅ Database "${DB_NAME}" ready.`);
  await bootstrap.end();

  // Now connect WITHIN the target database and execute each statement
  const conn = await mysql.createConnection({
    host: DB_HOST, port: DB_PORT, user: DB_USER, password: DB_PASS,
    database: DB_NAME, multipleStatements: false,
  });

  let success = 0;
  let benign  = 0;
  const failed = [];

  async function runPass(label, stmts) {
    console.log(`\n[SCHEMA] ── ${label} (${stmts.length} statements) ──`);
    for (const stmt of stmts) {
      const preview = stmt.replace(/\s+/g, ' ').slice(0, 80);
      try {
        await conn.query(stmt);
        console.log(`[SCHEMA] ✅ ${preview}${stmt.length > 80 ? '…' : ''}`);
        success++;
      } catch (err) {
        const code = err.code || '';
        if (isBenignError(code)) {
          console.log(`[SCHEMA] ↺  ${preview}  (skipped: ${code})`);
          benign++;
          continue;
        }
        // Save the statement for the retry pass
        failed.push({ stmt, err, code });
        console.error(`[SCHEMA] ❌ ${preview}`);
        console.error(`         [${code}] ${err.message}`);
      }
    }
  }

  // Pass 1 — initial run with FK-naive ordering
  await runPass('Pass 1: schema.sql', statements);

  // Pass 1b — run all migration files in order
  for (const m of migrations) {
    await runPass(`Pass 1b: migrations/${m.file}`, m.statements);
  }

  // Pass 2 — retry every statement that failed (likely FK order issues)
  if (failed.length > 0) {
    console.log(`\n[SCHEMA] Retrying ${failed.length} failed statement(s) — parents should now exist.`);
    const retryStmts = failed.map(f => f.stmt);
    failed.length = 0;

    console.log(`\n[SCHEMA] ── Pass 2: FK retry ──`);
    for (const stmt of retryStmts) {
      const preview = stmt.replace(/\s+/g, ' ').slice(0, 80);
      try {
        await conn.query(stmt);
        console.log(`[SCHEMA] ✅ ${preview}${stmt.length > 80 ? '…' : ''}`);
        success++;
      } catch (err) {
        const code = err.code || '';
        if (isBenignError(code)) {
          console.log(`[SCHEMA] ↺  ${preview}  (skipped: ${code})`);
          benign++;
          continue;
        }
        console.error(`[SCHEMA] ❌ ${preview}`);
        console.error(`         [${code}] ${err.message}`);
        failed.push({ stmt, err, code });
      }
    }
  }

  await conn.end();

  console.log('\n=================================================');
  console.log(`[SCHEMA] Done. success=${success} benign=${benign} failed=${failed.length}`);
  console.log('=================================================');
  if (failed.length > 0) {
    console.error('[SCHEMA] ❗ Statements that could not be applied:');
    for (const f of failed) {
      console.error(`  • [${f.code}] ${f.err.message}`);
    }
    process.exit(1);
  }
}

main().catch(err => {
  console.error('[SCHEMA] Fatal:', err);
  process.exit(1);
});