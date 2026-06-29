-- Per-device SIM slot registry: tracks which phone number is bound/active on each slot.
CREATE TABLE IF NOT EXISTS `sim_slot_bindings` (
  `id`            INT          NOT NULL AUTO_INCREMENT,
  `user_id`       VARCHAR(255) NOT NULL,
  `device_id`     VARCHAR(255) NOT NULL DEFAULT '',
  `sim_slot`      TINYINT      NOT NULL DEFAULT 1,
  `phone_number`  VARCHAR(20)  NOT NULL DEFAULT '',
  `is_active`     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = SIM monitor toggle ON for this slot',
  `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_device_slot` (`user_id`, `device_id`, `sim_slot`),
  INDEX `idx_user_number_active` (`user_id`, `phone_number`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
