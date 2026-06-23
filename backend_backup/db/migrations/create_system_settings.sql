-- =============================================================================
-- Migration: Create system_settings table (adminController.getOtpFormat)
-- This table was referenced by adminController.js / authController.js but was
-- never added to schema.sql — adds it now to fix the 500 on /api/admin/otp-format
-- =============================================================================

CREATE TABLE IF NOT EXISTS `system_settings` (
  `id`            INT          NOT NULL AUTO_INCREMENT,
  `setting_key`   VARCHAR(128) NOT NULL,
  `setting_value` TEXT         DEFAULT NULL,
  `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed the OTP format template default
INSERT INTO `system_settings` (`setting_key`, `setting_value`) VALUES
  ('otp_format_template', 'আপনার প্রিয় পে-চেক অ্যাপ ভেরিফিকেশন কোড হলো: {otp}। কোডটি গোপন রাখুন।')
ON DUPLICATE KEY UPDATE `setting_value` = VALUES(`setting_value`);
