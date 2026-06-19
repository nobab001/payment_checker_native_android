-- MariaDB dump 10.19  Distrib 10.4.32-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: paychek_online_v2
-- ------------------------------------------------------
-- Server version	10.4.32-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `checkout_view_templates`
--

DROP TABLE IF EXISTS `checkout_view_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `checkout_view_templates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sms_template_id` int(11) NOT NULL,
  `single_number_instruction` text NOT NULL COMMENT 'Instruction shown when 1 number is active',
  `multiple_number_instruction` text NOT NULL COMMENT 'Instruction shown when >1 numbers are active',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_sms_template` (`sms_template_id`),
  CONSTRAINT `fk_checkout_view_sms_template` FOREIGN KEY (`sms_template_id`) REFERENCES `sms_templates` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `checkout_view_templates`
--

LOCK TABLES `checkout_view_templates` WRITE;
/*!40000 ALTER TABLE `checkout_view_templates` DISABLE KEYS */;
INSERT INTO `checkout_view_templates` VALUES (9,1,'নিচের বিকাশ পার্সোনাল নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় বিকাশ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 06:33:49','2026-06-19 06:33:49'),(10,2,'নিচের নগদ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় নগদ নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 06:33:49','2026-06-19 06:33:49'),(11,3,'নিচের রকেট নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় রকেট নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 06:33:49','2026-06-19 06:33:49'),(13,5,'নিচের বিকাশ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় বিকাশ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 06:33:49','2026-06-19 06:33:49'),(14,6,'নিচের নগদ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় নগদ এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 06:33:49','2026-06-19 06:33:49'),(54,4,'নিচের উপায় নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় উপায় নম্বরে Send Money করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 09:31:52','2026-06-19 09:31:52'),(58,7,'নিচের রকেট এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় রকেট এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 09:31:52','2026-06-19 09:31:52'),(59,8,'নিচের উপায় এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি নিচে দিয়ে সাবমিট করুন।','নিচের যেকোনো একটি সক্রিয় উপায় এজেন্ট নম্বরে Cash In করুন এবং ট্রানজেকশন আইডি সাবমিট করুন।','2026-06-19 09:31:52','2026-06-19 09:31:52');
/*!40000 ALTER TABLE `checkout_view_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `device_trial_logs`
--

DROP TABLE IF EXISTS `device_trial_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `device_trial_logs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL,
  `android_id` varchar(255) NOT NULL,
  `hardware_fingerprint` varchar(255) NOT NULL,
  `sim_slot_ids` varchar(255) NOT NULL,
  `has_used_trial` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_device_binding` (`android_id`,`hardware_fingerprint`,`sim_slot_ids`),
  KEY `idx_android_id` (`android_id`),
  KEY `idx_fingerprint` (`hardware_fingerprint`),
  KEY `idx_sim_slots` (`sim_slot_ids`),
  KEY `idx_dtl_android` (`android_id`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `device_trial_logs`
--

LOCK TABLES `device_trial_logs` WRITE;
/*!40000 ALTER TABLE `device_trial_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `device_trial_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `email_accounts`
--

DROP TABLE IF EXISTS `email_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `email_accounts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL COMMENT 'SMTP app-specific password',
  `host` varchar(255) NOT NULL DEFAULT 'smtp.gmail.com',
  `port` int(11) NOT NULL DEFAULT 465,
  `secure` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1 = SSL, 0 = TLS',
  `daily_limit` int(11) NOT NULL DEFAULT 500,
  `sent_today` int(11) NOT NULL DEFAULT 0,
  `last_reset_at` timestamp NULL DEFAULT NULL COMMENT 'Last time sent_today was reset to 0',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_email_acc` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `email_accounts`
--

LOCK TABLES `email_accounts` WRITE;
/*!40000 ALTER TABLE `email_accounts` DISABLE KEYS */;
INSERT INTO `email_accounts` VALUES (1,'paymentcheckerbd@gmail.com','vabh qblx ostm xbih','smtp.gmail.com',465,1,399,0,NULL,1,'2026-06-19 09:03:11','2026-06-19 09:03:11');
/*!40000 ALTER TABLE `email_accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gateway_layouts`
--

DROP TABLE IF EXISTS `gateway_layouts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `gateway_layouts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `site_name` varchar(255) NOT NULL DEFAULT '' COMMENT 'Merchant site label',
  `site_url` varchar(512) DEFAULT NULL COMMENT 'Target checkout website URL',
  `api_key` varchar(255) NOT NULL DEFAULT '' COMMENT 'Public API key for verification requests',
  `api_secret` varchar(255) NOT NULL DEFAULT '' COMMENT 'SHA256 hashed secret key',
  `layout_config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Checkout block definitions (blueprint: checkout_layout.dart)' CHECK (json_valid(`layout_config`)),
  `redirect_url` varchar(512) DEFAULT NULL COMMENT 'Success redirect target URL',
  `callback_url` varchar(512) DEFAULT NULL COMMENT 'Webhook POST endpoint for payment events',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_api_key` (`api_key`),
  KEY `idx_gateway_user` (`user_id`),
  KEY `idx_gateway_active` (`user_id`,`is_active`),
  CONSTRAINT `fk_gateway_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gateway_layouts`
--

LOCK TABLES `gateway_layouts` WRITE;
/*!40000 ALTER TABLE `gateway_layouts` DISABLE KEYS */;
/*!40000 ALTER TABLE `gateway_layouts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `global_config`
--

DROP TABLE IF EXISTS `global_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `global_config` (
  `config_key` varchar(128) NOT NULL,
  `config_value` text NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `global_config`
--

LOCK TABLES `global_config` WRITE;
/*!40000 ALTER TABLE `global_config` DISABLE KEYS */;
INSERT INTO `global_config` VALUES ('facebook_support_link','','2026-06-19 06:22:21'),('gmail_tracking_enabled','false','2026-06-19 06:22:21'),('maintenance_mode','false','2026-06-19 06:22:21'),('registration_enabled','true','2026-06-19 06:22:21'),('sms_tracking_enabled','true','2026-06-19 06:22:21'),('telegram_support_link','','2026-06-19 06:22:21'),('trial_days','7','2026-06-19 06:22:21'),('whatsapp_support_link','','2026-06-19 06:22:21'),('youtube_support_link','','2026-06-19 06:22:21');
/*!40000 ALTER TABLE `global_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `otps`
--

DROP TABLE IF EXISTS `otps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `otps` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `contact` varchar(255) NOT NULL COMMENT 'Phone number or Email address',
  `code` varchar(10) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used_at` datetime DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_contact` (`contact`),
  KEY `idx_code_contact` (`code`,`contact`),
  KEY `idx_contact_code` (`contact`,`code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `otps`
--

LOCK TABLES `otps` WRITE;
/*!40000 ALTER TABLE `otps` DISABLE KEYS */;
INSERT INTO `otps` VALUES (1,'nobab.yousuf.hazi.99@gmail.com','313996','2026-06-19 15:01:35','2026-06-19 14:59:15','2026-06-19 08:56:35');
/*!40000 ALTER TABLE `otps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `registered_devices`
--

DROP TABLE IF EXISTS `registered_devices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `registered_devices` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL COMMENT 'Android Hardware ID (android_id)',
  `android_id` varchar(255) DEFAULT NULL COMMENT 'Raw Android ID',
  `hardware_fingerprint` varchar(512) DEFAULT NULL COMMENT 'Build.FINGERPRINT',
  `sim_slot_ids` text DEFAULT NULL COMMENT 'SIM subscription IDs JSON',
  `device_name` varchar(255) NOT NULL DEFAULT 'My Phone',
  `custom_name` varchar(255) DEFAULT NULL COMMENT 'User-renamed label',
  `device_model` varchar(255) NOT NULL DEFAULT '',
  `android_version` varchar(64) NOT NULL DEFAULT '',
  `status` enum('pending','active','rejected') NOT NULL DEFAULT 'pending',
  `is_parent` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=Parent device, 0=Child device',
  `is_approved` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0=pending, 1=approved',
  `device_role` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'owner | restricted | pending',
  `sim1_number` varchar(32) DEFAULT NULL,
  `sim1_operator` varchar(64) DEFAULT NULL,
  `sim2_number` varchar(32) DEFAULT NULL,
  `sim2_operator` varchar(64) DEFAULT NULL,
  `sim_settings` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'Full slot filter JSON from app' CHECK (json_valid(`sim_settings`)),
  `last_seen_at` timestamp NULL DEFAULT NULL,
  `last_battery_percent` tinyint(3) unsigned DEFAULT NULL,
  `trial_started_at` timestamp NULL DEFAULT NULL,
  `trial_expires_at` timestamp NULL DEFAULT NULL,
  `is_trial_locked` tinyint(1) NOT NULL DEFAULT 0,
  `lock_reason` varchar(64) DEFAULT NULL COMMENT 'trial_expired | manual_block | payment_required',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `custom_device_name` varchar(100) NOT NULL DEFAULT '',
  `sim_one_number` varchar(15) DEFAULT NULL,
  `sim_one_active` tinyint(1) DEFAULT 1,
  `sim_two_number` varchar(15) DEFAULT NULL,
  `sim_two_active` tinyint(1) DEFAULT 1,
  `is_app_active` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_device` (`user_id`,`device_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_parent` (`is_parent`),
  KEY `idx_trial_lock` (`is_trial_locked`,`trial_expires_at`),
  CONSTRAINT `fk_devices_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `registered_devices`
--

LOCK TABLES `registered_devices` WRITE;
/*!40000 ALTER TABLE `registered_devices` DISABLE KEYS */;
/*!40000 ALTER TABLE `registered_devices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sms_history`
--

DROP TABLE IF EXISTS `sms_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sms_history` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL DEFAULT '',
  `sim_slot` tinyint(4) DEFAULT NULL COMMENT '1 or 2',
  `sim_number` varchar(32) DEFAULT NULL,
  `provider_tag` varchar(128) NOT NULL DEFAULT '' COMMENT 'bKash | Nagad | Rocket | Upay',
  `amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `trx_id` varchar(64) NOT NULL DEFAULT '' COMMENT 'Unique Transaction ID from SMS',
  `sender_number` varchar(32) DEFAULT NULL COMMENT 'Sender mobile number or address',
  `receiver_number` varchar(32) DEFAULT NULL,
  `sms_timestamp` datetime NOT NULL COMMENT 'Timestamp parsed from SMS body',
  `sms_date` date DEFAULT NULL,
  `full_sms` text DEFAULT NULL COMMENT 'Original SMS text',
  `dedupe_key` varchar(512) NOT NULL DEFAULT '' COMMENT 'Deduplication composite key',
  `raw_sms_sha256` varchar(64) DEFAULT NULL COMMENT 'SHA256(rawSmsBody) — server-side integrity hash',
  `is_synced` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0=pending upload, 1=sent to server',
  `is_used` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0=unchecked, 1=marked SOLDOUT',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sms_history`
--

LOCK TABLES `sms_history` WRITE;
/*!40000 ALTER TABLE `sms_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `sms_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sms_parse_failures`
--

DROP TABLE IF EXISTS `sms_parse_failures`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sms_parse_failures` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL DEFAULT '',
  `raw_body` text NOT NULL COMMENT 'Original SMS text — HMAC verified',
  `raw_body_hash` varchar(64) NOT NULL COMMENT 'SHA256(rawBody) — for dedup and lookup',
  `hmac_signature` varchar(128) NOT NULL COMMENT 'Client HMAC — verified, kept for audit',
  `parse_error` varchar(512) DEFAULT NULL COMMENT 'Failure reason from parseRawSms()',
  `sms_timestamp_ms` bigint(20) DEFAULT NULL COMMENT 'Original SMS epoch ms timestamp',
  `is_resolved` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0=pending, 1=template added/resolved',
  `resolved_at` timestamp NULL DEFAULT NULL,
  `admin_note` text DEFAULT NULL COMMENT 'Admin note on resolution',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_parse_fail_user` (`user_id`,`created_at`),
  KEY `idx_parse_fail_hash` (`raw_body_hash`),
  KEY `idx_parse_fail_unresolved` (`is_resolved`,`created_at`),
  CONSTRAINT `fk_parse_fail_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sms_parse_failures`
--

LOCK TABLES `sms_parse_failures` WRITE;
/*!40000 ALTER TABLE `sms_parse_failures` DISABLE KEYS */;
/*!40000 ALTER TABLE `sms_parse_failures` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sms_settings`
--

DROP TABLE IF EXISTS `sms_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sms_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provider_name` varchar(128) DEFAULT NULL COMMENT 'Human-readable label e.g. "Green Web SMS"',
  `gateway_url` varchar(512) NOT NULL COMMENT 'Gateway base URL with placeholders',
  `http_method` varchar(10) NOT NULL DEFAULT 'GET',
  `post_body_template` text DEFAULT NULL,
  `api_key` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `sender_id` varchar(64) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sms_settings`
--

LOCK TABLES `sms_settings` WRITE;
/*!40000 ALTER TABLE `sms_settings` DISABLE KEYS */;
INSERT INTO `sms_settings` VALUES (1,NULL,'http://bulksmsbd.net/api/smsapi','GET',NULL,'hknDpPg0AazTNirpWyao','nobab002','8809617626944',1,'2026-06-19 09:03:55','2026-06-19 09:03:55');
/*!40000 ALTER TABLE `sms_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sms_templates`
--

DROP TABLE IF EXISTS `sms_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `sms_templates` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) DEFAULT NULL COMMENT 'NULL for admin/global, user_id for custom templates',
  `template_name` varchar(128) NOT NULL COMMENT 'Display name e.g. bKash Personal',
  `sender_id` varchar(64) NOT NULL DEFAULT '' COMMENT 'Sender address to match e.g. bKash',
  `matching_keyword` varchar(255) DEFAULT NULL COMMENT 'Comma-separated keywords required for strict verification',
  `regex_pattern` text DEFAULT NULL,
  `is_official` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1 = Official admin template, 0 = Custom user template',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_template_name` (`template_name`),
  KEY `idx_template_sender` (`sender_id`),
  KEY `idx_template_user` (`user_id`),
  CONSTRAINT `fk_template_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sms_templates`
--

LOCK TABLES `sms_templates` WRITE;
/*!40000 ALTER TABLE `sms_templates` DISABLE KEYS */;
INSERT INTO `sms_templates` VALUES (1,NULL,'bKash Personal','bKash','You have received,Tk.,Ref:','You have received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 06:22:21','2026-06-19 09:31:52'),(2,NULL,'Nagad Personal','NAGAD','received cash in Tk,TrxID:','received cash in Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 06:22:21','2026-06-19 09:31:52'),(3,NULL,'Rocket Personal','16216','received Tk,TrxID:','received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 06:22:21','2026-06-19 09:31:52'),(4,NULL,'Upay Personal','upay','received Tk,TrxID','received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 09:31:52','2026-06-19 09:31:52'),(5,NULL,'bKash Agent','bKash','Cash In,Tk.,Ref:','Cash In Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 06:22:21','2026-06-19 06:33:49'),(6,NULL,'Nagad Agent','NAGAD','Cash in received,Tk.,TrxID:','Cash in received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 06:22:21','2026-06-19 06:33:49'),(7,NULL,'Rocket Agent','16216','Cash In received,Tk.,TrxID:','Cash In received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID:\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 09:31:52','2026-06-19 09:31:52'),(8,NULL,'Upay Agent','upay','Cash In received,Tk.,TrxID','Cash In received Tk\\s*([\\d,]+(?:\\.\\d+)?)\\s*from\\s*([\\d*Xx]+).*?TrxID\\s*([A-Z0-9]{6,})',1,1,'2026-06-19 09:31:52','2026-06-19 09:31:52');
/*!40000 ALTER TABLE `sms_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subscription_plans`
--

DROP TABLE IF EXISTS `subscription_plans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `subscription_plans` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `plan_name` varchar(50) NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `max_sites` int(11) NOT NULL,
  `max_devices` int(11) NOT NULL,
  `duration_days` int(11) DEFAULT 365,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `plan_name` (`plan_name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subscription_plans`
--

LOCK TABLES `subscription_plans` WRITE;
/*!40000 ALTER TABLE `subscription_plans` DISABLE KEYS */;
INSERT INTO `subscription_plans` VALUES (1,'Basic',100.00,1,1,365,'2026-06-19 08:55:32'),(2,'Standard',200.00,3,3,365,'2026-06-19 08:55:32'),(3,'Premium',500.00,999,10,365,'2026-06-19 08:55:32');
/*!40000 ALTER TABLE `subscription_plans` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_settings`
--

DROP TABLE IF EXISTS `system_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `system_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(128) NOT NULL,
  `setting_value` text DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_setting_key` (`setting_key`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_settings`
--

LOCK TABLES `system_settings` WRITE;
/*!40000 ALTER TABLE `system_settings` DISABLE KEYS */;
INSERT INTO `system_settings` VALUES (1,'otp_format_template','আপনার প্রিয় পে-চেক অ্যাপ ভেরিফিকেশন কোড হলো: {otp}। কোডটি গোপন রাখুন।','2026-06-19 09:31:52');
/*!40000 ALTER TABLE `system_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_credentials`
--

DROP TABLE IF EXISTS `user_credentials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_credentials`
--

LOCK TABLES `user_credentials` WRITE;
/*!40000 ALTER TABLE `user_credentials` DISABLE KEYS */;
INSERT INTO `user_credentials` VALUES (1,1,'email','nobab.yousuf.hazi.99@gmail.com','2026-06-19 15:01:32','2026-06-19 08:59:15'),(2,1,'phone','01894086541','2026-06-19 15:01:32','2026-06-19 08:59:29');
/*!40000 ALTER TABLE `user_credentials` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `avatar` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL COMMENT 'BD format: 01XXXXXXXXX',
  `email` varchar(255) DEFAULT NULL,
  `pin` varchar(255) NOT NULL DEFAULT '' COMMENT 'bcrypt hashed 6-digit PIN',
  `balance` decimal(12,2) NOT NULL DEFAULT 0.00,
  `blocked` tinyint(1) NOT NULL DEFAULT 0,
  `role` varchar(20) NOT NULL DEFAULT 'user' COMMENT 'user | admin',
  `is_paid` tinyint(1) DEFAULT 0,
  `active_plan_name` varchar(50) DEFAULT 'FREE_LEVEL',
  `expiry_date` date DEFAULT NULL,
  `profile_complete` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0=new signup pending, 1=active',
  `sms_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Admin toggle for SMS tracking',
  `gmail_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Admin toggle for Gmail tracking',
  `fcm_token` varchar(512) DEFAULT NULL,
  `secretKey` varchar(128) DEFAULT NULL COMMENT 'Per-user HMAC-SHA256 secret (64-char hex)',
  `secretKeyVersion` int(10) unsigned NOT NULL DEFAULT 1 COMMENT 'Key version counter for rotation tracking',
  `secretKeyCreatedAt` timestamp NULL DEFAULT NULL COMMENT 'Timestamp of key generation or last rotation',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_phone` (`phone`),
  UNIQUE KEY `uniq_email` (`email`),
  KEY `idx_role` (`role`),
  KEY `idx_blocked` (`blocked`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'MD Yousuf',NULL,'01894086541','nobab.yousuf.hazi.99@gmail.com','$2a$10$Kx8eLUjvyQpyoXzpv8QaFeN8.5mvF8rsq60CZXMo1SNnsr5aA54aG',0.00,0,'user',0,'FREE_LEVEL',NULL,1,1,0,NULL,NULL,1,NULL,'2026-06-19 08:59:15','2026-06-19 09:01:32');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'paychek_online_v2'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-19 15:36:06
