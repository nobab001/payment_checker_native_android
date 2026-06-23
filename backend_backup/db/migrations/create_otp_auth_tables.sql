-- =============================================================================
-- Migration: Create sms_settings, email_accounts, add index, and seed Agent templates
-- Database: paychek_online_v2
-- Run: phpMyAdmin-এ SQL ট্যাবে রান করুন বা CLI দিয়ে ইমপোর্ট করুন
-- =============================================================================

-- 1. Create sms_settings table
CREATE TABLE IF NOT EXISTS `sms_settings` (
  `id`            INT          NOT NULL AUTO_INCREMENT,
  `api_url`       VARCHAR(512) NOT NULL,
  `api_key`       VARCHAR(255) DEFAULT NULL,
  `sender_id`     VARCHAR(64)  DEFAULT NULL,
  `is_active`     TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create email_accounts table
CREATE TABLE IF NOT EXISTS `email_accounts` (
  `id`            INT          NOT NULL AUTO_INCREMENT,
  `email`         VARCHAR(255) NOT NULL,
  `password`      VARCHAR(255) NOT NULL COMMENT 'SMTP Password / App Password',
  `host`          VARCHAR(255) NOT NULL DEFAULT 'smtp.gmail.com',
  `port`          INT          NOT NULL DEFAULT 465,
  `secure`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 = SSL, 0 = TLS',
  `daily_limit`   INT          NOT NULL DEFAULT 500,
  `sent_today`    INT          NOT NULL DEFAULT 0,
  `is_active`     TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_email_acc` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Add joint index to otps table (if not exists)
ALTER TABLE `otps` ADD INDEX IF NOT EXISTS `idx_contact_code` (`contact`, `code`);

-- 4. Seed new Agent templates into sms_templates
INSERT INTO `sms_templates` (`id`, `template_name`, `sender_id`, `matching_keyword`, `regex_pattern`, `is_official`, `is_active`) VALUES
  (5, 'bKash Agent', 'bKash', 'Cash In,Tk.,Ref:', 'Cash In Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})', 1, 1),
  (6, 'Nagad Agent', 'NAGAD', 'Cash in received,Tk.,TrxID:', 'Cash in received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})', 1, 1),
  (7, 'Rocket Agent', '16216', 'Cash In received,Tk.,TrxID:', 'Cash In received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})', 1, 1),
  (8, 'Upay Agent', 'upay', 'Cash In received,Tk.,TrxID', 'Cash In received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})', 1, 1)
ON DUPLICATE KEY UPDATE 
  `template_name` = VALUES(`template_name`),
  `sender_id` = VALUES(`sender_id`),
  `matching_keyword` = VALUES(`matching_keyword`),
  `regex_pattern` = VALUES(`regex_pattern`),
  `is_official` = VALUES(`is_official`),
  `is_active` = VALUES(`is_active`);

-- 5. Seed new Agent templates into checkout_view_templates
INSERT INTO `checkout_view_templates` (`sms_template_id`, `single_number_instruction`, `multiple_number_instruction`) VALUES
  (5, 'নিচের বিকাশ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় বিকাশ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।'),
  (6, 'নিচের নগদ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় নগদ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।'),
  (7, 'নিচের রকেট এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় রকেট এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।'),
  (8, 'নিচের উপায় এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় উপায় এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।')
ON DUPLICATE KEY UPDATE 
  `single_number_instruction` = VALUES(`single_number_instruction`),
  `multiple_number_instruction` = VALUES(`multiple_number_instruction`);

-- 6. Seed missing facebook and youtube support links in global_config
INSERT INTO `global_config` (`config_key`, `config_value`) VALUES
  ('facebook_support_link', ''),
  ('youtube_support_link', '')
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;
