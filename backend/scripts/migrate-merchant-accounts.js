require('dotenv').config();
const { PrismaClient } = require('@prisma/client');
const p = new PrismaClient();

(async () => {
  await p.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS merchant_accounts (
      id INT NOT NULL AUTO_INCREMENT,
      website_id INT NOT NULL,
      provider VARCHAR(40) NOT NULL,
      merchant_name VARCHAR(120) NOT NULL,
      merchant_ref VARCHAR(120) NULL,
      logo_url VARCHAR(512) NULL,
      api_key VARCHAR(512) NULL,
      api_secret_enc TEXT NULL,
      username VARCHAR(255) NULL,
      password_enc TEXT NULL,
      app_key VARCHAR(512) NULL,
      app_secret_enc TEXT NULL,
      base_url VARCHAR(512) NULL,
      callback_url VARCHAR(512) NULL,
      is_active TINYINT NOT NULL DEFAULT 1,
      is_default TINYINT NOT NULL DEFAULT 0,
      priority INT NOT NULL DEFAULT 0,
      notes TEXT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      INDEX idx_merchant_acct_website_active (website_id, is_active),
      INDEX idx_merchant_acct_provider (website_id, provider, is_active)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);
  const n = await p.merchant_accounts.count();
  console.log('merchant_accounts ready, count=', n);
  await p.$disconnect();
})().catch(async (e) => {
  console.error(e);
  await p.$disconnect();
  process.exit(1);
});
