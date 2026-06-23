-- =============================================================================
-- Migration: Rename raw_body to full_sms and verify composite indexes
-- Database: paychek_online_v2
-- Run: phpMyAdmin-এ SQL ট্যাবে রান করুন
-- =============================================================================

-- 1. Rename raw_body to full_sms in sms_history (if not already renamed)
ALTER TABLE `sms_history` 
  CHANGE COLUMN `raw_body` `full_sms` TEXT DEFAULT NULL COMMENT 'Original SMS text';

-- 2. Verify and add Composite Index idx_user_trx for B-Tree optimization
-- (প্রথমে যদি সূচীটি না থাকে তবে যোগ হবে, নাহলে ত্রুটি এড়াতে সরাসরি ইনডেক্স তৈরি)
ALTER TABLE `sms_history` 
  ADD INDEX IF NOT EXISTS `idx_user_trx` (`user_id`, `trx_id`);

-- 3. Modify sms_templates table structure
ALTER TABLE `sms_templates` 
  ADD COLUMN IF NOT EXISTS `user_id` INT DEFAULT NULL AFTER `id`,
  ADD COLUMN IF NOT EXISTS `template_name` VARCHAR(128) NOT NULL DEFAULT '' AFTER `user_id`,
  ADD COLUMN IF NOT EXISTS `matching_keyword` VARCHAR(255) DEFAULT NULL AFTER `sender_id`,
  ADD COLUMN IF NOT EXISTS `regex_pattern` TEXT DEFAULT NULL AFTER `matching_keyword`,
  ADD COLUMN IF NOT EXISTS `is_official` TINYINT(1) NOT NULL DEFAULT 1 AFTER `regex_pattern`,
  ADD INDEX IF NOT EXISTS `idx_template_user` (`user_id`),
  ADD INDEX IF NOT EXISTS `idx_template_sender` (`sender_id`);

-- Drop old columns if they exist (clean up database)
ALTER TABLE `sms_templates` DROP COLUMN IF EXISTS `customer_preview`;
ALTER TABLE `sms_templates` DROP COLUMN IF EXISTS `formats`;

-- 4. Add template_id to gateway_methods table
ALTER TABLE `gateway_methods`
  ADD COLUMN IF NOT EXISTS `template_id` INT DEFAULT NULL AFTER `user_id`,
  ADD CONSTRAINT `fk_gateway_template` FOREIGN KEY IF NOT EXISTS (`template_id`) REFERENCES `sms_templates` (`id`) ON DELETE SET NULL;

-- 5. Insert default official SMS templates
INSERT INTO `sms_templates` (`id`, `template_name`, `sender_id`, `matching_keyword`, `regex_pattern`, `is_official`, `is_active`) VALUES
  (1, 'bKash Personal', 'bKash', 'You have received,Tk.,Ref:', 'You have received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})', 1, 1),
  (2, 'Nagad Personal', 'NAGAD', 'received cash in Tk,TrxID:', 'received cash in Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})', 1, 1),
  (3, 'Rocket Personal', '16216', 'received Tk,TrxID:', 'received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})', 1, 1),
  (4, 'Upay Personal', 'upay', 'received Tk,TrxID', 'received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})', 1, 1)
ON DUPLICATE KEY UPDATE 
  `template_name` = VALUES(`template_name`),
  `sender_id` = VALUES(`sender_id`),
  `matching_keyword` = VALUES(`matching_keyword`),
  `regex_pattern` = VALUES(`regex_pattern`),
  `is_official` = VALUES(`is_official`),
  `is_active` = VALUES(`is_active`);
