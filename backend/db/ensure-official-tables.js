const prisma = require('./prisma');

async function ensureOfficialTables() {
  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS official_reviews (
      id INT NOT NULL AUTO_INCREMENT,
      rating INT NOT NULL DEFAULT 5,
      author_name VARCHAR(64) NOT NULL,
      company VARCHAR(128) NULL,
      merchant_type VARCHAR(64) NULL,
      country_flag VARCHAR(16) NULL,
      comment TEXT NOT NULL,
      status VARCHAR(16) NOT NULL DEFAULT 'pending',
      helpful_count INT NOT NULL DEFAULT 0,
      admin_reply TEXT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      KEY idx_official_reviews_status_created (status, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  `);

  await prisma.$executeRawUnsafe(`
    CREATE TABLE IF NOT EXISTS official_companies (
      id INT NOT NULL AUTO_INCREMENT,
      name VARCHAR(128) NOT NULL,
      logo_url VARCHAR(512) NOT NULL,
      website_url VARCHAR(512) NULL,
      industry VARCHAR(128) NULL,
      country VARCHAR(64) NULL,
      merchant_since VARCHAR(32) NULL,
      is_verified TINYINT NOT NULL DEFAULT 1,
      priority INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  `);
}

module.exports = { ensureOfficialTables };
