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

-- 3. Add user_id to sms_templates for custom user-defined templates/sender IDs
ALTER TABLE `sms_templates` 
  ADD COLUMN IF NOT EXISTS `user_id` INT DEFAULT NULL AFTER `id`,
  ADD INDEX IF NOT EXISTS `idx_template_user` (`user_id`),
  ADD CONSTRAINT `fk_template_user` FOREIGN KEY IF NOT EXISTS (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
