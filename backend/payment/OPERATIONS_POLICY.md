# Operations Policy — Payment Platform v3.0

> **Effective:** Phase-3B.6 onward  
> **Pair with:** `PRODUCTION_ACCEPTANCE_GATE.md`, `DOCUMENTATION_FREEZE.md`, `PRODUCTION_RUNBOOK.md`

---

## 1. Change Freeze Policy (Phase-3B.6 → Pilot complete)

### 🔒 Frozen (no change without PAG exception + PR)

See **`FOUNDATION_FREEZE_v3.md`** for baseline declaration.

During **Pilot window** (until GO + lessons learned): **architecture refactor forbidden.** Only critical bugfix, security patch, monitoring/logging (no behavior change), documentation.

| Area | Paths / artifacts |
|------|-------------------|
| Checkout UI | `backend/public/js/checkout/**` |
| Payment contracts | `payment/CONTRACT.md`, `LIFECYCLE_SPEC.md` |
| Callback schema | `core/merchant-callback-v1.js` |
| PaymentContext | `core/payment-context-v1.js` |
| Event names | `events/EVENTS_FROZEN.md` |
| Error codes | `errors/error-codes.js` (add-only) |
| Provider registry schema | `registry/` |
| Adapter interface | `providers/base-provider.js` |

### ✅ Allowed during freeze

- Critical bug fix (P0/P1) with evidence regression test
- Security patch
- Performance optimization (**no behavior change**)
- Documentation / runbook updates
- Monitoring / metrics improvements (no contract change)

### ❌ Not allowed during freeze

- New API shape or breaking route change
- Callback field removal or semantic change
- Database schema change (emergency + approved migration only)
- Provider contract change without version bump
- Phase-3C Commission code
- New provider adapters

**Emergency break-glass:** Engineering lead + documented incident ID required.

---

## 2. Documentation Lock — PR Required

> **No frozen contract may change without a Pull Request.**

| Contract | Change rule |
|----------|-------------|
| PaymentContext v1 | v1.1 additive via `metadata` only; v2 = new module |
| MerchantCallbackV1 | See `CALLBACK_VERSIONING.md` |
| Event names | Additive only; rename = new event |
| Error codes | Add at end; never renumber |
| Provider registry | New provider = new adapter file |
| Adapter interface | Breaking change = adapter major version |

PR must include:

- [ ] Why change is needed
- [ ] Backward compatibility impact
- [ ] Golden test re-run evidence
- [ ] Version bump note in `DOCUMENTATION_FREEZE.md` if contract changes

---

## 3. Incident Severity Matrix

Unified classification for pilot and production. **Action** + **Response SLA**.

| Severity | Examples | Action | Response SLA |
|----------|----------|--------|--------------|
| **P0 Critical** | Wrong payment amount settled; duplicate settlement; data corruption; payment completely down | **Immediate rollback**; pause all pilot merchants | Immediate (< 15 min ack) |
| **P1 High** | Merchant callback failing; session loss; outbox dead spike; duplicate callback | **Pilot pause**; hotfix branch | < 30 min |
| **P2 Medium** | Retry delay; metrics endpoint down; circuit flapping; elevated p95 | Hotfix; monitor 24h | < 4 hours |
| **P3 Low** | Log format; non-payment UI; doc typo | Next release | Next sprint |

### P0 examples (rollback)

- Same `trx_id` completes two sessions
- Merchant receives callback twice for one payment
- Session `completed` with wrong amount vs bKash

### P1 examples (pilot pause)

- Merchant callback success < 95% for 1 hour
- Outbox pending > 50 for 15 minutes

### Escalation

1. Classify severity in incident ticket
2. Follow `PRODUCTION_RUNBOOK.md` playbook
3. Record in Pilot Summary / Release Evidence Package

---

## 4. Build → Validate → Measure → Improve

| Phase | Activity | Output |
|-------|----------|--------|
| Build | Code + migration | PR (frozen period: bugfix only) |
| Validate | Golden + sandbox + chaos | Evidence package |
| Measure | KPIs + pilot monitor | Baseline + Pilot Summary |
| Improve | Hotfix or next version | v3.0.1, v3.1.0, … |

> **A feature is complete only when it is proven safe to run in production with evidence.**

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Change freeze + severity + doc lock |
