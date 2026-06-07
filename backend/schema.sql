-- =============================================================================
-- Payment Checker NTK — Complete MySQL Database Schema
-- Version : 2.0.0  (paychek_online_v2)
-- Engine  : InnoDB | Charset: utf8mb4_unicode_ci
-- Usage   : Run this file directly in phpMyAdmin (Import tab)
--           or via CLI: mysql -u root -p < schema.sql
-- =============================================================================

-- Step 1: Create & select the database
CREATE DATABASE IF NOT EXISTS `paychek_online_v2`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `paychek_online_v2`;

-- =============================================================================
-- TABLE 1: users
-- Core user accounts — stores profile, PIN hash, balance, role
-- =============================================================================
CREATE TABLE IF NOT EXISTS `users` (
  `id`               INT           NOT NULL AUTO_INCREMENT,
  `name`             VARCHAR(255)  NOT NULL DEFAULT '',
  `phone`            VARCHAR(20)   DEFAULT NULL COMMENT 'BD format: 01XXXXXXXXX',
  `email`            VARCHAR(255)  DEFAULT NULL,
  `pin`              VARCHAR(255)  NOT NULL DEFAULT '' COMMENT 'bcrypt hashed 6-digit PIN',
  `balance`          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `blocked`          TINYINT(1)    NOT NULL DEFAULT 0,
  `role`             VARCHAR(20)   NOT NULL DEFAULT 'user' COMMENT 'user | admin',
  `profile_complete` TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0=new signup pending, 1=active',
  `sms_enabled`      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT 'Admin toggle for SMS tracking',
  `gmail_enabled`    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT 'Admin toggle for Gmail tracking',
  `created_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_phone` (`phone`),
  UNIQUE KEY `uniq_email` (`email`),
  INDEX `idx_role` (`role`),
  INDEX `idx_blocked` (`blocked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 2: otps
-- One-Time Password storage for login and PIN recovery
-- =============================================================================
CREATE TABLE IF NOT EXISTS `otps` (
  `id`         INT          NOT NULL AUTO_INCREMENT,
  `contact`    VARCHAR(255) NOT NULL COMMENT 'Phone number or Email address',
  `code`       VARCHAR(10)  NOT NULL,
  `expires_at` DATETIME     NOT NULL,
  `used_at`    DATETIME     DEFAULT NULL,
  `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_contact` (`contact`),
  INDEX `idx_code_contact` (`code`, `contact`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 3: user_credentials
-- Multi-credential system — links multiple phones/emails to one user account
-- =============================================================================
CREATE TABLE IF NOT EXISTS `user_credentials` (
  `id`          INT          NOT NULL AUTO_INCREMENT,
  `user_id`     INT          NOT NULL,
  `type`        ENUM('phone','email') NOT NULL,
  `value`       VARCHAR(255) NOT NULL,
  `verified_at` DATETIME     DEFAULT NULL,
  `created_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_cred_value` (`value`),
  INDEX `idx_cred_user_type` (`user_id`, `type`),
  CONSTRAINT `fk_cred_user`
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 4: registered_devices
-- Device tracking, parent/child roles, SIM settings, and TRIAL LOCK system
-- =============================================================================
CREATE TABLE IF NOT EXISTS `registered_devices` (
  `id`                   INT           NOT NULL AUTO_INCREMENT,
  `user_id`              INT           NOT NULL,
  `device_id`            VARCHAR(255)  NOT NULL COMMENT 'Android Hardware ID (android_id)',
  `device_name`          VARCHAR(255)  NOT NULL DEFAULT 'My Phone',
  `custom_name`          VARCHAR(255)  DEFAULT NULL COMMENT 'User-renamed label',
  `device_model`         VARCHAR(255)  NOT NULL DEFAULT '',
  `android_version`      VARCHAR(64)   NOT NULL DEFAULT '',

  -- Status & Role
  `status`               ENUM('pending','active','rejected') NOT NULL DEFAULT 'pending',
  `is_parent`            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1=Parent device, 0=Child device',

  -- SIM Configuration
  `sim1_number`          VARCHAR(32)   DEFAULT NULL,
  `sim1_operator`        VARCHAR(64)   DEFAULT NULL,
  `sim2_number`          VARCHAR(32)   DEFAULT NULL,
  `sim2_operator`        VARCHAR(64)   DEFAULT NULL,
  `sim_settings`         JSON          DEFAULT NULL COMMENT 'Full slot filter JSON from app',

  -- Heartbeat Tracking
  `last_seen_at`         TIMESTAMP     NULL DEFAULT NULL,
  `last_battery_percent` TINYINT UNSIGNED DEFAULT NULL,

  -- -----------------------------------------------------------------------
  -- TRIAL LOCK SYSTEM
  -- trial_started_at : প্রথম activation-এর সময় সেট হয়
  -- trial_expires_at : trial_started_at + N days (server-side configurable)
  -- is_trial_locked  : trial শেষ হলে 1 হয়, অ্যাপ ব্লক হয়
  -- lock_reason      : কেন locked — 'trial_expired' | 'manual_block' | 'payment_required'
  -- -----------------------------------------------------------------------
  `trial_started_at`     TIMESTAMP     NULL DEFAULT NULL,
  `trial_expires_at`     TIMESTAMP     NULL DEFAULT NULL,
  `is_trial_locked`      TINYINT(1)    NOT NULL DEFAULT 0,
  `lock_reason`          VARCHAR(64)   DEFAULT NULL COMMENT 'trial_expired | manual_block | payment_required',

  `created_at`           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_device` (`user_id`, `device_id`),
  INDEX `idx_device_id` (`device_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_is_parent` (`is_parent`),
  INDEX `idx_trial_lock` (`is_trial_locked`, `trial_expires_at`),
  CONSTRAINT `fk_devices_user`
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 5: sms_history
-- Parsed payment transactions — the core data table
-- Includes Unique TrxID deduplication and sold-out tracking
-- =============================================================================
CREATE TABLE IF NOT EXISTS `sms_history` (
  `id`              INT           NOT NULL AUTO_INCREMENT,
  `user_id`         INT           NOT NULL,
  `device_id`       VARCHAR(255)  NOT NULL DEFAULT '',
  `sim_slot`        TINYINT       DEFAULT NULL COMMENT '1 or 2',
  `sim_number`      VARCHAR(32)   DEFAULT NULL,

  -- Payment Data
  `provider_tag`    VARCHAR(128)  NOT NULL DEFAULT '' COMMENT 'bKash | Nagad | Rocket | Upay',
  `amount`          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `trx_id`          VARCHAR(64)   NOT NULL DEFAULT '' COMMENT 'Unique Transaction ID from SMS',
  `sender_number`   VARCHAR(32)   DEFAULT NULL COMMENT 'Sender mobile number or address',
  `receiver_number` VARCHAR(32)   DEFAULT NULL,

  -- SMS Metadata
  `sms_timestamp`   DATETIME      NOT NULL COMMENT 'Timestamp parsed from SMS body',
  `sms_date`        DATE          DEFAULT NULL,
  `full_sms`        TEXT          DEFAULT NULL COMMENT 'Original SMS text',

  -- -----------------------------------------------------------------------
  -- DEDUPLICATION KEY
  -- Formula: sms_timestamp + '|' + sender_number + '|' + SHA2(full_sms, 256)
  -- UNIQUE constraint prevents same SMS being inserted twice
  -- App uses ConflictAlgorithm.IGNORE equivalent on INSERT
  -- -----------------------------------------------------------------------
  `dedupe_key`      VARCHAR(512)  NOT NULL DEFAULT '' COMMENT 'Deduplication composite key',

  -- Sync & Usage Status
  `is_synced`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0=pending upload, 1=sent to server',
  `is_used`         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0=unchecked, 1=marked SOLDOUT',
  `used_at`         TIMESTAMP     NULL DEFAULT NULL,
  `used_by_merchant_id` INT       DEFAULT NULL,

  `created_at`      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),

  -- Unique TrxID per user (prevents duplicate transactions from same user)
  UNIQUE KEY `uniq_dedupe` (`user_id`, `dedupe_key`(255)),

  -- Fast lookup indexes (blueprint: idx_parsed_user_time, idx_parsed_trx, idx_parsed_verify_lookup)
  INDEX `idx_user_time`         (`user_id`, `sms_timestamp`),
  INDEX `idx_user_trx`          (`user_id`, `trx_id`),
  INDEX `idx_provider`          (`user_id`, `provider_tag`),
  INDEX `idx_verify_lookup`     (`user_id`, `trx_id`, `amount`, `is_used`),
  INDEX `idx_device_sync`       (`device_id`, `is_synced`),

  CONSTRAINT `fk_sms_history_user`
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 6: sms_templates
-- Admin-defined SMS parsing rules (regex template engine)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `sms_templates` (
  `id`               INT          NOT NULL AUTO_INCREMENT,
  `user_id`          INT          DEFAULT NULL COMMENT 'NULL for admin/global, user_id for custom templates',
  `customer_preview` VARCHAR(128) NOT NULL COMMENT 'Display name e.g. bKash Personal',
  `sender_id`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'Sender address to match e.g. bKash',
  `formats`          JSON         NOT NULL COMMENT 'Array of format strings with [Amount],[TrxID] tokens',
  `is_active`        TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_template_active` (`customer_preview`, `is_active`),
  INDEX `idx_template_user` (`user_id`),
  CONSTRAINT `fk_template_user`
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 7: gateway_layouts
-- B2B Merchant API integration — checkout site configs and API keys
-- =============================================================================
CREATE TABLE IF NOT EXISTS `gateway_layouts` (
  `id`             INT          NOT NULL AUTO_INCREMENT,
  `user_id`        INT          NOT NULL,

  -- Site Identity
  `site_name`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Merchant site label',
  `site_url`       VARCHAR(512) DEFAULT NULL COMMENT 'Target checkout website URL',

  -- API Credentials
  `api_key`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Public API key for verification requests',
  `api_secret`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SHA256 hashed secret key',

  -- Dynamic Checkout Layout
  `layout_config`  JSON         DEFAULT NULL COMMENT 'Checkout block definitions (blueprint: checkout_layout.dart)',
  `redirect_url`   VARCHAR(512) DEFAULT NULL COMMENT 'Success redirect target URL',
  `callback_url`   VARCHAR(512) DEFAULT NULL COMMENT 'Webhook POST endpoint for payment events',

  -- Status
  `is_active`      TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_api_key` (`api_key`),
  INDEX `idx_gateway_user` (`user_id`),
  INDEX `idx_gateway_active` (`user_id`, `is_active`),
  CONSTRAINT `fk_gateway_user`
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- TABLE 8: global_config
-- Admin-controlled feature flags (maintenance mode, registration toggle, etc.)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `global_config` (
  `config_key`   VARCHAR(128) NOT NULL,
  `config_value` TEXT         NOT NULL,
  `updated_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Default config values
INSERT INTO `global_config` (`config_key`, `config_value`) VALUES
  ('maintenance_mode',      'false'),
  ('registration_enabled',  'true'),
  ('sms_tracking_enabled',  'true'),
  ('gmail_tracking_enabled','false'),
  ('trial_days',            '7'),
  ('telegram_support_link', ''),
  ('whatsapp_support_link', '')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- =============================================================================
-- TABLE 9: sms_settings
-- SMS gateway provider configurations (admin-managed)
-- =============================================================================
CREATE TABLE IF NOT EXISTS `sms_settings` (
  `id`                 INT          NOT NULL AUTO_INCREMENT,
  `gateway_url`        VARCHAR(512) NOT NULL COMMENT 'Gateway base URL with placeholders',
  `http_method`        VARCHAR(10)  NOT NULL DEFAULT 'GET',
  `post_body_template` TEXT         DEFAULT NULL,
  `api_key`            VARCHAR(255) DEFAULT NULL,
  `username`           VARCHAR(255) DEFAULT NULL,
  `sender_id`          VARCHAR(64)  DEFAULT NULL,
  `is_active`          TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- END OF SCHEMA
-- Tables created:
--   1. users               — User profiles
--   2. otps                — OTP verification codes
--   3. user_credentials    — Multi phone/email per account
--   4. registered_devices  — Device tracking + Trial Lock
--   5. sms_history         — Parsed payment records (Unique TrxID)
--   6. sms_templates       — Admin regex parsing rules
--   7. gateway_layouts     — Merchant checkout API sites
--   8. global_config       — App-wide feature flags
--   9. sms_settings        — SMS gateway provider config
-- =============================================================================
