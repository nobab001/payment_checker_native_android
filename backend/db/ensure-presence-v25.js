/**
 * Device Communication v2.5 — Phase 1 schema + default policies (idempotent).
 */
const prisma = require('../db/prisma');

const DEFAULT_POLICIES = [
  {
    package_key: 'welcome',
    heartbeat_interval_sec: 300,
    probe_steps_json: '[60,120,180]',
    worker_sweep_sec: 30,
    sync_interval_sec: 1800,
    jitter_sec: 30,
    presence_engine_version: 2, // Phase 4: Trial package on v2.5
  },
  {
    package_key: 'gateway',
    heartbeat_interval_sec: 300,
    probe_steps_json: '[60,120,180]',
    worker_sweep_sec: 30,
    sync_interval_sec: 1800,
    jitter_sec: 30,
    presence_engine_version: 1,
  },
  {
    package_key: 'personal_business',
    heartbeat_interval_sec: 900,
    probe_steps_json: '[300,300]',
    worker_sweep_sec: 60,
    sync_interval_sec: 3600,
    jitter_sec: 20,
    presence_engine_version: 1,
  },
  {
    package_key: 'personal',
    heartbeat_interval_sec: 1800,
    probe_steps_json: '[900,900]',
    worker_sweep_sec: 60,
    sync_interval_sec: 3600,
    jitter_sec: 30,
    presence_engine_version: 1,
  },
];

async function columnExists(table, column) {
  const rows = await prisma.$queryRawUnsafe(
    `SELECT COUNT(*) AS c FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
    table,
    column,
  );
  return Number(rows[0]?.c || 0) > 0;
}

async function ensurePresenceV25Schema() {
  if (!(await columnExists('registered_devices', 'device_online'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE registered_devices
      ADD COLUMN device_online TINYINT NOT NULL DEFAULT 1
      COMMENT 'v2.5 presence: 1=alive for checkout (worker/heartbeat only)'
      AFTER last_battery_percent
    `);
  }

  if (!(await columnExists('sim_slot_bindings', 'merchant_enabled'))) {
    await prisma.$executeRawUnsafe(`
      ALTER TABLE sim_slot_bindings
      ADD COLUMN merchant_enabled TINYINT NOT NULL DEFAULT 1
      COMMENT 'v2.5 merchant toggle; worker never writes this'
      AFTER is_active
    `);
    await prisma.$executeRawUnsafe(`
      UPDATE sim_slot_bindings SET merchant_enabled = is_active
    `);
  }

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS comm_policy (
      id INT NOT NULL AUTO_INCREMENT,
      package_key VARCHAR(32) NOT NULL,
      heartbeat_interval_sec INT NOT NULL DEFAULT 300,
      probe_steps_json JSON NOT NULL,
      worker_sweep_sec INT NOT NULL DEFAULT 30,
      sync_interval_sec INT NOT NULL DEFAULT 1800,
      jitter_sec INT NOT NULL DEFAULT 30,
      presence_engine_version TINYINT NOT NULL DEFAULT 1 COMMENT '1=legacy 2=v2.5',
      is_active TINYINT NOT NULL DEFAULT 1,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_comm_policy_package (package_key)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  for (const p of DEFAULT_POLICIES) {
    const existing = await prisma.$queryRaw`
      SELECT id FROM comm_policy WHERE package_key = ${p.package_key} LIMIT 1
    `;
    if (existing?.length) continue;
    await prisma.$executeRawUnsafe(
      `INSERT INTO comm_policy (
        package_key, heartbeat_interval_sec, probe_steps_json,
        worker_sweep_sec, sync_interval_sec, jitter_sec, presence_engine_version
      ) VALUES (?, ?, ?, ?, ?, ?, ?)`,
      p.package_key,
      p.heartbeat_interval_sec,
      p.probe_steps_json,
      p.worker_sweep_sec,
      p.sync_interval_sec,
      p.jitter_sec,
      p.presence_engine_version,
    );
  }

  await prisma.$executeRawUnsafe(`
    INSERT INTO global_config (config_key, config_value)
    VALUES ('presence_v2_global_enabled', '0')
    ON DUPLICATE KEY UPDATE config_key = config_key
  `);

  // Phase 3: shadow compare on by default until gateway cutover
  await prisma.$executeRawUnsafe(`
    INSERT INTO global_config (config_key, config_value)
    VALUES ('presence_v2_shadow_mode', '1')
    ON DUPLICATE KEY UPDATE config_key = config_key
  `);

  // Phase 4 rollout: Trial (welcome) on v2.5 first — rollback: SET presence_engine_version=1
  await prisma.$executeRawUnsafe(`
    UPDATE comm_policy
    SET presence_engine_version = 2
    WHERE package_key = 'welcome' AND presence_engine_version = 1
  `);
}

module.exports = { ensurePresenceV25Schema, DEFAULT_POLICIES };
