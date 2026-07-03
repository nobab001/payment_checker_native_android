-- XAMPP-safe: add category column if missing (ignore duplicate column error)
SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sms_templates'
    AND COLUMN_NAME = 'category'
);

SET @sql = IF(
  @col_exists = 0,
  'ALTER TABLE `sms_templates` ADD COLUMN `category` VARCHAR(32) NULL DEFAULT ''SEND_MONEY'' AFTER `template_name`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `sms_templates`
SET `category` = 'SEND_MONEY'
WHERE `category` IS NULL OR `category` = '';

UPDATE `sms_templates`
SET `category` = 'CASH_OUT'
WHERE (`category` IS NULL OR `category` = 'SEND_MONEY')
  AND (`template_name` LIKE '%Agent%' OR `template_name` LIKE '%Cash Out%' OR `template_name` LIKE '%এজেন্ট%');
