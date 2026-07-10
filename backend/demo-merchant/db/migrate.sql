-- Demo Merchant v1 tables (run against the shared PayCheck database)
-- Usage: mysql -u ... -p DB_NAME < backend/demo-merchant/db/migrate.sql

CREATE TABLE IF NOT EXISTS demo_merchant_users (
  id INT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(16) NOT NULL DEFAULT 'user',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY demo_merchant_users_email_key (email),
  KEY idx_dm_user_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS demo_merchant_wallets (
  user_id INT NOT NULL,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT demo_merchant_wallets_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES demo_merchant_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS demo_merchant_wallet_ledger (
  id INT NOT NULL AUTO_INCREMENT,
  user_id INT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  balance_after DECIMAL(12,2) NOT NULL,
  entry_type VARCHAR(16) NOT NULL,
  reference_type VARCHAR(32) NOT NULL,
  reference_id VARCHAR(64) NULL,
  description VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dm_ledger_user_created (user_id, created_at),
  CONSTRAINT demo_merchant_wallet_ledger_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES demo_merchant_users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS demo_merchant_products (
  id INT NOT NULL AUTO_INCREMENT,
  sku VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description TEXT NULL,
  price DECIMAL(12,2) NOT NULL,
  image_url VARCHAR(512) NULL,
  is_active TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY demo_merchant_products_sku_key (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS demo_merchant_orders (
  id INT NOT NULL AUTO_INCREMENT,
  user_id INT NOT NULL,
  order_number VARCHAR(64) NOT NULL,
  order_type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  amount DECIMAL(12,2) NOT NULL,
  product_id INT NULL,
  payment_session_token VARCHAR(128) NULL,
  paychek_order_id VARCHAR(191) NULL,
  paychek_payment_id VARCHAR(128) NULL,
  trace_id VARCHAR(64) NULL,
  meta_json TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  UNIQUE KEY demo_merchant_orders_order_number_key (order_number),
  KEY idx_dm_order_user_status (user_id, status),
  KEY idx_dm_order_user_created (user_id, created_at),
  KEY idx_dm_order_session (payment_session_token),
  CONSTRAINT demo_merchant_orders_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES demo_merchant_users(id) ON DELETE CASCADE,
  CONSTRAINT demo_merchant_orders_product_id_fkey
    FOREIGN KEY (product_id) REFERENCES demo_merchant_products(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS demo_merchant_transactions (
  id INT NOT NULL AUTO_INCREMENT,
  user_id INT NOT NULL,
  order_id INT NULL,
  txn_type VARCHAR(32) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  description VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dm_txn_user_type (user_id, txn_type),
  KEY idx_dm_txn_user_created (user_id, created_at),
  CONSTRAINT demo_merchant_transactions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES demo_merchant_users(id) ON DELETE CASCADE,
  CONSTRAINT demo_merchant_transactions_order_id_fkey
    FOREIGN KEY (order_id) REFERENCES demo_merchant_orders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
