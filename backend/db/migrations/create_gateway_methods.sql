-- =============================================================================
-- gateway_methods table — গেটওয়ে কাস্টমাইজারের জন্য
-- Database: paychek_online_v2
-- Run: phpMyAdmin-এ SQL ট্যাবে রান করুন
-- =============================================================================

CREATE TABLE IF NOT EXISTS `gateway_methods` (
  `id`           INT          NOT NULL AUTO_INCREMENT,
  `user_id`      INT          NOT NULL,
  `sim_slot`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 = SIM-১, 2 = SIM-২',
  `provider`     VARCHAR(20)  NOT NULL COMMENT 'bKash | Nagad | Rocket | Upay',
  `number`       VARCHAR(20)      NULL COMMENT 'SIM ফোন নম্বর',
  `display_name` VARCHAR(50)      NULL COMMENT 'কাস্টম প্রদর্শন নাম',
  `is_enabled`   TINYINT      NOT NULL DEFAULT 1 COMMENT '0 = বন্ধ, 1 = চালু',
  `priority`     INT          NOT NULL DEFAULT 0 COMMENT 'সাজানোর ক্রম (1 = সবচেয়ে উপরে)',
  `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_sim`      (`user_id`, `sim_slot`),
  KEY `idx_user_priority` (`user_id`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- ডিফল্ট ডেটা — প্রতিটি user-এর জন্য signup-এর পর insert হবে
-- (এটি authController.js-এ completeProfile-এ যোগ করতে হবে)
-- =============================================================================
-- উদাহরণ: user_id = 1 এর জন্য default gateway methods
-- INSERT INTO gateway_methods (user_id, sim_slot, provider, priority) VALUES
--   (1, 1, 'bKash',  1),
--   (1, 1, 'Nagad',  2),
--   (1, 1, 'Rocket', 3),
--   (1, 1, 'Upay',   4),
--   (1, 2, 'bKash',  5),
--   (1, 2, 'Nagad',  6),
--   (1, 2, 'Rocket', 7),
--   (1, 2, 'Upay',   8);
