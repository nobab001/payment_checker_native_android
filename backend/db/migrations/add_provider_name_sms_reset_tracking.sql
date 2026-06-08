-- =============================================================================
-- Migration: Add provider_name to sms_settings table
-- Run once in phpMyAdmin or via CLI
-- =============================================================================

ALTER TABLE `sms_settings`
  ADD COLUMN IF NOT EXISTS `provider_name` VARCHAR(128) DEFAULT NULL
    COMMENT 'Human-readable label e.g. "Green Web SMS", "SSLCOMMERZ SMS"'
  AFTER `sender_id`;

-- =============================================================================
-- Migration: Add daily_reset_at to email_accounts table
-- Useful for automated daily counter reset tracking
-- =============================================================================

ALTER TABLE `email_accounts`
  ADD COLUMN IF NOT EXISTS `last_reset_at` TIMESTAMP NULL DEFAULT NULL
    COMMENT 'Last time sent_today was reset to 0'
  AFTER `sent_today`;
