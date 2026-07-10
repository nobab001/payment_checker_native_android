-- Links Vibe Mode requests to pay/init payment_sessions (redirect + webhook).
ALTER TABLE checkout_vibe_requests
  ADD COLUMN payment_session_token VARCHAR(80) NULL AFTER website_id;

CREATE INDEX idx_vibe_payment_session ON checkout_vibe_requests (payment_session_token);
