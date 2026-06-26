SET FOREIGN_KEY_CHECKS=0;
-- MariaDB dump 10.19  Distrib 10.11.8-MariaDB, for Win64 (AMD64)
--
-- Host: 127.0.0.1    Database: paychek_online_v2
-- ------------------------------------------------------
-- Server version	10.11.8-MariaDB

;
;
;
;
;
;
;
;
;
;

--
-- Table structure for table `checkout_view_templates`
--

DROP TABLE IF EXISTS `checkout_view_templates`;
;
;
CREATE TABLE `checkout_view_templates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sms_template_id` int(11) NOT NULL,
  `single_number_instruction` text NOT NULL,
  `multiple_number_instruction` text NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_sms_template` (`sms_template_id`),
  CONSTRAINT `fk_checkout_view_sms_template` FOREIGN KEY (`sms_template_id`) REFERENCES `sms_templates` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=553 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `checkout_view_templates`
--

LOCK TABLES `checkout_view_templates` WRITE;
;
INSERT INTO `checkout_view_templates` VALUES
(527,11,'single','multi','2026-06-23 21:36:49','2026-06-23 21:36:49'),
(528,12,'single','multi','2026-06-23 21:37:00','2026-06-23 21:37:00'),
(529,13,'single','multi','2026-06-23 21:37:08','2026-06-23 21:37:08'),
(538,14,'single a','multi a','2026-06-23 23:06:20','2026-06-23 23:06:20'),
(539,15,'single a','multi a','2026-06-23 23:06:28','2026-06-23 23:06:28'),
(552,1,'aª¿aª+aªÜaºçaª¦ aª¼aª+aªòaª+aª¦ aª¬aª+aª¦aºìaª+aºïaª¿aª+aª¦ aª¿aª«aºìaª¼aª¦aºç Send Money aªòaª¦aºüaª¿ aªÅaª¼aªé aªƒaºìaª¦aª+aª¿aª£aºçaªòaª¦aª¿ aªåaªçaªíaª+ aª¿aª+aªÜaºç aªªaª+aºƒaºç aª+aª+aª¼aª«aª+aªƒ aªòaª¦aºüaª¿aÑñ','aª¿aª+aªÜaºçaª¦ aª»aºçaªòaºïaª¿aºï aªÅaªòaªƒaª+ aª+aªòaºìaª¦aª+aºƒ aª¼aª+aªòaª+aª¦ aª¿aª«aºìaª¼aª¦aºç Send Money aªòaª¦aºüaª¿ aªÅaª¼aªé aªƒaºìaª¦aª+aª¿aª£aºçaªòaª¦aª¿ aªåaªçaªíaª+ aª+aª+aª¼aª«aª+aªƒ aªòaª¦aºüaª¿aÑñ','2026-06-24 05:22:00','2026-06-24 05:22:00');
;
UNLOCK TABLES;

--
-- Table structure for table `device_trial_logs`
--

DROP TABLE IF EXISTS `device_trial_logs`;
;
;
CREATE TABLE `device_trial_logs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL,
  `android_id` varchar(255) NOT NULL,
  `hardware_fingerprint` varchar(255) NOT NULL,
  `sim_slot_ids` varchar(255) NOT NULL,
  `has_used_trial` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_device_binding` (`android_id`,`hardware_fingerprint`,`sim_slot_ids`),
  KEY `idx_android_id` (`android_id`),
  KEY `idx_fingerprint` (`hardware_fingerprint`),
  KEY `idx_sim_slots` (`sim_slot_ids`),
  KEY `idx_dtl_android` (`android_id`(64))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `device_trial_logs`
--

LOCK TABLES `device_trial_logs` WRITE;
;
INSERT INTO `device_trial_logs` VALUES
(4,4,'3fe864e3b1b1028a','OnePlus/MT2111_IND/OP5155L1:14/UKQ1.230924.001/R.2202aff-2-fc45e:user/release-keys','no_sims',1,'2026-06-23 17:56:23');
;
UNLOCK TABLES;

--
-- Table structure for table `gateway_layouts`
--

DROP TABLE IF EXISTS `gateway_layouts`;
;
;
CREATE TABLE `gateway_layouts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `site_name` varchar(255) NOT NULL DEFAULT '',
  `site_url` varchar(512) DEFAULT NULL,
  `api_key` varchar(255) NOT NULL DEFAULT '',
  `api_secret` varchar(255) NOT NULL DEFAULT '',
  `layout_config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`layout_config`)),
  `redirect_url` varchar(512) DEFAULT NULL,
  `callback_url` varchar(512) DEFAULT NULL,
  `is_active` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_api_key` (`api_key`),
  KEY `idx_gateway_user` (`user_id`),
  KEY `idx_gateway_active` (`user_id`,`is_active`),
  CONSTRAINT `fk_gateway_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `gateway_layouts`
--

LOCK TABLES `gateway_layouts` WRITE;
;
INSERT INTO `gateway_layouts` VALUES
(4,12,'Test Store 2','https://teststore2.com','pk_18183b646e6ac8040013a2536779ef6b','sk_1204a2788cc76a07339cda45f10e80f867f1739b25eb6378','{}',NULL,NULL,1,'2026-06-23 12:51:33','2026-06-23 12:51:33');
;
UNLOCK TABLES;

--
-- Table structure for table `gateway_methods`
--

DROP TABLE IF EXISTS `gateway_methods`;
;
;
CREATE TABLE `gateway_methods` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(255) NOT NULL,
  `sim_slot` int(11) NOT NULL DEFAULT 1,
  `provider` varchar(50) NOT NULL,
  `number` varchar(20) NOT NULL,
  `display_name` varchar(100) DEFAULT NULL,
  `is_enabled` tinyint(4) NOT NULL DEFAULT 0,
  `priority` int(11) NOT NULL DEFAULT 0,
  `template_id` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `device_id` varchar(255) NOT NULL DEFAULT '',
  `custom_patterns` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_template` (`template_id`),
  KEY `idx_user_device` (`user_id`,`device_id`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
;

--
-- Dumping data for table `gateway_methods`
--

LOCK TABLES `gateway_methods` WRITE;
;
INSERT INTO `gateway_methods` VALUES
(35,'4',2,'bKash Personal','01848790038',NULL,1,1,1,'2026-06-23 21:41:38','2026-06-23 21:41:38','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(36,'4',2,'Nagad personal','01848790038',NULL,1,2,11,'2026-06-23 21:41:40','2026-06-23 21:41:40','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(37,'4',2,'Upay personal','01848790038',NULL,1,3,12,'2026-06-23 21:41:44','2026-06-23 21:41:44','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(38,'4',2,'Rocket personal','01848790038',NULL,1,4,13,'2026-06-23 21:41:45','2026-06-23 21:41:45','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(39,'4',1,'bKash Personal','01894086541',NULL,1,5,1,'2026-06-23 21:42:06','2026-06-24 05:10:24','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(40,'4',1,'Upay personal','01894086541',NULL,1,6,12,'2026-06-23 23:10:24','2026-06-23 23:10:24','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(41,'4',1,'Rocket personal','01894086541',NULL,1,7,13,'2026-06-23 23:10:25','2026-06-23 23:10:25','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(42,'4',1,'Nagad personal','01894086541',NULL,1,8,11,'2026-06-23 23:10:25','2026-06-23 23:10:25','548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL),
(43,'4',1,'bkash agent','01614677619',NULL,1,1,14,'2026-06-23 23:15:02','2026-06-23 23:15:02','de3b2a6c735cb5a006bd21122b297a821391a448758bc68974fa9dbe9f62df8f',NULL),
(44,'4',1,'Nagad agent','01614677619',NULL,1,2,15,'2026-06-23 23:15:03','2026-06-23 23:15:03','de3b2a6c735cb5a006bd21122b297a821391a448758bc68974fa9dbe9f62df8f',NULL),
(45,'4',2,'Rocket personal','01613988902',NULL,1,3,13,'2026-06-23 23:15:07','2026-06-23 23:15:07','de3b2a6c735cb5a006bd21122b297a821391a448758bc68974fa9dbe9f62df8f',NULL);
;
UNLOCK TABLES;

--
-- Table structure for table `global_config`
--

DROP TABLE IF EXISTS `global_config`;
;
;
CREATE TABLE `global_config` (
  `config_key` varchar(128) NOT NULL,
  `config_value` text NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `global_config`
--

LOCK TABLES `global_config` WRITE;
;
INSERT INTO `global_config` VALUES
('facebook_support_link','https://www.facebook.com/share/1CbbezKhGj/','2026-06-19 17:32:50'),
('gmail_tracking_enabled','false','2026-06-19 10:28:38'),
('maintenance_mode','false','2026-06-19 10:28:38'),
('registration_enabled','true','2026-06-19 10:28:38'),
('sms_tracking_enabled','true','2026-06-19 10:28:38'),
('telegram_support_link','https://t.me/dubai_telecom_admin','2026-06-19 17:32:50'),
('whatsapp_support_link','https://wa.me/8801894086541','2026-06-19 17:32:50'),
('youtube_support_link','https://youtube.com','2026-06-19 17:32:50');
;
UNLOCK TABLES;

--
-- Table structure for table `otp_sms_templates`
--

DROP TABLE IF EXISTS `otp_sms_templates`;
;
;
CREATE TABLE `otp_sms_templates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(128) NOT NULL,
  `setting_value` text DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_setting_key` (`setting_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `otp_sms_templates`
--

LOCK TABLES `otp_sms_templates` WRITE;
;
INSERT INTO `otp_sms_templates` VALUES
(1,'otp_format_template','aªåaª¬aª¿aª+aª¦ aª¬aºìaª¦aª+aª»aª+ aª¬aºç-aªÜaºçaªò aªàaºìaª»aª+aª¬ aª¡aºçaª¦aª+aª½aª+aªòaºçaª¦aª¿ aªòaºïaªí aª¦aª¦aºï: {otp}aÑñ aªòaºïaªíaªƒaª+ aªùaºïaª¬aª¿ aª¦aª+aªûaºüaª¿aÑñ','2026-06-19 10:28:38');
;
UNLOCK TABLES;

--
-- Table structure for table `otps`
--

DROP TABLE IF EXISTS `otps`;
;
;
CREATE TABLE `otps` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `contact` varchar(255) NOT NULL,
  `code` varchar(255) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_contact` (`contact`),
  KEY `idx_code_contact` (`code`,`contact`),
  KEY `idx_contact_code` (`contact`,`code`)
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `otps`
--

LOCK TABLES `otps` WRITE;
;
INSERT INTO `otps` VALUES
(38,'nobab.yousuf.hazi.99@gmail.com','709478','2026-06-24 00:00:49','2026-06-23 23:56:06','2026-06-23 17:55:49'),
(39,'01711223344','85f44562ee4a5071b51dbc9d772b22ea:f8e9546b8522503ca8e9c44d01005208','2026-06-24 00:24:00',NULL,'2026-06-23 18:19:00'),
(40,'01711223344','d36ed92147ca8b9bd4922da556c24345:6d526680fc63dd7c35462515773d1a44','2026-06-24 00:24:29','2026-06-24 00:19:30','2026-06-23 18:19:29'),
(41,'01711223344','46761492c0eb01460ea82495239c83ea:710a08044b04427facc60bb8dd4bc3e5','2026-06-23 18:24:30',NULL,'2026-06-23 18:19:30'),
(42,'01711223344','ead031072697e276b92d7feff486315b:aabf1aeeebe92cf3174bf9098ec9964d','2026-06-24 00:26:37','2026-06-24 00:21:38','2026-06-23 18:21:37'),
(43,'01711223344','ac96080d95f3993d4531e133e27db4f9:888e3fb3a6883875b94a0e68d28c1642','2026-06-24 00:26:38','2026-06-24 00:21:38','2026-06-23 18:21:38'),
(44,'01711223344','7139e0a152c8ea79af314b5bc4c1ef95:eb80740366c01945b53451b0680b3b48','2026-06-24 00:29:57','2026-06-24 00:24:57','2026-06-23 18:24:57'),
(45,'01711223344','ea0e8c416fbad224bd4271ae1920d6c4:ebabd7b818c11673eb60cc3c1ff7b07c','2026-06-24 00:29:57','2026-06-24 00:24:57','2026-06-23 18:24:57'),
(46,'01711223344','c15162112d0b682baf5ec6a4fe5bc269:a394d961435cb812dd457a6eee02bed9','2026-06-24 00:30:14','2026-06-24 00:25:15','2026-06-23 18:25:14'),
(47,'01711223344','a54340392d2882bb24560b803f4c2ddf:3cb93a34879bbe6594327ab220219c8d','2026-06-24 00:30:15','2026-06-24 00:25:15','2026-06-23 18:25:15'),
(48,'01711223344','68f8ad32773bdf28ab9f059ac0c97a22:6880340d96523bb0e0109faed5195149','2026-06-24 00:30:15','2026-06-24 00:25:15','2026-06-23 18:25:15'),
(49,'01711223344','82267c684a419de2c11a366d71be8fef:33abc26cec3f3636df35609e410dd321','2026-06-24 00:30:42','2026-06-24 00:25:43','2026-06-23 18:25:42'),
(50,'01711223344','84dd217c6f41c271f8ec1b442ffb181d:9a24f6d972710f8dfe32a0d510048deb','2026-06-24 00:30:43','2026-06-24 00:25:43','2026-06-23 18:25:43'),
(51,'01711223344','28679741f2aa667de87a0503f0642c13:09c860e15c2f54e5271e06033d319b5c','2026-06-24 00:30:43','2026-06-24 00:25:43','2026-06-23 18:25:43'),
(52,'01711223344','23d0b3449d34b83df80ac2fc8ffe85c5:c778aa6647a108a2ad48415043d03bcb','2026-06-24 00:31:47','2026-06-24 00:26:47','2026-06-23 18:26:47'),
(53,'01711223344','8eaab1c02d05dc32836e2023b4fbb85a:129213eaabb0c29f388c131e898e3fc5','2026-06-24 00:31:47','2026-06-24 00:26:47','2026-06-23 18:26:47'),
(54,'01711223344','f882133a203e474b1cbc5c7730cbdfea:45c72f2b1b4780ec34b72f85e9585edb','2026-06-24 00:31:48','2026-06-24 00:26:48','2026-06-23 18:26:48'),
(55,'01711223344','6b79825eaf2ee076455c61a589726099:8d27589c63b3eed5590e987c4e64e051','2026-06-24 00:32:54','2026-06-24 00:27:55','2026-06-23 18:27:54'),
(56,'01711223344','940721582759dfd7390107a1ca3b226e:d7329f36fd0f8f7aac737a9bec87aa14','2026-06-24 00:32:55','2026-06-24 00:27:55','2026-06-23 18:27:55'),
(57,'01711223344','9fd9d98c27da2e729eaf1ac13d54b180:33ab00a6073f9bfd0e2c1d9693a72544','2026-06-24 00:32:55','2026-06-24 00:27:55','2026-06-23 18:27:55'),
(58,'01711223344','925e7c31b6a5881f3a5aa483929ea862:fb604ec408bbdc8a8f5f9549c88ab852','2026-06-24 00:56:33','2026-06-24 00:51:33','2026-06-23 18:51:33'),
(59,'01711223344','fdeec8f8e3a80254a52f99566b8af2cf:245af5c76bf030fb7f8a3ada42426c2b','2026-06-24 00:56:33','2026-06-24 00:51:33','2026-06-23 18:51:33'),
(60,'01711223344','211b5b545b7773cb2716d8caaafefe75:d516894f9c69a5cb48280ce66ab99c3a','2026-06-24 00:56:33','2026-06-24 00:51:33','2026-06-23 18:51:33'),
(61,'nobab.yousuf.hazi.99@gmail.com','a042e3475e3806eaddfe5d8eaa5c0c77:a6ee9fed4bb98ff07593bd71c493a05b','2026-06-24 09:45:36','2026-06-24 09:40:44','2026-06-24 03:40:36'),
(62,'nobab.yousuf.hazi.99@gmail.com','717ce323652d86966980f7c3486baa2c:145159d970cd2b19eae83190e0a35026','2026-06-24 11:14:44','2026-06-24 11:09:55','2026-06-24 05:09:44'),
(63,'nobab.yousuf.hazi.99@gmail.com','8342e4d11e485e80f7b9a87efd083877:80207364ed69e0546f1d936065c40425','2026-06-24 11:18:53','2026-06-24 11:14:04','2026-06-24 05:13:53'),
(64,'bangladeshy7@gmail.com','2de0848d7bb12da0552ae73df923d624:af3203fb4e5b42d3de7befcf2b1557c7','2026-06-24 05:21:38','2026-06-24 05:16:50','2026-06-23 23:16:38'),
(65,'01614677619','dfdd9ddcd51f2560d88476d777b1c978:3ceea9ecb142eb35e84ecd5cd9e1a0ef','2026-06-24 05:22:01','2026-06-24 05:17:06','2026-06-23 23:17:01'),
(66,'nobab.yousuf.hazi.99@gmail.com','3b09f32c47b28b368877d348085dde5e:2b47dfcee322fc0e28ce2f9626795cd1','2026-06-24 13:07:05','2026-06-24 13:02:18','2026-06-24 07:02:05'),
(67,'nobab.yousuf.hazi.99@gmail.com','f64a13a3d4342802db9c29c7eaa72644:360126ecbfdb12e15a90702ed77c43a3','2026-06-24 15:37:36','2026-06-24 15:32:50','2026-06-24 09:32:36');
;
UNLOCK TABLES;

--
-- Table structure for table `registered_devices`
--

DROP TABLE IF EXISTS `registered_devices`;
;
;
CREATE TABLE `registered_devices` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL,
  `android_id` varchar(255) DEFAULT NULL,
  `hardware_fingerprint` varchar(512) DEFAULT NULL,
  `sim_slot_ids` text DEFAULT NULL,
  `device_name` varchar(255) NOT NULL DEFAULT 'My Phone',
  `custom_name` varchar(255) DEFAULT NULL,
  `device_model` varchar(255) NOT NULL DEFAULT '',
  `android_version` varchar(64) NOT NULL DEFAULT '',
  `status` enum('pending','active','rejected') NOT NULL DEFAULT 'pending',
  `is_parent` tinyint(4) NOT NULL DEFAULT 0,
  `is_approved` tinyint(4) NOT NULL DEFAULT 0,
  `device_role` varchar(20) NOT NULL DEFAULT 'pending',
  `sim1_number` varchar(32) DEFAULT NULL,
  `sim1_operator` varchar(64) DEFAULT NULL,
  `sim2_number` varchar(32) DEFAULT NULL,
  `sim2_operator` varchar(64) DEFAULT NULL,
  `sim_settings` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`sim_settings`)),
  `last_seen_at` timestamp NULL DEFAULT NULL,
  `last_battery_percent` tinyint(3) unsigned DEFAULT NULL,
  `trial_started_at` timestamp NULL DEFAULT NULL,
  `trial_expires_at` timestamp NULL DEFAULT NULL,
  `is_trial_locked` tinyint(4) NOT NULL DEFAULT 0,
  `lock_reason` varchar(64) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `custom_device_name` varchar(100) NOT NULL DEFAULT '',
  `sim_one_number` varchar(15) DEFAULT NULL,
  `sim_one_active` tinyint(4) DEFAULT 1,
  `sim_two_number` varchar(15) DEFAULT NULL,
  `sim_two_active` tinyint(4) DEFAULT 1,
  `is_app_active` tinyint(4) DEFAULT 1,
  `is_owner_device` tinyint(4) DEFAULT 0,
  `device_specific_pin` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_device` (`user_id`,`device_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_parent` (`is_parent`),
  KEY `idx_trial_lock` (`is_trial_locked`,`trial_expires_at`),
  CONSTRAINT `fk_devices_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `registered_devices`
--

LOCK TABLES `registered_devices` WRITE;
;
INSERT INTO `registered_devices` VALUES
(5,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',NULL,NULL,NULL,'Main Phone',NULL,'OnePlus MT2111','14','active',1,1,'owner',NULL,NULL,NULL,NULL,NULL,'2026-06-24 09:32:50',NULL,'2026-06-23 11:56:23','2026-06-30 11:56:23',0,NULL,'2026-06-23 17:56:23','2026-06-24 09:32:50','',NULL,1,NULL,1,1,1,NULL),
(15,12,'device_test_9991',NULL,NULL,NULL,'Main Phone',NULL,'Test Device 1','13','active',1,1,'owner',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-23 18:51:33','2026-06-30 18:51:33',0,NULL,'2026-06-23 18:51:33','2026-06-23 18:51:33','',NULL,1,NULL,1,1,1,NULL),
(16,12,'device_test_9992',NULL,NULL,NULL,'Co-Parent Device',NULL,'Test Device 2','13','pending',0,0,'pending',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-23 12:51:33','2026-06-30 12:51:33',0,NULL,'2026-06-23 18:51:33','2026-06-23 18:51:33','',NULL,1,NULL,1,1,0,NULL),
(17,4,'de3b2a6c735cb5a006bd21122b297a821391a448758bc68974fa9dbe9f62df8f',NULL,NULL,NULL,'Co-Parent Device',NULL,'vivo V2543','16','active',0,1,'owner',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-06-23 23:14:04','2026-06-30 23:14:04',0,NULL,'2026-06-24 05:14:04','2026-06-24 05:14:27','',NULL,1,NULL,1,1,0,NULL);
;
UNLOCK TABLES;

--
-- Table structure for table `sms_gateways`
--

DROP TABLE IF EXISTS `sms_gateways`;
;
;
CREATE TABLE `sms_gateways` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provider_name` varchar(128) DEFAULT NULL,
  `gateway_url` varchar(512) NOT NULL,
  `http_method` varchar(10) NOT NULL DEFAULT 'GET',
  `post_body_template` text DEFAULT NULL,
  `api_key` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `sender_id` varchar(64) DEFAULT NULL,
  `is_active` tinyint(4) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `sms_gateways`
--

LOCK TABLES `sms_gateways` WRITE;
;
INSERT INTO `sms_gateways` VALUES
(1,NULL,'http://bulksmsbd.net/api/smsapi','GET',NULL,'hknDpPg0AazTNirpWyao','nobab002','8809617626944',1,'2026-06-19 10:50:28','2026-06-19 10:50:28');
;
UNLOCK TABLES;

--
-- Table structure for table `sms_history`
--

DROP TABLE IF EXISTS `sms_history`;
;
;
CREATE TABLE `sms_history` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL DEFAULT '',
  `sim_slot` tinyint(4) DEFAULT NULL,
  `sim_number` varchar(32) DEFAULT NULL,
  `provider_tag` varchar(128) NOT NULL DEFAULT '',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `trx_id` varchar(64) NOT NULL DEFAULT '',
  `sender_number` varchar(32) DEFAULT NULL,
  `receiver_number` varchar(32) DEFAULT NULL,
  `sms_timestamp` datetime NOT NULL,
  `sms_date` date DEFAULT NULL,
  `full_sms` text DEFAULT NULL,
  `dedupe_key` varchar(512) NOT NULL DEFAULT '',
  `raw_sms_sha256` varchar(64) DEFAULT NULL,
  `is_synced` tinyint(4) NOT NULL DEFAULT 0,
  `is_used` tinyint(4) NOT NULL DEFAULT 0,
  `used_at` timestamp NULL DEFAULT NULL,
  `used_by_merchant_id` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_trx_id_unique` (`trx_id`),
  UNIQUE KEY `uniq_dedupe` (`user_id`,`dedupe_key`(255)),
  KEY `idx_user_time` (`user_id`,`sms_timestamp`),
  KEY `idx_user_trx` (`user_id`,`trx_id`),
  KEY `idx_provider` (`user_id`,`provider_tag`),
  KEY `idx_verify_lookup` (`user_id`,`trx_id`,`amount`,`is_used`),
  KEY `idx_device_sync` (`device_id`,`is_synced`),
  KEY `idx_raw_sms_hash` (`user_id`,`raw_sms_sha256`),
  KEY `idx_user_id_sms` (`user_id`),
  CONSTRAINT `fk_sms_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `sms_history`
--

LOCK TABLES `sms_history` WRITE;
;
INSERT INTO `sms_history` VALUES
(34,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',11.00,'DFO8MV2D7S','01894086541',NULL,'2026-06-24 04:18:00','2026-06-24','You have received Tk 11.00 from 01894086541. Fee Tk 0.00. Balance Tk 3,771.84. TrxID DFO8MV2D7S at 24/06/2026 10:17','1782274680000|01894086541|c0842441222b4d52f9730388b4205703fb83c38f0de6b3999f42bb90c78c3460','c0842441222b4d52f9730388b4205703fb83c38f0de6b3999f42bb90c78c3460',1,0,NULL,NULL,'2026-06-23 22:17:43'),
(35,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',620.00,'DFO6MW9ASU','01616139422',NULL,'2026-06-24 04:44:56','2026-06-24','You have received Tk 620.00 from 01616139422. Fee Tk 0.00. Balance Tk 4,391.84. TrxID DFO6MW9ASU at 24/06/2026 10:44','1782276296000|01616139422|37fc0285b29ecd82f59a598cc0036ba85b3c32320d7c562bf07e4baf0ee3777d','37fc0285b29ecd82f59a598cc0036ba85b3c32320d7c562bf07e4baf0ee3777d',1,0,NULL,NULL,'2026-06-23 22:44:39'),
(36,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',30.00,'DFO1MYB0WR','01913576525',NULL,'2026-06-24 05:26:55','2026-06-24','You have received Tk 30.00 from 01913576525. Fee Tk 0.00. Balance Tk 2,870.35. TrxID DFO1MYB0WR at 24/06/2026 11:26','1782278815000|01913576525|7a7003c3a59b99a3a4ecc47cf9f950f9e6a6977ce35f3dac2d02a38b19686b1e','7a7003c3a59b99a3a4ecc47cf9f950f9e6a6977ce35f3dac2d02a38b19686b1e',1,0,NULL,NULL,'2026-06-23 23:26:36'),
(37,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',1000.00,'DFO5MYSTGB','01601630749',NULL,'2026-06-24 05:37:02','2026-06-24','Cash In Tk 1,000.00 from 01601630749 successful. Fee Tk 0.00. Balance Tk 3,870.35. TrxID DFO5MYSTGB at 24/06/2026 11:37. Download App: https://bKa.sh/8app','1782279422000|01601630749|0176005ded45bcf67581f7590de21ed2872b622668017c60aed7f6ec04a7866e','0176005ded45bcf67581f7590de21ed2872b622668017c60aed7f6ec04a7866e',1,0,NULL,NULL,'2026-06-23 23:36:42'),
(38,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',652.00,'DFO4N0GRTE','01647741085',NULL,'2026-06-24 06:11:06','2026-06-24','You have received Tk 652.00 from 01647741085. Fee Tk 0.00. Balance Tk 4,522.35. TrxID DFO4N0GRTE at 24/06/2026 12:11','1782281466000|01647741085|05d62d5eda7fd2a9129df0e39b50564da330dca6d61d701e211ac6837a35aa5b','05d62d5eda7fd2a9129df0e39b50564da330dca6d61d701e211ac6837a35aa5b',1,1,'2026-06-24 00:26:56',NULL,'2026-06-24 00:10:45'),
(39,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',885.00,'DFO7N1EVLH','01740713420',NULL,'2026-06-24 06:29:49','2026-06-24','You have received Tk 885.00 from 01740713420. Fee Tk 0.00. Balance Tk 5,276.84. TrxID DFO7N1EVLH at 24/06/2026 12:29','1782282589000|01740713420|22a812a310b157acf619ad7adf424adf72e3e273550bf11242602676c8d79407','22a812a310b157acf619ad7adf424adf72e3e273550bf11242602676c8d79407',1,0,NULL,NULL,'2026-06-24 00:29:33'),
(40,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',1000.00,'DFO5N5AKFR','01620814488',NULL,'2026-06-24 08:02:59','2026-06-24','You have received Tk 1,000.00 from 01620814488. Fee Tk 0.00. Balance Tk 5,512.35. TrxID DFO5N5AKFR at 24/06/2026 14:02','1782288179000|01620814488|ec890f4818bee34351fa7c2f84fa6ffa60839404a1005a148369e0774dfc8cde','ec890f4818bee34351fa7c2f84fa6ffa60839404a1005a148369e0774dfc8cde',1,0,NULL,NULL,'2026-06-24 02:15:25'),
(41,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',284.00,'DFO3N5B2R5','01781497437',NULL,'2026-06-24 08:03:12','2026-06-24','You have received Tk 284.00 from 01781497437. Fee Tk 0.00. Balance Tk 5,796.35. TrxID DFO3N5B2R5 at 24/06/2026 14:03','1782288192000|01781497437|b422578b8d6c17c2f3480056870f7cb23ab1f34080ea41b7cd3a637f3edd4e4e','b422578b8d6c17c2f3480056870f7cb23ab1f34080ea41b7cd3a637f3edd4e4e',1,0,NULL,NULL,'2026-06-24 02:15:25'),
(42,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',11.00,'DFO1NA3CG7','01894086541',NULL,'2026-06-24 10:21:05','2026-06-24','You have received Tk 11.00 from 01894086541. Fee Tk 0.00. Balance Tk 5,847.84. TrxID DFO1NA3CG7 at 24/06/2026 16:21','1782296465000|01894086541|6df96886fe2c371352de555658defdd1eecc3c84b4c246fb0bb6654be941bb3b','6df96886fe2c371352de555658defdd1eecc3c84b4c246fb0bb6654be941bb3b',1,0,NULL,NULL,'2026-06-24 04:20:49'),
(43,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',12.00,'DFO9NA9P5R','01894086541',NULL,'2026-06-24 10:25:16','2026-06-24','You have received Tk 12.00 from 01894086541. Fee Tk 0.00. Balance Tk 5,859.84. TrxID DFO9NA9P5R at 24/06/2026 16:25','1782296716000|01894086541|0bbcc3df8829b254c92210fa53764741a505621f7e425184d2eb6b293e693197','0bbcc3df8829b254c92210fa53764741a505621f7e425184d2eb6b293e693197',1,0,NULL,NULL,'2026-06-24 04:25:00'),
(44,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',13.00,'DFO8NAB4N2','01894086541',NULL,'2026-06-24 10:26:53','2026-06-24','You have received Tk 13.00 from 01894086541. Fee Tk 0.00. Balance Tk 5,872.84. TrxID DFO8NAB4N2 at 24/06/2026 16:26','1782296813000|01894086541|413385028a10467be746c8c9132d7e490b49fd6842c741aedf241f1657ab9e18','413385028a10467be746c8c9132d7e490b49fd6842c741aedf241f1657ab9e18',1,0,NULL,NULL,'2026-06-24 04:26:35'),
(45,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',14.00,'DFO7NACDIL','01894086541',NULL,'2026-06-24 10:27:53','2026-06-24','You have received Tk 14.00 from 01894086541. Fee Tk 0.00. Balance Tk 5,886.84. TrxID DFO7NACDIL at 24/06/2026 16:27','1782296873000|01894086541|fd24a213275749049dc584367d4588b17839269852e4d5e284d0e77d19ceb4a9','fd24a213275749049dc584367d4588b17839269852e4d5e284d0e77d19ceb4a9',1,0,NULL,NULL,'2026-06-24 04:27:36'),
(46,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',535.00,'DFO0NAGLQE','01947563496',NULL,'2026-06-24 10:30:26','2026-06-24','You have received Tk 535.00 from 01947563496. Fee Tk 0.00. Balance Tk 7,403.35. TrxID DFO0NAGLQE at 24/06/2026 16:30','1782297026000|01947563496|06cf04b12c6f1691929bed64fde96e32e40c7e7ae1c5c7d52078095c7ba638db','06cf04b12c6f1691929bed64fde96e32e40c7e7ae1c5c7d52078095c7ba638db',1,0,NULL,NULL,'2026-06-24 04:30:07'),
(47,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',1880.00,'DFO8NANYA8','01303279475',NULL,'2026-06-24 10:35:42','2026-06-24','You have received Tk 1,880.00 from 01303279475. Fee Tk 0.00. Balance Tk 9,283.35. TrxID DFO8NANYA8 at 24/06/2026 16:35','1782297342000|01303279475|f6db556ee1ac6791d73ccae1cef091c02e1353a8d9ca2d7f326661d94de760cd','f6db556ee1ac6791d73ccae1cef091c02e1353a8d9ca2d7f326661d94de760cd',1,0,NULL,NULL,'2026-06-24 04:35:22'),
(48,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',460.00,'DFO5NBNLKR','01314341107',NULL,'2026-06-24 11:00:45','2026-06-24','You have received Tk 460.00 from 01314341107. Fee Tk 0.00. Balance Tk 6,346.84. TrxID DFO5NBNLKR at 24/06/2026 17:00','1782298845000|01314341107|d7d158041199dce1efbefd9f468ed0c9e339dcd1826d4184a3364639cd992c21','d7d158041199dce1efbefd9f468ed0c9e339dcd1826d4184a3364639cd992c21',1,0,NULL,NULL,'2026-06-24 05:00:30'),
(49,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',900.00,'DFO4NCB0L2','01893311045',NULL,'2026-06-24 11:17:04','2026-06-24','You have received Tk 900.00 from 01893311045. Fee Tk 0.00. Balance Tk 10,183.35. TrxID DFO4NCB0L2 at 24/06/2026 17:17','1782299824000|01893311045|832212f353bd19896c25a47fe07c653a7f98ba55e64ea5287935b7af3b95c723','832212f353bd19896c25a47fe07c653a7f98ba55e64ea5287935b7af3b95c723',1,0,NULL,NULL,'2026-06-24 05:16:46'),
(50,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',600.00,'DFO9NGYPTR','01639702401',NULL,'2026-06-24 12:58:32','2026-06-24','You have received Tk 600.00 from 01639702401. Fee Tk 0.00. Balance Tk 7,246.84. TrxID DFO9NGYPTR at 24/06/2026 18:58','1782305912000|01639702401|b24c7b64f40b5cf13dfb491b1143dd8633ddaae42bed6fe230cf3554cb2f307f','b24c7b64f40b5cf13dfb491b1143dd8633ddaae42bed6fe230cf3554cb2f307f',1,0,NULL,NULL,'2026-06-24 07:33:47'),
(51,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',838.00,'DFO1NGORPV','01917183540',NULL,'2026-06-24 12:52:59','2026-06-24','You have received Tk 838.00 from 01917183540. Fee Tk 0.00. Balance Tk 13,910.35. TrxID DFO1NGORPV at 24/06/2026 18:52','1782305579000|01917183540|b95562dd4be2fa124ba9a3ef653f6644dcba7af91b8645a094d278d55bb27400','b95562dd4be2fa124ba9a3ef653f6644dcba7af91b8645a094d278d55bb27400',1,0,NULL,NULL,'2026-06-24 07:33:47'),
(52,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',50.00,'DFO6NIOZM4','01773892740',NULL,'2026-06-24 13:34:06','2026-06-24','You have received Tk 50.00 from 01773892740. Fee Tk 0.00. Balance Tk 13,960.35. TrxID DFO6NIOZM4 at 24/06/2026 19:34','1782308046000|01773892740|2c191d40977cf62dd846e53cd892bd47ff82340093216c3286671c29bb2ebae8','2c191d40977cf62dd846e53cd892bd47ff82340093216c3286671c29bb2ebae8',1,0,NULL,NULL,'2026-06-24 07:33:47'),
(53,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',220.00,'DFO7NEIPTX','01334440104',NULL,'2026-06-24 12:06:42','2026-06-24','Cash In Tk 220.00 from 01334440104 successful. Fee Tk 0.00. Balance Tk 10,403.35. TrxID DFO7NEIPTX at 24/06/2026 18:06. Download App: https://bKa.sh/8app','1782302802000|01334440104|dc6b464f7db389918e8922c4cfaea8bc511305de9ea5c68cd5997296eba014fa','dc6b464f7db389918e8922c4cfaea8bc511305de9ea5c68cd5997296eba014fa',1,0,NULL,NULL,'2026-06-24 07:33:47'),
(54,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',300.00,'DFO7NDDOGX','01988189476',NULL,'2026-06-24 11:42:04','2026-06-24','You have received Tk 300.00 from 01988189476. Fee Tk 0.00. Balance Tk 6,646.84. TrxID DFO7NDDOGX at 24/06/2026 17:42','1782301324000|01988189476|f92fbf56c0ca667a688cd116e1d10e3b19d3c774fddd4358f46c1f36160397f1','f92fbf56c0ca667a688cd116e1d10e3b19d3c774fddd4358f46c1f36160397f1',1,0,NULL,NULL,'2026-06-24 07:33:47'),
(55,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',1,'01894086541','bKash',480.00,'DFO5NIPJ1T','01312496028',NULL,'2026-06-24 13:34:26','2026-06-24','You have received Tk 480.00 from 01312496028. Fee Tk 0.00. Balance Tk 14,440.35. TrxID DFO5NIPJ1T at 24/06/2026 19:34','1782308066000|01312496028|54a7789a510b0b3bb39b847131d09c07940aa2467d11005b8f010a7e844126ab','54a7789a510b0b3bb39b847131d09c07940aa2467d11005b8f010a7e844126ab',1,0,NULL,NULL,'2026-06-24 07:34:07'),
(56,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50',2,'01848790038','bKash',250.00,'DFO2NJ7VFC','01913360338',NULL,'2026-06-24 13:43:17','2026-06-24','You have received Tk 250.00 from 01913360338. Fee Tk 0.00. Balance Tk 7,496.84. TrxID DFO2NJ7VFC at 24/06/2026 19:43','1782308597000|01913360338|a62c1a021812228036b59c82fbe172755ead859b9577010aa070578e7e81f25d','a62c1a021812228036b59c82fbe172755ead859b9577010aa070578e7e81f25d',1,0,NULL,NULL,'2026-06-24 07:43:00');
;
UNLOCK TABLES;

--
-- Table structure for table `sms_parse_failures`
--

DROP TABLE IF EXISTS `sms_parse_failures`;
;
;
CREATE TABLE `sms_parse_failures` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL DEFAULT '',
  `raw_body` text NOT NULL,
  `raw_body_hash` varchar(64) NOT NULL,
  `hmac_signature` varchar(128) NOT NULL,
  `parse_error` varchar(512) DEFAULT NULL,
  `sms_timestamp_ms` bigint(20) DEFAULT NULL,
  `is_resolved` tinyint(4) NOT NULL DEFAULT 0,
  `resolved_at` timestamp NULL DEFAULT NULL,
  `admin_note` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_parse_fail_user` (`user_id`,`created_at`),
  KEY `idx_parse_fail_hash` (`raw_body_hash`),
  KEY `idx_parse_fail_unresolved` (`is_resolved`,`created_at`),
  CONSTRAINT `fk_parse_fail_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `sms_parse_failures`
--

LOCK TABLES `sms_parse_failures` WRITE;
;
INSERT INTO `sms_parse_failures` VALUES
(8,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50','You have received Tk 10.00 from 01894086541. Fee Tk 0.00. Balance Tk 3,760.84. TrxID DFO9MTRJP7 at 24/06/2026 09:43','ab88db5d37442364c108628de80da23bac151ab293902da47b6504d3dde23c49','bf7c0b2fa6527a2676e276449b4274c2d263fe4aedf9e78c3fb8f6258999887b','No matching SMS template found for provided rawBody',1782272600000,0,NULL,NULL,'2026-06-23 21:43:03'),
(9,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50','You have received Tk 10.00 from 01894086541. Fee Tk 0.00. Balance Tk 5,836.84. TrxID DFO0N8LARG at 24/06/2026 15:38','8863524cb483b2422c86aefff089df142514bca8b7a52658a7c85321e5e0d3ac','0e3178ec9392fae316f7e180949cc4f502870ebe83c2a002565a44df68f462dc','No matching SMS template found for provided rawBody',1782293902000,0,NULL,NULL,'2026-06-24 03:38:05'),
(10,4,'548ed64a68366da1b899e928033bac17aeef37ffa4ab0a77cd8f4c71b3afcf50','You have received Tk 490.00 from 01776075619. Ref imon. Fee Tk 0.00. Balance Tk 6,918.35. TrxID DFO8N8SERS at 24/06/2026 15:44','ba9bbd1edc83b2eb64602723f35da287bc73b2ccdaf06e854ed7de34f211033f','922bd68fd6739d242b7f0fa701c55ad4f627fa39f01a86bfe96e84a50486d13f','No matching SMS template found for provided rawBody',1782294272000,0,NULL,NULL,'2026-06-24 03:44:13');
;
UNLOCK TABLES;

--
-- Table structure for table `sms_templates`
--

DROP TABLE IF EXISTS `sms_templates`;
;
;
CREATE TABLE `sms_templates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL,
  `template_name` varchar(128) NOT NULL,
  `sender_id` varchar(64) NOT NULL DEFAULT '',
  `matching_keyword` varchar(255) DEFAULT NULL,
  `regex_pattern` text DEFAULT NULL,
  `is_official` tinyint(4) NOT NULL DEFAULT 1,
  `is_active` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `sender_number` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_template_name` (`template_name`),
  KEY `idx_template_sender` (`sender_id`),
  KEY `idx_template_user` (`user_id`),
  CONSTRAINT `fk_template_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `sms_templates`
--

LOCK TABLES `sms_templates` WRITE;
;
INSERT INTO `sms_templates` VALUES
(1,NULL,'bKash Personal','bKash','','^You have received Tk (?<amount>[\\d,\\.]+) from (?<sender>[\\d*xX]+)\\. Fee Tk 0\\.00\\. Balance Tk (.*)\\. TrxID (?<trxid>[A-Za-z0-9]+) at (.*)$|||^Cash In Tk (?<amount>[\\d,\\.]+) from (?<sender>[\\d*xX]+) successful\\. Fee Tk 0\\.00\\. Balance Tk (.*)\\. TrxID (?<trxid>[A-Za-z0-9]+) at (.*)\\. Download App: https:\\/\\/bKa\\.sh\\/8app$',1,1,'2026-06-24 05:22:00','2026-06-24 05:22:00',NULL),
(11,NULL,'Nagad personal','NAGAD','','^Money Received\\.\nAmount: Tk (?<amount>[\\d,\\.]+)\nSender: \\(sender\\}\nRef: N\\/A\nTxnID: (?<trxid>[A-Za-z0-9]+)\nBalance: Tk (.*)\n(.*)$|||^Cash In Received\\.\nAmount: Tk (?<amount>[\\d,\\.]+)\nUddokta: \\(sender\\}\nTxnID: (?<trxid>[A-Za-z0-9]+)\nBalance: (.*)\n(.*)$',1,1,'2026-06-23 21:33:41','2026-06-24 06:59:02','NAGAD'),
(12,NULL,'Upay personal','upay','','^Send money of Tk\\. (?<amount>[\\d,\\.]+) to \\(sender\\} is successful\\. Fee Tk\\. 0\\.0\\. Balance Tk\\. (.*)\\. Ref\\-\\. TrxID (?<trxid>[A-Za-z0-9]+) at (.*)\\.$',1,1,'2026-06-23 21:34:41','2026-06-24 07:00:30','upay'),
(13,NULL,'Rocket personal','16216','','',1,1,'2026-06-23 21:36:09','2026-06-24 07:01:44','16216'),
(14,NULL,'bkash agent','bkash','','^Cash Out Tk (.*) from (.*) successful\\. Fee Tk 0\\.00\\. Balance Tk (.*)\\. TrxID (.*) at (.*)$',1,1,'2026-06-23 23:05:06','2026-06-23 23:05:06','bkash'),
(15,NULL,'Nagad agent','NAGAD','','^Cash Out Received\\.\nAmount: Tk (.*)\nCustomer: (.*)\nTxnID: (.*)\nComm: Tk (.*)\nBalance: Tk (.*)\n(.*)$',1,1,'2026-06-23 23:05:52','2026-06-23 23:05:52','NAGAD');
;
UNLOCK TABLES;

--
-- Table structure for table `smtp_gateways`
--

DROP TABLE IF EXISTS `smtp_gateways`;
;
;
CREATE TABLE `smtp_gateways` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `host` varchar(255) NOT NULL DEFAULT 'smtp.gmail.com',
  `port` int(11) NOT NULL DEFAULT 465,
  `secure` tinyint(4) NOT NULL DEFAULT 1,
  `daily_limit` int(11) NOT NULL DEFAULT 500,
  `sent_today` int(11) NOT NULL DEFAULT 0,
  `last_reset_at` timestamp NULL DEFAULT NULL,
  `is_active` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_email_acc` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `smtp_gateways`
--

LOCK TABLES `smtp_gateways` WRITE;
;
INSERT INTO `smtp_gateways` VALUES
(1,'paymentcheckerbd@gmail.com','vabh qblx ostm xbih','smtp.gmail.com',465,1,499,6,NULL,1,'2026-06-19 10:49:55','2026-06-24 09:32:40');
;
UNLOCK TABLES;

--
-- Table structure for table `subscription_plans`
--

DROP TABLE IF EXISTS `subscription_plans`;
;
;
CREATE TABLE `subscription_plans` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `plan_name` varchar(50) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `max_sites` int(11) NOT NULL,
  `max_devices` int(11) NOT NULL,
  `duration_days` int(11) DEFAULT 365,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `plan_name` (`plan_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
;

--
-- Dumping data for table `subscription_plans`
--

LOCK TABLES `subscription_plans` WRITE;
;
;
UNLOCK TABLES;

--
-- Table structure for table `user_credentials`
--

DROP TABLE IF EXISTS `user_credentials`;
;
;
CREATE TABLE `user_credentials` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `type` enum('phone','email') NOT NULL,
  `value` varchar(255) NOT NULL,
  `verified_at` datetime DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_cred_value` (`value`),
  KEY `idx_cred_user_type` (`user_id`,`type`),
  CONSTRAINT `fk_cred_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `user_credentials`
--

LOCK TABLES `user_credentials` WRITE;
;
INSERT INTO `user_credentials` VALUES
(16,4,'email','nobab.yousuf.hazi.99@gmail.com','2026-06-23 23:56:23','2026-06-23 17:56:06'),
(17,4,'phone','01894086541','2026-06-23 23:56:23','2026-06-23 17:56:23'),
(26,12,'phone','01711223344','2026-06-24 00:51:33','2026-06-23 18:51:33'),
(27,4,'email','bangladeshy7@gmail.com','2026-06-24 05:16:50','2026-06-23 23:16:50'),
(28,4,'phone','01614677619','2026-06-24 05:17:06','2026-06-23 23:17:06');
;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
;
;
CREATE TABLE `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `avatar` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `pin` varchar(255) NOT NULL DEFAULT '',
  `balance` decimal(12,2) NOT NULL DEFAULT 0.00,
  `blocked` tinyint(4) NOT NULL DEFAULT 0,
  `role` varchar(20) NOT NULL DEFAULT 'user',
  `is_paid` tinyint(4) DEFAULT 0,
  `active_plan_name` varchar(50) DEFAULT 'FREE_LEVEL',
  `expiry_date` date DEFAULT NULL,
  `profile_complete` tinyint(4) NOT NULL DEFAULT 0,
  `sms_enabled` tinyint(4) NOT NULL DEFAULT 1,
  `gmail_enabled` tinyint(4) NOT NULL DEFAULT 0,
  `fcm_token` varchar(512) DEFAULT NULL,
  `secretKey` varchar(128) DEFAULT NULL,
  `secretKeyVersion` int(10) unsigned NOT NULL DEFAULT 1,
  `secretKeyCreatedAt` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_phone` (`phone`),
  UNIQUE KEY `uniq_email` (`email`),
  KEY `idx_role` (`role`),
  KEY `idx_blocked` (`blocked`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
;
INSERT INTO `users` VALUES
(4,'MD Yousuf',NULL,'01894086541','nobab.yousuf.hazi.99@gmail.com','$2a$10$Sno0IIdy6JRRA1kMOmcdGOC9nNZe5/fnRzmNrjAWbBFfI2o9dFGmy',0.00,0,'user',1,'Trial Package','2026-06-30',1,1,0,NULL,'f0aa6fe630cefd9470441cece8fea091a11de648be73fb3d53f67c7d28791b5f',1,'2026-06-23 17:56:23','2026-06-23 17:56:06','2026-06-23 17:56:23'),
(12,'',NULL,'01711223344',NULL,'',0.00,0,'user',1,'Basic','2026-06-29',1,1,0,'mock_firebase_push_token_xyz_123',NULL,1,NULL,'2026-06-23 18:51:33','2026-06-23 18:51:33');
;
UNLOCK TABLES;
;

;
;
;
;
;
;
;

-- Dump completed on 2026-06-24 22:07:23

