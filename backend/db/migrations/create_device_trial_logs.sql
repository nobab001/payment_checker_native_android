-- Migration: Create device_trial_logs table for trial abuse prevention
-- Target: MySQL (InnoDB)

CREATE TABLE IF NOT EXISTS `device_trial_logs` (
  `id`                   INT           NOT NULL AUTO_INCREMENT,
  `android_id`           VARCHAR(255)  NOT NULL,
  `hardware_fingerprint` VARCHAR(255)  NOT NULL,
  `sim_slot_ids`         VARCHAR(255)  NOT NULL,
  `has_used_trial`       TINYINT(1)    NOT NULL DEFAULT 1,
  `created_at`           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_device_binding` (`android_id`, `hardware_fingerprint`, `sim_slot_ids`),
  INDEX `idx_android_id` (`android_id`),
  INDEX `idx_fingerprint` (`hardware_fingerprint`),
  INDEX `idx_sim_slots` (`sim_slot_ids`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
