# Phase-3B.5 — Production Hardening

> **Status:** COMPLETE (code) — pending Staging QA  
> **Prerequisite:** Phase-3B `bkash_live` lifecycle implemented  
> **Not production-ready until:** Staging bKash sandbox QA + chaos tests PASS

---

## Purpose

Phase-3B delivered architecture and `bkash_live` lifecycle.  
Phase-3B.5 makes the payment layer **operationally production-grade** without changing Checkout UI or breaking Merchant Callback V1.

---

## Scope Lock

| In scope | Out of scope |
|----------|--------------|
| Circuit breaker (bKash API) | Phase-3C Commission |
| Outbox table + worker | New providers (SSLCommerz…) |
| Session expiry cleanup worker | Checkout UI changes |
| Secret rotation (`current` + `previous`) | Type callback |
| Metrics / health dashboard API + auth | Android |
| Immutable audit via Event Bus | |
| Callback rate limiter | |
| DB unique constraints | |
| Callback versioning guidelines | |
| Chaos testing spec | |

---

## 1. Three-Layer Idempotency (Webhook)

```
Layer 1: Redis IdempotencyManager (lock / complete)
Layer 2: Database duplicate guard + UNIQUE (website_id, trx_id)
Layer 3: State Machine (PaymentSessionEngine)
Layer 4: Outbox delivery_key UNIQUE (session + url)
```

**Rule:** Same `providerTransactionId` + `websiteId` cannot SUCCESS twice on different sessions.

---

## 2. Outbox Pattern (Merchant Callback) — COMPLETE

```
Payment SUCCESS
    ↓
Prisma $transaction (session complete + outbox insert)
    ↓
merchant_callback_outbox (pending)
    ↓
Worker (cron 15s) + inline processOutboxBatch
    ↓
Merchant Callback (RetryEngine)
    ↓
sent | dead
```

**Files:**
- `payment/reliability/merchant-callback-outbox.js`
- `payment/workers/outbox-worker.js`
- `db/migrations/add_payment_hardening_3b5.sql`

---

## 3. Session Expiry Cleanup

Cron every 5 minutes → `session-cleanup-worker.js`

---

## 4. Secret Rotation

`config_json`: `callbackSecret` + `previousCallbackSecret`

---

## 5. Health Monitoring + Auth

`GET /api/v1/payment/metrics`

**Auth:** `X-Payment-Metrics-Key` or admin Bearer JWT  
**Optional:** `PAYMENT_METRICS_IP_WHITELIST`  
**Rate limit:** 30/min (configurable)

Response excludes secrets; includes `outbox.pending`, `outbox.dead`.

---

## 6. Circuit Breaker

Per provider (`bkash_live`) on token grant API.

---

## 7. Audit Log (Business)

Event Bus → `audit_logs` (immutable append-only).

---

## 8. Callback Versioning

See `CALLBACK_VERSIONING.md`.

---

## Chaos Testing

See `CHAOS_TESTING.md` — run on staging before Phase-3C.

---

## Entry Checklist (before Phase-3C)

- [ ] Run migration: `add_payment_hardening_3b5.sql` or `npx prisma db push`
- [ ] Staging: 20–30 bKash sandbox transactions
- [ ] Chaos scenarios 1–10 PASS (`CHAOS_TESTING.md`)
- [ ] `npm run test:payment-gate` PASS
- [ ] Session cleanup cron running
- [ ] Outbox worker cron running
- [ ] Metrics endpoint auth verified
- [ ] Secret rotation tested on staging

---

## Priority-2 (Post-3B.5 / Phase-4)

- Dead Letter Queue admin UI
- Real-time metrics dashboard
- Trace explorer by `traceId`

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 0.1 | 2026-07-06 | Initial spec |
| 1.0 | 2026-07-06 | Outbox + DB constraints + metrics auth complete |
