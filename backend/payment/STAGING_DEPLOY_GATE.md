# Phase-3B.6 — Staging Deployment Gate

> **Purpose:** Validate Payment Layer in a **real staging environment** before Commit/PR/Production Pilot.  
> **Prerequisite:** Phase-3B.5 hardening code complete + `npm run test:payment-gate` PASS locally.  
> **Pair with:** `PRODUCTION_ACCEPTANCE_GATE.md` (PAG), `GOLDEN_TEST_SUITE.md`, `PRODUCTION_GATE_EVIDENCE.md`, `PRODUCTION_KPIS.md`, `PRODUCTION_RUNBOOK.md`, `OPERATIONS_POLICY.md`, `RELEASE_EVIDENCE_PACKAGE.md`, `RELEASE_STRATEGY.md`, `DOCUMENTATION_FREEZE.md`

---

## Quick Start

```bash
cd backend

# 1. Fresh staging DB migration
npm run db:hardening-3b5
node scripts/add-website-trx-unique.js

# 2. Preflight (env + DB + indexes + gate)
npm run staging:preflight

# 3. Start server
APP_ENV=staging npm start

# 4. Health probes
curl -s https://staging.example/health | jq .
curl -s https://staging.example/payment/health | jq .
curl -s -H "X-Payment-Metrics-Key: $PAYMENT_METRICS_API_KEY" \
  https://staging.example/api/v1/payment/metrics | jq .
```

---

## Stage-1: Environment প্রস্তুত

### Staging Database (আলাদা)

| Rule | Detail |
|------|--------|
| **Never** | Production DB credentials on staging |
| **Always** | Fresh migration on empty staging DB |
| **Verify** | All payment tables + indexes |

### Migration Checklist

```bash
npm run db:hardening-3b5
node scripts/add-website-trx-unique.js
npm run staging:preflight
```

### Tables & Indexes

| Item | Verify |
|------|--------|
| `payment_sessions` | ✅ table exists |
| `merchant_callback_outbox` | ✅ table exists |
| `audit_logs` | ✅ table exists |
| `uniq_session_token` | ✅ on `payment_sessions.session_token` |
| `uniq_website_trx` | ✅ on `(website_id, trx_id)` |
| `uniq_mcb_delivery_key` | ✅ on `merchant_callback_outbox.delivery_key` |

**SQL verify:**

```sql
SHOW TABLES LIKE 'payment_sessions';
SHOW TABLES LIKE 'merchant_callback_outbox';
SHOW INDEX FROM payment_sessions WHERE Key_name = 'uniq_website_trx';
SHOW INDEX FROM merchant_callback_outbox WHERE Key_name = 'uniq_mcb_delivery_key';
```

---

## Stage-2: Environment Variables

Copy `.env.staging.example` → `.env` on staging server.

### Required

```env
APP_ENV=staging
NODE_ENV=production
PORT=3000

DATABASE_URL=mysql://user:pass@staging-db:3306/paychek_staging

REDIS_HOST=staging-redis
REDIS_PORT=6379

PAYMENT_METRICS_API_KEY=<generate-unique-staging-key>
JWT_SECRET=<staging-only-secret>
```

### bKash Sandbox (API mode)

Credentials go in **website** `website_official_gateways.config_json` (per merchant), not only env:

```json
{
  "mode": "api",
  "appKey": "<sandbox-app-key>",
  "appSecret": "<sandbox-app-secret>",
  "username": "<sandbox-username>",
  "password": "<sandbox-password>",
  "callbackSecret": "<staging-callback-secret>",
  "previousCallbackSecret": ""
}
```

Optional env overrides:

```env
BKASH_API_BASE=https://tokenized.sandbox.bka.sh/v1.2.0-beta
BKASH_PING_URL=https://tokenized.sandbox.bka.sh/v1.2.0-beta/tokenized/checkout/token/grant
```

### Security Rule

- [ ] No production bKash credentials
- [ ] No production `DATABASE_URL`
- [ ] No production merchant `api_secret` copied blindly
- [ ] `PAYMENT_METRICS_API_KEY` unique to staging

---

## Stage-3: Health Check

After deploy, all probes must return healthy status.

### Endpoints

| Endpoint | Auth | Expected |
|----------|------|----------|
| `GET /health` | None | `{ "status": "UP", "env": "staging" }` |
| `GET /payment/health` | None | Redirects → `/api/v1/payment/health` |
| `GET /api/v1/payment/health` | None | `status: UP`, DB UP, Redis UP/DOWN+fallback |
| `GET /api/v1/payment/metrics` | `X-Payment-Metrics-Key` | Full dashboard (no secrets) |

### Example

```bash
curl -s https://staging.example/api/v1/payment/health | jq '{
  status, env,
  db: .checks.database.status,
  redis: .checks.redis.status,
  providers: [.checks.providers[].providerId],
  circuits: .checks.circuitBreakers
}'
```

### Pass Criteria

- [ ] `checks.database.status` = `UP`
- [ ] `checks.redis.status` = `UP` (or `DOWN` with documented memory fallback for chaos only)
- [ ] All providers `up: true`
- [ ] All circuit breakers `CLOSED`
- [ ] `outbox.pending` = 0 (no stuck rows after idle period)
- [ ] Metrics endpoint returns `401` without key
- [ ] Metrics endpoint returns `200` with valid key

---

## Stage-4: Functional QA (bKash Sandbox)

**Target: ২০–৩০ বাস্তব sandbox transactions.**

Configure one test merchant:

1. `gateway_layouts` — `callback_url` → webhook.site or your test receiver
2. `website_official_gateways` — `provider: bkash_merchant`, `mode: api`, sandbox credentials
3. Checkout live-init → browser redirect → complete payment in bKash sandbox app

### Scenario Matrix

| # | Scenario | Count | Pass Criteria |
|---|----------|------:|---------------|
| 1 | Success | 10 | Session `completed`, outbox `sent`, merchant callback ×1 |
| 2 | Cancel | 3 | Session `failed`, no outbox row |
| 3 | Expired | 3 | Session `expired`, callback returns PAY_1007 |
| 4 | Wrong amount | 2 | Rejected or FAILED (document actual behavior) |
| 5 | Delayed callback | 2 | SUCCESS after 30s+ delay, state intact |
| 6 | Duplicate callback | 3 | Merchant HTTP ×1 only |
| 7 | Invalid signature | 2 | PAY_1005, session unchanged |
| 8 | Gateway timeout | 2 | Circuit OPEN, graceful error |
| 9 | Merchant callback retry | 2 | Outbox retries → `sent` or `dead` logged |
| 10 | Redis restart | 1 | Payment completes, no duplicate SUCCESS |

### Per-Transaction Log Template

Record for each test:

```
Date:
TraceId:
SessionToken:
Scenario:
bKash TrxId:
Session Status:
Outbox Status:
Merchant Callback Count:
Result: PASS / FAIL
Notes:
```

---

## Stage-5: Database Validation

After each transaction, verify chain:

```
payment_sessions.status
    ↔ gateway result
merchant_callback_outbox.status (if SUCCESS)
    ↔ sent | pending | dead
audit_logs.event_type
    ↔ PaymentCompleted, outbox.enqueued, MerchantCallbackSent
```

### SQL Snippets

```sql
-- By session
SELECT session_token, status, trx_id, completed_at
FROM payment_sessions WHERE session_token = 'ps_...';

SELECT delivery_key, status, attempts, sent_at, last_error
FROM merchant_callback_outbox WHERE session_token = 'ps_...';

SELECT event_type, status, created_at, detail_json
FROM audit_logs WHERE entity_id = 'ps_...' ORDER BY created_at;
```

### Integrity Checks

- [ ] No duplicate `trx_id` per `website_id` for `completed` sessions
- [ ] No duplicate `delivery_key` in outbox
- [ ] Terminal sessions have `completed_at` set
- [ ] Expired sessions never reach `completed`

---

## Stage-6: Merchant Validation

On merchant test site / webhook receiver:

- [ ] Order marked paid exactly once
- [ ] Amount matches session amount (not client-tampered value)
- [ ] `traceId` in callback matches server logs
- [ ] `X-Paychek-Signature` verifies with merchant `api_secret`
- [ ] MerchantCallbackV1 schema valid
- [ ] Commission field = 0 (Phase-3C not enabled yet)

---

## Stage-7: Chaos Testing

Run all scenarios in `CHAOS_TESTING.md`:

| # | Scenario | Required |
|---|----------|----------|
| 1 | Redis down | ✅ |
| 2 | DB latency | ✅ |
| 3 | Gateway timeout | ✅ |
| 4 | Worker crash | ✅ |
| 5 | Restart during callback | ✅ |
| 6 | Merchant callback down | ✅ |
| 7 | Duplicate trx_id | ✅ |
| 8 | Expired session | ✅ |
| 9 | Invalid signature | ✅ |
| 10 | 10× duplicate callback | ✅ |

Each scenario: record **expected vs actual** in a sign-off sheet.

---

## Stage-8: Performance Baseline

Capture from metrics after QA (save for regression):

```bash
curl -s -H "X-Payment-Metrics-Key: $KEY" \
  https://staging.example/api/v1/payment/metrics > staging-baseline-$(date +%Y%m%d).json
```

Record:

| Metric | Staging Value | Date |
|--------|---------------|------|
| Init latency p50 / p95 | | |
| Callback latency p50 / p95 | | |
| Merchant callback p50 / p95 | | |
| Retry dead queue max | | |
| Outbox dead count | | |
| Circuit breaker opens | | |

---

## Stage-9: Security Audit

- [ ] Grep staging logs: no `appSecret`, `password`, `callbackSecret` in plaintext
- [ ] `trace-logger` redacts secrets (run one payment, inspect stdout)
- [ ] Metrics without auth → `401`
- [ ] Metrics with wrong key → `401`
- [ ] Callback rate limiter triggers at 429 after burst (`PAY_1020` if configured)
- [ ] No raw bKash credentials in `audit_logs.detail_json`

---

## Stage-10: Release Decision

**Proceed only when PAG passes** — see `PRODUCTION_ACCEPTANCE_GATE.md`.

**All PASS:**

```
✅ staging:preflight PASS
✅ Functional QA (20–30 sandbox tx)
✅ Chaos tests (10/10)
✅ Database integrity verified
✅ Merchant callback ×1 per payment
✅ Security audit
✅ Performance baseline saved
✅ PAG 5/5 (Functional, Reliability, Operational, Business, Security)
✅ Release Evidence Package created
```

### Then

```
feature/payment-phase-3b-bkash
    ↓
Pull Request (payment module scope only)
    ↓
Code Review
    ↓
Production Pilot (gradual rollout)
```

---

## Production Pilot Rollout

Do **not** enable for all merchants at once.

```
1 merchant  (48h monitor)
    ↓
3 merchants (72h monitor)
    ↓
10 merchants
    ↓
Full rollout
```

Rollback trigger: duplicate callbacks, circuit stuck OPEN, outbox dead queue growth, merchant SLA breach.

---

## After Staging PASS → Phase-3C

Commission Engine (Registry-driven):

```
Provider Registry
    ↓
Payment Type
    ↓
Rule Resolver
    ↓
Commission Calculator
    ↓
Normalized Result
    ↓
MerchantCallbackV1 (commission field populated)
```

---

## Related Docs

| Doc | Purpose |
|-----|---------|
| `GOLDEN_TEST_SUITE.md` | 12 repeatable golden tests |
| `PRODUCTION_ACCEPTANCE_GATE.md` | PAG + Phase-3C exit criteria |
| `OPERATIONS_POLICY.md` | Change freeze + incident severity |
| `RELEASE_EVIDENCE_PACKAGE.md` | Per-release evidence bundle |
| `PRODUCTION_GATE_EVIDENCE.md` | Mandatory evidence per gate |
| `PRODUCTION_KPIS.md` | Health score + thresholds |
| `PRODUCTION_RUNBOOK.md` | Incidents, rollback, recovery |
| `RELEASE_STRATEGY.md` | v3.0 → v4.0 roadmap |
| `DOCUMENTATION_FREEZE.md` | v1.0 frozen contracts |
| `PHASE-3B5_PRODUCTION_HARDENING.md` | Hardening scope |
| `CHAOS_TESTING.md` | Chaos scenario details |
| `PRODUCTION_GATE.md` | Automated local gate |
| `LIFECYCLE_SPEC.md` | State machine |
| `CALLBACK_VERSIONING.md` | Merchant callback versions |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial Phase-3B.6 staging gate |
