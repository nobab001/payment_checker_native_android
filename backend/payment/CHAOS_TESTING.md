# Phase-3B.5 — Chaos Testing (Staging)

> Run on **staging** with real bKash sandbox credentials.  
> Record `traceId`, session token, and timestamps for each scenario.

---

## Preconditions

- Staging DB migrated (`merchant_callback_outbox`, `uniq_website_trx`)
- `PAYMENT_METRICS_API_KEY` set; metrics endpoint reachable
- Outbox worker cron active (or manual `runOutboxWorker()`)
- Redis available (idempotency)
- Test merchant with `callback_url` pointing to webhook.site or internal mock

---

## Scenario Matrix

| # | Scenario | Injection | Expected Result |
|---|----------|-----------|-----------------|
| 1 | **Redis down** | Stop Redis / block port | Callback still processes via memory idempotency fallback; no duplicate SUCCESS; logs show idempotency fallback |
| 2 | **DB latency** | `tc qdisc` +500ms on DB host | Callback completes within timeout; session eventually `completed`; outbox row `sent` or `pending` (worker retries) |
| 3 | **Gateway timeout** | Block bKash API egress | Circuit breaker → `OPEN`; redirect/init returns maintenance/unavailable; no partial SUCCESS |
| 4 | **Worker crash** | Kill process after outbox insert, before HTTP | Outbox row stays `pending`; restart → worker delivers; merchant receives exactly one callback |
| 5 | **Restart during callback** | Restart mid `processBkashCallback` | Idempotency lock prevents double complete; duplicate gateway callback → `session.already.completed` |
| 6 | **Merchant callback down** | Point callback URL to closed port | Outbox retries → `dead` after max attempts; `retryDeadQueue` / outbox.dead > 0; payment still `completed` |
| 7 | **Duplicate trx_id** | Two sessions, same bKash trxId | Second session rejected: `PAY_1006` + DB unique `uniq_website_trx` |
| 8 | **Expired session** | Callback after `expires_at` | `PAY_1007`; session `expired`; no outbox row |
| 9 | **Invalid signature** | Wrong HMAC | `PAY_1005`; session unchanged |
| 10 | **10× duplicate callback** | Replay same success payload | Merchant HTTP called once; outbox `delivery_key` unique |

---

## Detailed Procedures

### 1. Redis Down

```bash
# staging
sudo systemctl stop redis
# Run one successful sandbox payment end-to-end
sudo systemctl start redis
```

**Pass criteria:**
- Payment status = `completed`
- Merchant callback received once
- No second SUCCESS on duplicate replay

---

### 2. DB Latency

```bash
# Linux traffic control (example)
sudo tc qdisc add dev eth0 root netem delay 500ms
# Run payment + callback
sudo tc qdisc del dev eth0 root
```

**Pass criteria:**
- No orphaned `created` sessions after 10 min
- Outbox eventually `sent`

---

### 3. Gateway Timeout

Block `tokenized.pay.bka.sh` (or sandbox host) via firewall for 15 minutes.

**Pass criteria:**
- Circuit breaker state = `OPEN` in `GET /api/v1/payment/metrics`
- New init/redirect does not hang > 30s
- After unblock + cooldown, breaker returns `CLOSED`

---

### 4. Worker Crash

1. Complete payment in sandbox (SUCCESS in gateway)
2. Immediately `kill -9` Node process after DB shows outbox `pending`
3. Restart server

**Pass criteria:**
- Outbox row transitions `pending` → `sent`
- Merchant received callback after restart

---

### 5. Restart During Callback

1. Start callback request
2. `kill -9` mid-flight
3. Replay same callback from gateway

**Pass criteria:**
- Final state: one `completed` session
- Merchant callback count = 1

---

### 6. Merchant Callback Down

Set merchant `callback_url` to `http://127.0.0.1:9/` on staging test site.

**Pass criteria:**
- Session `completed`
- Outbox → `dead` after 5 attempts
- Audit: `outbox.merchant_callback.dead`

---

## Metrics Verification

```bash
curl -s -H "X-Payment-Metrics-Key: $PAYMENT_METRICS_API_KEY" \
  https://staging.example/api/v1/payment/metrics | jq .
```

**Expect:** `outbox.pending`, `outbox.dead`, `circuitBreakers`, latencies — no secrets in response.

---

## Sign-off Checklist

- [ ] All 10 chaos scenarios PASS on staging
- [ ] 20–30 real bKash sandbox transactions (success/cancel/delay/duplicate)
- [ ] `npm run test:payment-gate` PASS locally
- [ ] No duplicate merchant callbacks in webhook logs
- [ ] Phase-3B.5 entry checklist in `PHASE-3B5_PRODUCTION_HARDENING.md` complete

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial chaos matrix for staging QA |
