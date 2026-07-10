# Golden Test Suite — Payment Platform v3.0

> **Purpose:** Repeatable release regression pack. Run before every pilot expansion and production release.  
> **Rule:** Each test requires evidence per `PRODUCTION_GATE_EVIDENCE.md`.  
> **Environment:** Staging with **bKash sandbox** (API mode) + real browser redirect.

---

## When to Run

- Before Production Pilot start
- Before each pilot expansion (1 → 3 → 10 → 25 → all)
- Before tagging `v3.0.1`, `v3.1.0`, etc.
- After any payment-layer hotfix

---

## Golden Tests (12)

| # | Test | Steps (summary) | Expected | Evidence |
|---|------|-----------------|----------|----------|
| G01 | Successful Payment | live-init → redirect → sandbox pay → callback | `payment_sessions.status=completed`, outbox `sent`, merchant ×1 | trxId, traceId, screenshot |
| G02 | User Cancel | Start pay → cancel in bKash | `status=failed`, no outbox row | DB row |
| G03 | Expired Session | Wait past `expires_at` → callback | `EXPIRED`, PAY_1007 | audit + session |
| G04 | Callback Delay | Complete pay; delay gateway callback 30–60s | SUCCESS, state intact | timestamps |
| G05 | Duplicate Callback | Replay same success callback 10× | Merchant HTTP ×1 | mock count |
| G06 | Invalid Signature | Wrong HMAC on callback | PAY_1005, session unchanged | error response |
| G07 | Wrong Amount | Tamper amount in callback vs session | Reject / FAILED (document actual) | logs |
| G08 | Network Timeout | Block merchant URL briefly | Outbox retry → `sent` or `dead` | outbox attempts |
| G09 | Merchant Down | Callback URL closed port | Outbox retries, payment still `completed` | outbox `dead` ok |
| G10 | Redis Down | Stop Redis → one success payment | Completes, memory idempotency, no dup SUCCESS | redis log |
| G11 | DB Slow | Add latency (staging only) | No corruption, eventual `sent` | session + outbox |
| G12 | Browser Refresh | Refresh during redirect flow | Session preserved, pay can complete | session token same |

---

## Execution Order

```
G01 (baseline success) first
    → G02, G03 (terminal failures)
    → G05, G06 (idempotency / security)
    → G04, G07, G08, G09 (edge cases)
    → G10, G11 (chaos — staging only)
    → G12 (UX)
```

---

## Pass Criteria

- **12/12 PASS** with evidence attached
- Zero duplicate merchant callbacks across G01 + G05
- All G01 traceIds found in audit_logs
- Metrics snapshot taken after full run

---

## SQL Quick Verify (after G01)

```sql
SELECT session_token, status, trx_id, completed_at
FROM payment_sessions WHERE session_token = '<token>';

SELECT delivery_key, status, attempts, sent_at
FROM merchant_callback_outbox WHERE session_token = '<token>';

SELECT event_type, status, created_at
FROM audit_logs WHERE entity_id = '<token>' ORDER BY created_at;
```

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial golden pack for v3.0 pilot |
