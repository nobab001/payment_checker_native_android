-- =============================================================================
-- ✅ Paychek NTK — phpMyAdmin Migration Script
-- Database : paychek_online_v2
-- Run      : phpMyAdmin → paychek_online_v2 → SQL ট্যাব → এই পুরো ফাইলটি পেস্ট করুন
-- Safe     : সব কমান্ড IF NOT EXISTS / IF EXISTS দিয়ে লেখা — বারবার রান করা যাবে
-- =============================================================================

USE `paychek_online_v2`;

-- =============================================================================
-- MIGRATION 1: sms_settings — provider_name কলাম যোগ
-- SMS প্রোভাইডারের মানব-পাঠযোগ্য নাম (e.g. "Green Web SMS", "Twilio")
-- =============================================================================
ALTER TABLE `sms_settings`
  ADD COLUMN IF NOT EXISTS `provider_name` VARCHAR(128) DEFAULT NULL
    COMMENT 'Human-readable provider label e.g. "Green Web SMS"'
  AFTER `id`;

-- =============================================================================
-- MIGRATION 2: email_accounts — last_reset_at কলাম যোগ
-- sent_today কখন শেষবার 0-তে রিসেট হয়েছিল তা ট্র্যাক করার জন্য
-- =============================================================================
ALTER TABLE `email_accounts`
  ADD COLUMN IF NOT EXISTS `last_reset_at` TIMESTAMP NULL DEFAULT NULL
    COMMENT 'Last time sent_today was reset to 0'
  AFTER `sent_today`;

-- =============================================================================
-- MIGRATION 3: registered_devices — hardware fingerprint কলামগুলো যোগ
-- Android Device Binding Anti-Fraud System-এর জন্য প্রয়োজনীয়
-- =============================================================================
ALTER TABLE `registered_devices`
  ADD COLUMN IF NOT EXISTS `android_id`            VARCHAR(255) DEFAULT NULL
    COMMENT 'Raw Android ID (unhashed)'
  AFTER `device_id`,

  ADD COLUMN IF NOT EXISTS `hardware_fingerprint`  VARCHAR(512) DEFAULT NULL
    COMMENT 'Build.FINGERPRINT from device'
  AFTER `android_id`,

  ADD COLUMN IF NOT EXISTS `sim_slot_ids`          TEXT DEFAULT NULL
    COMMENT 'JSON array of SIM subscription IDs'
  AFTER `hardware_fingerprint`;

-- =============================================================================
-- MIGRATION 4: device_trial_logs — নতুন টেবিল
-- Anti-Fraud: ট্রায়াল অ্যাবিউজ ট্র্যাকিং লগ
-- =============================================================================
CREATE TABLE IF NOT EXISTS `device_trial_logs` (
  `id`                   INT           NOT NULL AUTO_INCREMENT,
  `device_id`            VARCHAR(255)  NOT NULL COMMENT 'Hashed Android Device ID',
  `android_id`           VARCHAR(255)  DEFAULT NULL,
  `hardware_fingerprint` VARCHAR(512)  DEFAULT NULL,
  `sim_slot_ids`         TEXT          DEFAULT NULL,
  `user_id`              INT           DEFAULT NULL COMMENT 'Associated user account',
  `has_used_trial`       TINYINT(1)    NOT NULL DEFAULT 0
    COMMENT '1 = এই হার্ডওয়্যারে ট্রায়াল একবার ব্যবহার হয়েছে',
  `action`               VARCHAR(64)   NOT NULL DEFAULT 'trial_started'
    COMMENT 'trial_started | trial_expired | trial_abuse_detected',
  `ip_address`           VARCHAR(64)   DEFAULT NULL,
  `created_at`           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_dtl_device`  (`device_id`),
  INDEX `idx_dtl_android` (`android_id`(64)),
  INDEX `idx_dtl_user`    (`user_id`),
  CONSTRAINT `fk_dtl_user`
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Anti-fraud trial usage tracking log';

-- =============================================================================
-- MIGRATION 5: global_config — নতুন config key যোগ (safe insert)
-- =============================================================================
INSERT INTO `global_config` (`config_key`, `config_value`) VALUES
  ('trial_days',            '7'),
  ('telegram_support_link', ''),
  ('whatsapp_support_link', ''),
  ('facebook_support_link', ''),
  ('youtube_support_link',  '')
ON DUPLICATE KEY UPDATE `config_key` = `config_key`; -- no-op if already exists

-- =============================================================================
-- ✅ VERIFICATION — মাইগ্রেশন সফল হয়েছে কিনা চেক করুন
-- এই কোয়েরিগুলো রান করলে নতুন কলামগুলো দেখা যাবে
-- =============================================================================

-- sms_settings-এর কলাম লিস্ট:
SHOW COLUMNS FROM `sms_settings`;

-- email_accounts-এর কলাম লিস্ট:
SHOW COLUMNS FROM `email_accounts`;

-- registered_devices-এর নতুন কলামগুলো:
SHOW COLUMNS FROM `registered_devices` LIKE 'android_id';
SHOW COLUMNS FROM `registered_devices` LIKE 'hardware_fingerprint';
SHOW COLUMNS FROM `registered_devices` LIKE 'sim_slot_ids';

-- device_trial_logs টেবিল তৈরি হয়েছে কিনা:
SHOW TABLES LIKE 'device_trial_logs';

-- =============================================================================
-- END
-- =============================================================================
