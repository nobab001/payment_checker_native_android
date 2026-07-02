-- API Integration v2 — additive, backward-compatible schema changes.
-- Safe to run multiple times (uses IF NOT EXISTS). Applied directly because the
-- XAMPP mysql.* system tables block Prisma schema-engine introspection.

-- ── gateway_layouts: new merchant/website columns ───────────────────────────
ALTER TABLE `gateway_layouts`
  ADD COLUMN IF NOT EXISTS `merchant_id` VARCHAR(64) NULL,
  ADD COLUMN IF NOT EXISTS `company_name` VARCHAR(255) NULL,
  ADD COLUMN IF NOT EXISTS `logo_url` VARCHAR(512) NULL,
  ADD COLUMN IF NOT EXISTS `checkout_theme` VARCHAR(50) NULL DEFAULT 'default',
  ADD COLUMN IF NOT EXISTS `success_url` VARCHAR(512) NULL,
  ADD COLUMN IF NOT EXISTS `cancel_url` VARCHAR(512) NULL,
  ADD COLUMN IF NOT EXISTS `webhook_url` VARCHAR(512) NULL,
  ADD COLUMN IF NOT EXISTS `checkout_mode` VARCHAR(30) NULL DEFAULT 'transaction',
  ADD COLUMN IF NOT EXISTS `api_secret_hash` VARCHAR(128) NULL,
  ADD COLUMN IF NOT EXISTS `api_secret_last4` VARCHAR(8) NULL,
  ADD COLUMN IF NOT EXISTS `api_secret_version` INT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS `number_order_json` LONGTEXT NULL,
  ADD COLUMN IF NOT EXISTS `receive_payment_type` TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `receive_commission` TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `allow_payment_type_callback` TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `allow_commission_callback` TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `commission_enabled` TINYINT NOT NULL DEFAULT 0;

ALTER TABLE `gateway_layouts`
  ADD UNIQUE INDEX IF NOT EXISTS `uniq_merchant_id` (`merchant_id`);

-- ── merchant_commissions ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `merchant_commissions` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `website_id` INT NOT NULL,
  `payment_type` VARCHAR(50) NOT NULL,
  `commission_type` VARCHAR(20) NOT NULL DEFAULT 'percentage',
  `commission_value` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `charge_type` VARCHAR(20) NOT NULL DEFAULT 'flat',
  `charge_value` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_website_payment_type` (`website_id`, `payment_type`),
  KEY `idx_commission_website` (`website_id`),
  CONSTRAINT `fk_commission_website` FOREIGN KEY (`website_id`)
    REFERENCES `gateway_layouts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── checkout_vibe_requests (Merchant Vibe Mode waiting requests) ─────────────
CREATE TABLE IF NOT EXISTS `checkout_vibe_requests` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `website_id` INT NOT NULL,
  `customer_number` VARCHAR(20) NOT NULL,
  `amount` DECIMAL(12,2) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'waiting',
  `matched_trx_id` VARCHAR(191) NULL,
  `matched_history_id` INT NULL,
  `expires_at` DATETIME NOT NULL,
  `matched_at` DATETIME NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vibe_website_status` (`website_id`, `status`),
  KEY `idx_vibe_match` (`customer_number`, `amount`, `status`),
  KEY `idx_vibe_expiry` (`expires_at`),
  CONSTRAINT `fk_vibe_website` FOREIGN KEY (`website_id`)
    REFERENCES `gateway_layouts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
