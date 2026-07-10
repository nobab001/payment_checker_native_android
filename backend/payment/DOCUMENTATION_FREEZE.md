# Documentation Freeze — Payment Platform v1.0

> **Baseline declaration:** `FOUNDATION_FREEZE_v3.md` (v3.0.0 FROZEN)  
> **Effective:** 2026-07-06  
> **Tag baseline:** `v3.0.0-payment-foundation` + Phase-3B/3B.5/3B.6 docs  
> **Rule:** Frozen contracts change only via version bump (V1.1 additive, V2 breaking).  
> **Lock:** No frozen contract change **without Pull Request** — see `OPERATIONS_POLICY.md` §2.

---

## Frozen Artifacts (v1.0)

| Document / Module | Path | Change Policy |
|-------------------|------|---------------|
| PaymentContext v1 | `core/payment-context-v1.js` | Extensions via `metadata.custom` only |
| MerchantCallbackV1 | `core/merchant-callback-v1.js` | See `CALLBACK_VERSIONING.md` |
| Provider Registry Contract | `registry/`, `CONTRACT.md` | New providers = new adapter |
| Adapter Interface | `providers/base-provider.js` | Minor methods only with ADR |
| Error Code Registry | `errors/error-codes.js` | Add only, never renumber |
| Event Names | `events/EVENTS_FROZEN.md` | Additive events only |
| State Machine | `LIFECYCLE_SPEC.md`, `state/` | Terminal states immutable |
| Staging Deploy Gate | `STAGING_DEPLOY_GATE.md` | Ops updates allowed |
| Chaos Testing | `CHAOS_TESTING.md` | Scenario additions ok |
| Golden Test Suite | `GOLDEN_TEST_SUITE.md` | New tests = minor version |
| Production Gate Evidence | `PRODUCTION_GATE_EVIDENCE.md` | Ops updates allowed |
| Production KPIs | `PRODUCTION_KPIS.md` | Threshold tuning ok |
| Production Runbook | `PRODUCTION_RUNBOOK.md` | Ops updates required |
| Production Gate (automated) | `PRODUCTION_GATE.md` | Test additions ok |
| ADRs | `DECISIONS.md` | Add new ADR; do not delete |
| Deprecation policy | `DEPRECATION_POLICY.md` | Ops timeline updates ok |
| Definition of Done | `DEFINITION_OF_DONE.md` | Per-phase criteria |
| Risk register | `RISK_REGISTER.md` | Update after incidents |

---

## Not Frozen (active development after pilot)

| Area | Starts After |
|------|--------------|
| Commission Engine (Phase-3C) | Pilot stable 48–72h |
| Operations Dashboard (Phase-4) | Post-3C or parallel ops need |
| Multi-provider adapters (Phase-5) | v3.2.0 roadmap |
| HA / clustering (Phase-6) | v4.0.0 roadmap |

---

## Release Documentation Map

See `RELEASE_STRATEGY.md` for version tags.

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial documentation freeze declaration |
