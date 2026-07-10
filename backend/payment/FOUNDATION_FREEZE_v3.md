# Foundation Freeze — v3.0.0

> **Status:** FROZEN  
> **Freeze date:** 2026-07-06  
> **Git tag:** `v3.0.0-payment-foundation` (+ Phase-3B/3B.5 on branch `feature/payment-phase-3b-bkash`)  
> **Exit condition:** Pilot PASS + PAG 5/5 + `PRODUCTION_ACCEPTANCE_GATE.md` Exit Criteria

---

## Foundation Version

```text
v3.0.0 — Payment Platform Foundation
```

This document is the **baseline** for all future payment work. When someone asks *"what state did we freeze?"* — this file is the answer.

---

## Scope

Payment Platform Foundation:

- Checkout integration boundary (UI frozen)
- Provider Registry + Adapter pattern
- Payment Engine + Session Engine
- Callback pipeline (gateway → state → outbox → merchant)
- MerchantCallbackV1 contract
- Event bus + frozen event names
- State machine + error code registry
- Hardening (outbox, idempotency, circuit breaker, metrics)
- Governance + operational gates

**Out of scope (post-exit):** Commission (Phase-3C), multi-provider expansion (v3.2.0), ops dashboard (v3.3.0), HA (v4.0.0).

---

## Frozen Modules

| Module | Path / doc | Change rule |
|--------|------------|-------------|
| Checkout UI | `public/js/checkout/**` | No change — ADR-001 |
| Provider Registry | `payment/registry/` | New provider = new adapter only |
| Payment Engine | `payment/engine/` | Bugfix only during freeze window |
| Session Engine | `payment/session/` | Bugfix only |
| Callback Contract | `core/merchant-callback-v1.js` | Version bump only — ADR-004 |
| PaymentContext v1 | `core/payment-context-v1.js` | `metadata.custom` extensions only |
| Event Names | `events/EVENTS_FROZEN.md` | Additive only |
| State Machine | `LIFECYCLE_SPEC.md`, `state/` | Terminal states immutable |
| Error Codes | `errors/error-codes.js` | Add-only, never renumber |
| Adapter Interface | `providers/base-provider.js` | PR + ADR for breaking change |

Full list: `DOCUMENTATION_FREEZE.md`

---

## Allowed Changes (during freeze)

- Critical bug fix (P0/P1) with regression evidence
- Security patch
- Documentation / runbook / governance updates
- Monitoring / logging improvements (**no behavior change**)
- Staging / pilot evidence collection (ops artifacts)

---

## Not Allowed (during freeze)

- Architecture refactor
- Breaking contract change (callback, context, events)
- Database schema change (emergency + approved migration only)
- New API shape or route semantics
- Phase-3C Commission code
- New provider adapters (until v3.2.0 gate)
- Checkout UI changes

See `OPERATIONS_POLICY.md` for enforcement.

---

## No Architecture Change Window (Pilot)

> **Until Pilot completes with GO decision, architecture refactor is forbidden.**

During **Staging Validation → Pilot → Retrospective**, only these four change types are permitted:

1. Critical bug fix  
2. Security fix  
3. Monitoring / logging improvement (no behavior change)  
4. Documentation update  

**Rationale:** Pilot results must reflect a **stable system**, not a moving target.

Pilot end = `PRODUCTION_ACCEPTANCE_GATE.md` signed + `certificates/PILOT_LESSONS_LEARNED.md` completed.

---

## Exit Condition (unfreeze path to Phase-3C)

Foundation freeze **lifts for new feature work** only when:

| # | Criterion |
|---|-----------|
| 1 | Golden Test Suite 12/12 + evidence |
| 2 | Sandbox ≥ 20 transactions + evidence |
| 3 | Chaos 10/10 PASS |
| 4 | Pilot 48–72h stable |
| 5 | PAG 5/5 PASS |
| 6 | Pilot lessons learned (`certificates/PILOT_LESSONS_LEARNED_TEMPLATE.md` → `releases/<pilot>/`) |
| 7 | GO decision documented in release evidence package |
| 8 | `certificates/PHASE_3B.md` marked Complete |

Then: **Phase-3C** (Registry-driven Commission Engine) may begin — see `PRODUCTION_ACCEPTANCE_GATE.md`.

---

## Related Documents

| Doc | Purpose |
|-----|---------|
| `README.md` | Onboarding entry point |
| `DECISIONS.md` | ADRs (why baseline exists) |
| `OPERATIONS_POLICY.md` | Change freeze enforcement |
| `DOCUMENTATION_FREEZE.md` | Contract freeze v1.0 |
| `PRODUCTION_ACCEPTANCE_GATE.md` | Exit gates |
| `DEFINITION_OF_DONE.md` | Done criteria |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | v3.0.0 Foundation Freeze declared |
