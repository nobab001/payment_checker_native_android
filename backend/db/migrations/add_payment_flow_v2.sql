-- API Integration v2 — Phase 6: Payment Flow routing (additive, backward-compatible).
-- Safe to run multiple times (uses IF NOT EXISTS). Applied directly because the
-- XAMPP mysql.* system tables block Prisma schema-engine introspection.

-- ── payment_sessions ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `payment_sessions` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `session_token` VARCHAR(80) NOT NULL,
  `website_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `order_id` VARCHAR(191) NULL,
  `amount` DECIMAL(12,2) NOT NULL,
  `currency` VARCHAR(8) NOT NULL DEFAULT 'BDT',
  `channel` VARCHAR(20) NOT NULL DEFAULT 'paycheck',
  `official_provider` VARCHAR(40) NULL,
  `customer_number` VARCHAR(20) NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'created',
  `trx_id` VARCHAR(191) NULL,
  `matched_history_id` INT NULL,
  `success_url` VARCHAR(512) NULL,
  `cancel_url` VARCHAR(512) NULL,
  `callback_url` VARCHAR(512) NULL,
  `webhook_url` VARCHAR(512) NULL,
  `meta_json` LONGTEXT NULL,
  `expires_at` DATETIME NOT NULL,
  `completed_at` DATETIME NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_session_token` (`session_token`),
  KEY `idx_session_website_status` (`website_id`, `status`),
  KEY `idx_session_expiry` (`status`, `expires_at`),
  KEY `idx_session_order` (`order_id`),
  CONSTRAINT `fk_session_website` FOREIGN KEY (`website_id`)
    REFERENCES `gateway_layouts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── website_official_gateways ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `website_official_gateways` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `website_id` INT NOT NULL,
  `provider` VARCHAR(40) NOT NULL,
  `display_name` VARCHAR(100) NULL,
  `redirect_url_template` VARCHAR(1024) NOT NULL,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `config_json` LONGTEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_website_official_provider` (`website_id`, `provider`),
  KEY `idx_official_website_active` (`website_id`, `is_active`),
  CONSTRAINT `fk_official_website` FOREIGN KEY (`website_id`)
    REFERENCES `gateway_layouts` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
