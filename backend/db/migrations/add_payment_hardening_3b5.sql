-- Phase-3B.5 Production Hardening
-- Run via: mysql ... < add_payment_hardening_3b5.sql
-- Or: npx prisma db push

-- Block duplicate provider trx_id per website (NULL trx_id rows are allowed multiple times in MySQL)
ALTER TABLE `payment_sessions`
  ADD UNIQUE KEY `uniq_website_trx` (`website_id`, `trx_id`);

CREATE TABLE IF NOT EXISTS `merchant_callback_outbox` (
  `id`            INT NOT NULL AUTO_INCREMENT,
  `delivery_key`  VARCHAR(191) NOT NULL,
  `session_token` VARCHAR(80) NOT NULL,
  `website_id`    INT NOT NULL,
  `target_url`    VARCHAR(512) NOT NULL,
  `payload_json`  LONGTEXT NOT NULL,
  `trace_id`      VARCHAR(64) DEFAULT NULL,
  `status`        VARCHAR(20) NOT NULL DEFAULT 'pending',
  `attempts`      INT NOT NULL DEFAULT 0,
  `last_error`    VARCHAR(512) DEFAULT NULL,
  `locked_at`     DATETIME DEFAULT NULL,
  `locked_by`     VARCHAR(64) DEFAULT NULL,
  `sent_at`       DATETIME DEFAULT NULL,
  `created_at`    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_mcb_delivery_key` (`delivery_key`),
  KEY `idx_mcb_status_created` (`status`, `created_at`),
  KEY `idx_mcb_session` (`session_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
