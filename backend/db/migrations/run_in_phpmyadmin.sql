-- =============================================================================
-- ✅ Paychek NTK — phpMyAdmin তে এই SQL টি রান করুন
-- paychek_online_v2 ডাটাবেজ select করে SQL ট্যাবে paste করুন
-- =============================================================================

USE `paychek_online_v2`;

-- ── 1. sms_settings: provider_name কলাম ──────────────────────────────────
ALTER TABLE `sms_settings`
  ADD COLUMN IF NOT EXISTS `provider_name` VARCHAR(128) DEFAULT NULL
    COMMENT 'Provider label e.g. Green Web SMS'
  AFTER `id`;

-- ── 2. email_accounts: last_reset_at কলাম ────────────────────────────────
ALTER TABLE `email_accounts`
  ADD COLUMN IF NOT EXISTS `last_reset_at` TIMESTAMP NULL DEFAULT NULL
    COMMENT 'Last time sent_today was reset to 0'
  AFTER `sent_today`;

-- ── 3. registered_devices: hardware signature কলামগুলো ──────────────────
ALTER TABLE `registered_devices`
  ADD COLUMN IF NOT EXISTS `android_id`           VARCHAR(255) DEFAULT NULL
    COMMENT 'Raw Android ID'
  AFTER `device_id`,
  ADD COLUMN IF NOT EXISTS `hardware_fingerprint` VARCHAR(512) DEFAULT NULL
    COMMENT 'Build.FINGERPRINT'
  AFTER `android_id`,
  ADD COLUMN IF NOT EXISTS `sim_slot_ids`         TEXT DEFAULT NULL
    COMMENT 'SIM subscription IDs JSON'
  AFTER `hardware_fingerprint`;

-- ── 4. device_trial_logs: has_used_trial কলাম (table already exists!) ────
ALTER TABLE `device_trial_logs`
  ADD COLUMN IF NOT EXISTS `has_used_trial` TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '1 = এই হার্ডওয়্যারে ট্রায়াল একবার ব্যবহার হয়েছে'
  AFTER `user_id`,
  ADD COLUMN IF NOT EXISTS `android_id`           VARCHAR(255) DEFAULT NULL
    COMMENT 'Raw Android ID'
  AFTER `device_id`,
  ADD COLUMN IF NOT EXISTS `hardware_fingerprint` VARCHAR(512) DEFAULT NULL
    COMMENT 'Build.FINGERPRINT'
  AFTER `android_id`,
  ADD COLUMN IF NOT EXISTS `sim_slot_ids`         TEXT DEFAULT NULL
    COMMENT 'SIM subscription IDs JSON'
  AFTER `hardware_fingerprint`;

-- ── Index যোগ (যদি না থাকে) ──────────────────────────────────────────────
ALTER TABLE `device_trial_logs`
  ADD INDEX IF NOT EXISTS `idx_dtl_android` (`android_id`(64));

-- ── ✅ Verification: নিচের কলামগুলো দেখা যাবে ──────────────────────────
SHOW COLUMNS FROM `sms_settings`       LIKE 'provider_name';
SHOW COLUMNS FROM `email_accounts`     LIKE 'last_reset_at';
SHOW COLUMNS FROM `registered_devices` LIKE 'android_id';
SHOW COLUMNS FROM `device_trial_logs`  LIKE 'has_used_trial';
