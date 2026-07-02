-- API Integration v2 — Phase 8: audit trail (additive). Safe to re-run.
CREATE TABLE IF NOT EXISTS `audit_logs` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(60) NOT NULL,
  `entity_type` VARCHAR(40) NULL,
  `entity_id` VARCHAR(80) NULL,
  `website_id` INT NULL,
  `user_id` INT NULL,
  `ip` VARCHAR(64) NULL,
  `status` VARCHAR(20) NULL,
  `detail_json` LONGTEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_event` (`event_type`, `created_at`),
  KEY `idx_audit_website` (`website_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
