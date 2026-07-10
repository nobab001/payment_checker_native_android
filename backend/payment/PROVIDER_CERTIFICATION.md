# Provider Certification Checklist

> A provider is **NOT production-ready** until every item below is checked.  
> Phase-3B certifies **`bkash_live` only** first.

---

## Registry & Contract

- [ ] Registry entry in `provider-registry.js` (id, aliases, dbProviderKey)
- [ ] `priority`, `enabled`, `maintenance` configured
- [ ] Version contract set (`adapterVersion`, `contractVersion`, `providerApiVersion`)
- [ ] `callbackPath` defined
- [ ] Registry validator passes (`provider-validator.js`)

## Adapter

- [ ] Adapter file extends `BaseProvider`
- [ ] `initialize()` — credential / config validation
- [ ] `createPayment()` — provider intent (no DB writes)
- [ ] `getRedirectUrl()` — customer redirect
- [ ] `verify()` — payment status with gateway API
- [ ] `normalize()` — raw → Merchant Callback v1.0
- [ ] `callback()` — inbound webhook handler
- [ ] `health()` — internal adapter state
- [ ] `ping()` — gateway API reachability

## Reliability

- [ ] Idempotency on callback (`idempotency-manager` check/lock/complete)
- [ ] State machine transitions validated (`payment-state-machine`)
- [ ] Merchant callback uses `RetryEngine` (3 attempts → dead queue)
- [ ] Signature verification (if provider supports it)

## Observability

- [ ] `traceId` propagated through full lifecycle
- [ ] Structured logs at Engine / Provider / Session / Redirect / Callback stages
- [ ] Events emitted for full timeline (see `EVENTS_FROZEN.md`)
- [ ] Monitoring probes registered (`payment-monitor`)

## Documentation

- [ ] Provider-specific notes in adapter file header
- [ ] Callback payload mapping documented
- [ ] Error codes mapped in `errors/error-registry.js`
- [ ] Checklist signed off in PR description

---

## Phase-3B Target: `bkash_live`

| Item | Status |
|------|--------|
| Registry Entry | ✅ |
| Adapter stub | ✅ |
| Health / Ping stub | ✅ |
| Verify | ✅ template + api stub |
| Normalize | ✅ |
| Callback | ✅ |
| Retry | ✅ MerchantCallbackV1 |
| Full lifecycle E2E | ⬜ manual QA |

**Do not enable other LIVE providers for production until `bkash_live` row is 100%.**
