# Database

## Engine

MySQL accessed exclusively through **Prisma**. Schema: `backend/prisma/schema.prisma`.

## Key models (selected)

| Model | Purpose |
|-------|---------|
| `users` | Merchant/admin accounts |
| `user_credentials` | Credential material |
| `registered_devices` | Bound devices, roles, approval, setup flags |
| `otps` / `otp_sms_templates` | OTP lifecycle |
| `sms_templates` / `sms_history` / `sms_parse_failures` | SMS matching pipeline |
| `sms_gateways` / `smtp_gateways` | Channel config |
| `gateway_layouts` / `gateway_methods` | Website checkout layout & methods |
| `website_official_gateways` | Official gateway bindings |
| `payment_sessions` | Checkout sessions |
| `merchant_callback_outbox` | Reliable merchant webhooks |
| `merchant_commissions` / `comm_policy` | Fees / campaigns |
| `subscription_plans` / `addon_plans` | Billing |
| `global_config` | Public/admin config flags (maintenance, social links, etc.) |
| `audit_logs` | Audit trail |
| `device_trial_logs` | Trial / abuse tracking |
| `custom_sms_archives` | Custom SMS archives |
| `demo_visitors` / `demo_payments` | Official site demo |

## Change policy

1. Prefer additive columns with safe defaults for production.
2. Use Prisma migrate in controlled environments; on VPS, additive `db/ensure-*.js` scripts are sometimes used for zero-downtime guards — document any new ensure script in this file when added.
3. Never drop columns used by live app versions without a compatibility window.
4. Index columns used in hot paths (session id, device id, website id, created_at).

## Data sensitivity

Treat phone numbers, emails, SMS bodies, tokens, and API secrets as sensitive. Minimize logging of PII. Mask credentials in device-bound dialogs (already practiced in app UI).

## Backups

Production backups must include MySQL dumps on a schedule (see `docs/deployment/production.md`). Test restore periodically.
