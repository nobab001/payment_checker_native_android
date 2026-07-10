# Final Production Gate — Evidence Required

> **Rule:** No gate is PASS without **evidence**. "PASS" text alone is not acceptable.  
> **Phase:** After Staging deploy, before Production Pilot expansion.  
> **Prerequisite:** `STAGING_DEPLOY_GATE.md` Stages 1–3 complete.

---

## Mandatory Evidence Matrix

| Gate | Evidence Required | Where to Capture |
|------|-------------------|------------------|
| Sandbox Payment | bKash trxId + `traceId` + screenshot (redirect + success page) | QA sheet / ticket |
| Merchant Callback | Full callback JSON body + HTTP 200 response headers | webhook.site export or test receiver log |
| Payment Session | `payment_sessions` row (status, trx_id, completed_at) | SQL export or screenshot |
| Audit Log | `audit_logs` row(s) for same `entity_id` | SQL query result |
| Outbox | `merchant_callback_outbox`: `pending` → `sent` (or `dead` with reason) | SQL + timestamp |
| Metrics | p50/p95 snapshot JSON from `/api/v1/payment/metrics` | `staging-baseline-YYYYMMDD.json` |
| Trace | Same `traceId` in: Engine → Session → Callback → Outbox → Merchant | Log grep / audit detail |
| Retry | Retry attempt count from logs or outbox `attempts` | `trace-logger` / outbox row |
| Duplicate Callback | Merchant HTTP call count = **exactly 1** per payment | Mock server count / webhook log |
| Rollback | Rollback test log (steps taken, time, outcome) | Incident-style write-up |

---

## Per-Transaction Evidence Template

Copy one row per golden test or sandbox transaction:

```
Test ID:
Date (BST):
Scenario:
Session Token:
Trace ID:
bKash TrxId:
payment_sessions.status:
outbox.status (sent_at / attempts / last_error):
audit_logs (event_types):
Merchant callback count:
Merchant HTTP status:
Metrics snapshot file:
Screenshot / log path:
Result: PASS | FAIL
Reviewer:
Notes:
```

---

## Sign-off Checklist

Before declaring **Staging PASS** or starting **Production Pilot**:

- [ ] Golden Test Suite: 12/12 with evidence (`GOLDEN_TEST_SUITE.md`)
- [ ] 20–30 sandbox transactions with evidence rows filled
- [ ] Chaos: 10/10 with expected vs actual (`CHAOS_TESTING.md`)
- [ ] Performance baseline saved (`PRODUCTION_KPIS.md`)
- [ ] Rollback drill completed (`PRODUCTION_RUNBOOK.md` § Rollback)
- [ ] `npm run staging:preflight` PASS on staging server
- [ ] No open P0/P1 payment bugs

**Sign-off:**

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Engineering | | | |
| QA / Validator | | | |
| Operations | | | |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial evidence gate |
