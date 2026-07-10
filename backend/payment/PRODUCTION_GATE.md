# Phase-3B Production Gate

Run before commit, PR, or production deploy:

```bash
cd backend
npm run test:payment-gate
```

Exit code `0` = all **automated** gates passed.  
Exit code `1` = fix failures before proceeding.

## Automated Coverage

| Gate | What it verifies |
|------|------------------|
| Unit Tests | State machine, idempotency, normalize, signature, PAY_1005 |
| E2E Success | Session SUCCESS, traceId, MerchantCallbackV1, merchantCb=1 |
| E2E Cancel | FAILED, no merchant callback |
| E2E Expired | PAY_1007 |
| E2E Duplicate (10×) | merchantCb=1 |
| E2E Callback Delay | REDIRECTED → SUCCESS after delay |
| E2E Invalid Signature | PAY_1005 |
| Restart Recovery | Session persisted in DB |
| Load 100/300/500 | Idempotency concurrent ops |
| Backward Compat | template mode without signature |
| Security | Log redaction (no secrets in stdout) |
| Observability | Single traceId across stages |
| Failure Injection | Merchant URL down → dead queue |

## Manual (required before production)

See **`STAGING_DEPLOY_GATE.md`** (Phase-3B.6) for full step-by-step staging validation.

- [ ] `npm run staging:preflight` on staging server
- [ ] Staging deploy
- [ ] 20–30 real **bKash sandbox** transactions (API mode)
- [ ] Full browser redirect + return URL on staging
- [ ] Merchant callback received on real webhook URL
- [ ] `CHAOS_TESTING.md` — all 10 scenarios on staging
- [ ] Performance baseline captured from `/api/v1/payment/metrics`

## Environment

- `GATE_TEST_WEBSITE_ID` — defaults to `7`
- `REDIS_OP_TIMEOUT_MS` — defaults to `500` (memory fallback if Redis slow)
