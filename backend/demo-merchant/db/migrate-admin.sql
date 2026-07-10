-- Demo Merchant v1.1 — admin role support
ALTER TABLE demo_merchant_users
  ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'user' AFTER full_name;
