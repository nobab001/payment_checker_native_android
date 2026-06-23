-- =============================================================================
-- Migration: Create checkout_view_templates and seed default instructions
-- Database: paychek_online_v2
-- Run: phpMyAdmin-এ SQL ট্যাবে রান করুন
-- =============================================================================

-- 1. Create checkout_view_templates table
CREATE TABLE IF NOT EXISTS `checkout_view_templates` (
  `id`                          INT      NOT NULL AUTO_INCREMENT,
  `sms_template_id`             INT      NOT NULL,
  `single_number_instruction`   TEXT     NOT NULL COMMENT 'Instruction shown when 1 number is active',
  `multiple_number_instruction` TEXT     NOT NULL COMMENT 'Instruction shown when >1 numbers are active',
  `created_at`                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_sms_template` (`sms_template_id`),
  CONSTRAINT `fk_checkout_view_sms_template`
    FOREIGN KEY (`sms_template_id`) REFERENCES `sms_templates`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Default checkout view templates seeds
INSERT INTO `checkout_view_templates` (`sms_template_id`, `single_number_instruction`, `multiple_number_instruction`) VALUES
  (1, 'নিচের বিকাশ পার্সোনাল নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় বিকাশ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।'),
  (2, 'নিচের নগদ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় নগদ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।'),
  (3, 'নিচের রকেট নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় রকেট নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।'),
  (4, 'নিচের উপায় নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।', 'নিচের যেকোনো একটি সক্রিয় উপায় নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।')
ON DUPLICATE KEY UPDATE 
  `single_number_instruction` = VALUES(`single_number_instruction`),
  `multiple_number_instruction` = VALUES(`multiple_number_instruction`);
