# Production Runbook — Payment Platform v3.0

> **Audience:** Engineering + Operations  
> **Scope:** `bkash_live` payment layer (Phase-3B)  
> **Pair with:** `PRODUCTION_KPIS.md`, `PRODUCTION_GATE_EVIDENCE.md`

---

## 1. Service Start / Stop

### Start (staging / production)

```bash
cd backend
export APP_ENV=staging   # or production
export NODE_ENV=production
npm run db:hardening-3b5   # first deploy only
npm run staging:preflight  # verify before traffic
npm start                  # or PM2: pm2 start app.js --name paychek-api
```

### Stop

```bash
pm2 stop paychek-api
# or SIGTERM to node process — allow in-flight callbacks to finish (~30s)
```

### Verify after start

```bash
curl -s https://HOST/health
curl -s https://HOST/api/v1/payment/health | jq '{status, db: .checks.database, redis: .checks.redis}'
```

---

## 2. Incident Declaration

Declare **payment incident** if any:

- Duplicate merchant callback (same session, HTTP > 1)
- Payment Success Rate < 90% for 1 hour
- Outbox pending > 50 for 15 minutes
- Circuit breaker OPEN > 15 minutes
- Data corruption (session `completed` without matching trx_id)

**Notify:** Engineering lead + affected merchants (pilot list).

---

## 3. Rollback Procedure

### Application rollback

```bash
# Revert to previous known-good tag
git checkout v3.0.0-payment-foundation   # or last pilot-approved tag
cd backend && npm ci && npm start
```

### Feature rollback (pilot merchant)

1. Disable official gateway for website: `website_official_gateways.is_active = 0`
2. Merchant falls back to legacy checkout (if configured)
3. Document in rollback log (required evidence)

### Rollback test (before production)

- Deploy previous tag on staging
- Run G01 from `GOLDEN_TEST_SUITE.md`
- Record: time, version, result → `PRODUCTION_GATE_EVIDENCE.md`

---

## 4. Redis Down

**Symptoms:** `checks.redis.status = DOWN` in `/api/v1/payment/health`; logs show `REDIS_TIMEOUT`.

**Behavior:** Idempotency falls back to in-memory (single instance only).

**Actions:**

1. Restore Redis: `systemctl start redis` (or managed service)
2. Verify: `redis-cli ping` → PONG
3. Monitor duplicate callbacks for 1 hour
4. **Do not** run multiple API instances without Redis

---

## 5. Database Down / Slow

**Symptoms:** Health 503; slow callbacks; outbox stuck `processing`.

**Actions:**

1. Check MySQL connectivity / disk / connections
2. Kill long queries if needed (staging: document query)
3. Reclaim stale outbox locks (> 5 min processing): worker auto-reclaims
4. Verify no duplicate `completed` sessions:

```sql
SELECT website_id, trx_id, COUNT(*) c
FROM payment_sessions
WHERE status = 'completed' AND trx_id IS NOT NULL
GROUP BY website_id, trx_id HAVING c > 1;
```

---

## 6. Payment Queue Stuck (Outbox)

**Symptoms:** `outbox.pending` growing; merchants not receiving callbacks.

**Actions:**

1. Check worker cron running (logs: `[OUTBOX] processed`)
2. Inspect stuck rows:

```sql
SELECT id, session_token, status, attempts, last_error, locked_at
FROM merchant_callback_outbox
WHERE status IN ('pending','processing')
ORDER BY created_at LIMIT 20;
```

3. If merchant URL down → rows go `dead` after 5 attempts — notify merchant
4. Manual retry (ops): fix URL → set `status='pending'`, `locked_at=NULL` → worker picks up
5. **Never** delete outbox rows without audit trail

---

## 7. Circuit Breaker OPEN (bKash API)

**Symptoms:** `circuitBreakers.bkash_live.state = OPEN`; init/redirect errors.

**Actions:**

1. Check bKash sandbox/production status
2. Wait cooldown (~10 min) or fix credentials in `config_json`
3. Probe: `GET /api/v1/payment/health` until `CLOSED`
4. Do not force-open without root cause analysis

---

## 8. Duplicate Callback Investigation

1. Get `session_token` + `traceId` from merchant report
2. Grep logs: `session.already.completed`, `Idempotency`
3. Count merchant webhook deliveries
4. Check outbox: should be one `delivery_key` per URL, `sent` once
5. If duplicate confirmed → **P0** — pause pilot merchant, rollback if needed

---

## 9. Key Endpoints

| Endpoint | Auth | Use |
|----------|------|-----|
| `GET /health` | None | LB probe |
| `GET /api/v1/payment/health` | None | Payment readiness |
| `GET /api/v1/payment/metrics` | `X-Payment-Metrics-Key` | KPIs |
| `POST /api/payment/bkash/callback` | Signature | Gateway only |

---

## 10. Escalation Contacts

| Role | Contact | When |
|------|---------|------|
| Engineering | _fill_ | P0 payment bug |
| bKash support | _fill_ | API outage |
| Merchant liaison | _fill_ | Callback SLA |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial runbook for v3.0 pilot |
