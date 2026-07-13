-- Live merchant accounts (multi-account credentials per website / provider)
-- Encrypted secrets: api_secret_enc, password_enc, app_secret_enc (AES via merchantCrypto.js)

CREATE TABLE IF NOT EXISTS `merchant_accounts` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `website_id` INT NOT NULL,
  `provider` VARCHAR(40) NOT NULL,
  `merchant_name` VARCHAR(120) NOT NULL,
  `merchant_ref` VARCHAR(120) NULL,
  `logo_url` VARCHAR(512) NULL,
  `api_key` VARCHAR(512) NULL,
  `api_secret_enc` TEXT NULL,
  `username` VARCHAR(255) NULL,
  `password_enc` TEXT NULL,
  `app_key` VARCHAR(512) NULL,
  `app_secret_enc` TEXT NULL,
  `base_url` VARCHAR(512) NULL,
  `callback_url` VARCHAR(512) NULL,
  `is_active` TINYINT NOT NULL DEFAULT 1,
  `is_default` TINYINT NOT NULL DEFAULT 0,
  `priority` INT NOT NULL DEFAULT 0,
  `notes` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_merchant_acct_website_active` (`website_id`, `is_active`),
  INDEX `idx_merchant_acct_provider` (`website_id`, `provider`, `is_active`),
  CONSTRAINT `fk_merchant_account_website`
    FOREIGN KEY (`website_id`) REFERENCES `gateway_layouts` (`id`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
