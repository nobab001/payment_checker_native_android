-- Add category column for checkout tab separation (idempotent for XAMPP)
ALTER TABLE `sms_templates`
  ADD COLUMN IF NOT EXISTS `category` VARCHAR(32) NULL DEFAULT 'SEND_MONEY' AFTER `template_name`;

-- Backfill from template_name patterns
UPDATE `sms_templates`
SET `category` = 'SEND_MONEY'
WHERE (`category` IS NULL OR `category` = '')
  AND (`template_name` LIKE '%Personal%' OR `template_name` LIKE '%Send Money%');

UPDATE `sms_templates`
SET `category` = 'CASH_OUT'
WHERE (`category` IS NULL OR `category` = '')
  AND (`template_name` LIKE '%Agent%' OR `template_name` LIKE '%Cash Out%' OR `template_name` LIKE '%Cash In%');

UPDATE `sms_templates`
SET `category` = 'PAYMENT'
WHERE (`category` IS NULL OR `category` = '')
  AND (`template_name` LIKE '%Merchant%' OR `template_name` LIKE '%Payment%');

UPDATE `sms_templates`
SET `category` = 'BANK'
WHERE (`category` IS NULL OR `category` = '')
  AND `template_name` LIKE '%Bank%';

UPDATE `sms_templates`
SET `category` = 'CARD'
WHERE (`category` IS NULL OR `category` = '')
  AND `template_name` LIKE '%Card%';

UPDATE `sms_templates`
SET `category` = 'SEND_MONEY'
WHERE `category` IS NULL OR `category` = '';
