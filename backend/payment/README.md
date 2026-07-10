# Payment Platform — Start Here (5-Minute Onboarding)

> **You are joining a governed payment platform, not a single feature branch.**  
> **Mode:** Validation > Development until Pilot PASS.  
> **Motto:** Release Complete ≠ Code Complete. Evidence before Production.

---

## If you only read one thing

1. Read **`FOUNDATION_FREEZE_v3.md`** — v3.0.0 baseline (FROZEN)  
2. Read **`CONTRACT.md`** — how providers plug in  
3. Read **this file** — where everything lives  
4. Run **`npm run staging:preflight`** — proves your env is ready  
5. Read **`PRODUCTION_ACCEPTANCE_GATE.md`** — nothing ships without PAG  
6. **Never modify frozen contracts** without PR + version bump → `OPERATIONS_POLICY.md`  
7. Follow the **phase workflow** below — no Phase-3C until Pilot GO + lessons learned

---

## Four Layers (mental model)

```text
Application Layer     Checkout UI, Admin, Website APIs
Platform Layer        Payment Engine, Registry, Session, Callback, Outbox
Operations Layer      KPIs, Runbooks, Evidence, Gates, Golden Tests
Governance Layer      Freeze, PAG, ADRs, DoD, Risk Register, Release Evidence
```

New code goes in **Platform**. UI changes are **frozen**. Production decisions use **Operations + Governance**.

---

## New developer — first hour

| Step | Action |
|------|--------|
| 1 | `cd backend && npm install` |
| 2 | Copy `.env.example` → `.env` (never commit secrets) |
| 3 | `npm run db:hardening-3b5` (first time only) |
| 4 | `npm run test:payment-gate` — expect 16/16 PASS |
| 5 | Read `CONTRACT.md`, `LIFECYCLE_SPEC.md`, `DOCUMENTATION_FREEZE.md` |
| 6 | Read `DECISIONS.md` — why things are built this way |
| 7 | Skim `OPERATIONS_POLICY.md` — what you cannot change during freeze |

**Do not** start Phase-3C or add providers until `PRODUCTION_ACCEPTANCE_GATE.md` Exit Criteria pass.

---

## Phase workflow (current)

```text
✅ Phase-3A   Foundation frozen (v3.0.0-payment-foundation)
✅ Phase-3B   bkash_live lifecycle (code)
✅ Phase-3B.5 Hardening (outbox, metrics auth, constraints)
🔶 Phase-3B.6 Staging Validation  ← YOU ARE HERE
⬜ Pilot      1 → 3 → 10 → 25 → all merchants
⬜ v3.0.1     Pilot bugfixes only
⬜ Phase-3C   Commission Engine (registry-driven Rule Engine)
⬜ v3.2.0     Multi-provider adapters
⬜ v3.3.0     Operations dashboard
⬜ v4.0.0     High availability
```

**Next 2–3 weeks:** Staging → Evidence → Pilot → Retrospective → GO/NO-GO. **Not new architecture.**

---

## Validation execution (engineering mode)

```bash
cd backend
npm run staging:preflight          # env + DB + indexes
# deploy to staging, then:
# Golden 12/12 → GOLDEN_TEST_SUITE.md
# Sandbox ≥20 tx → PRODUCTION_GATE_EVIDENCE.md
# Chaos 10/10 → CHAOS_TESTING.md
# PAG 5/5 → PRODUCTION_ACCEPTANCE_GATE.md
# Bundle → RELEASE_EVIDENCE_PACKAGE.md
```

---

## Governance docs (read before changing payment code)

| Doc | When to read |
|-----|--------------|
| `FOUNDATION_FREEZE_v3.md` | v3.0.0 baseline — FROZEN |
| `DECISIONS.md` | ADRs (why baseline exists) |
| `OPERATIONS_POLICY.md` | Change freeze, incidents, PR lock |
| `DEFINITION_OF_DONE.md` | When is a phase truly done? |
| `DEPRECATION_POLICY.md` | How V1 → V2 sunsets work |
| `RISK_REGISTER.md` | Known risks + mitigations |
| `PRODUCTION_ACCEPTANCE_GATE.md` | Production / Phase-3C gate |
| `RELEASE_STRATEGY.md` | Version roadmap |

---

## Architecture docs (frozen — PR required)

| Doc | Content |
|-----|---------|
| `CONTRACT.md` | Registry + adapter contract |
| `LIFECYCLE_SPEC.md` | Session state machine |
| `CALLBACK_VERSIONING.md` | MerchantCallbackV1 → V2 |
| `events/EVENTS_FROZEN.md` | Event names |
| `errors/error-codes.js` | PAY_1xxx (add-only) |
| `PROVIDER_CERTIFICATION.md` | New adapter checklist |

---

## Operations docs (run / release / incident)

| Doc | Content |
|-----|---------|
| `STAGING_DEPLOY_GATE.md` | Staging deploy steps |
| `GOLDEN_TEST_SUITE.md` | 12 repeatable tests |
| `PRODUCTION_GATE_EVIDENCE.md` | Evidence per transaction |
| `PRODUCTION_KPIS.md` | Health score metrics |
| `PRODUCTION_RUNBOOK.md` | Incidents, rollback |
| `RELEASE_EVIDENCE_PACKAGE.md` | Per-release bundle |
| `releases/README-TEMPLATE.md` | Copy for each release |

---

## Phase certificates

| Phase | Certificate |
|-------|-------------|
| 3B | `certificates/PHASE_3B.md` |
| Pilot lessons | `certificates/PILOT_LESSONS_LEARNED_TEMPLATE.md` |
| Template | `certificates/PHASE_COMPLETION_TEMPLATE.md` |

---

## Automated checks

```bash
npm run test:payment-unit    # 6 unit tests
npm run test:payment-gate    # 16 production gates
npm run staging:preflight    # staging readiness
npm run db:hardening-3b5     # DDL migration
```

---

## Hard rules

1. **No `if (provider === 'bkash')`** in core — use Registry + Adapter  
2. **No frozen contract edits** without PR (`OPERATIONS_POLICY.md`)  
3. **No Production Pilot** without PAG 5/5 + evidence package  
4. **No Phase-3C** until Exit Criteria 10/10 (`PRODUCTION_ACCEPTANCE_GATE.md`)  
5. **Checkout UI** (`public/js/checkout/**`) is frozen  

---

## Questions?

| Question | Answer in |
|----------|-----------|
| How do I add a provider? | `PROVIDER_CERTIFICATION.md` + new adapter only |
| How do I release? | `RELEASE_EVIDENCE_PACKAGE.md` |
| What if Redis dies? | `RISK_REGISTER.md` + `PRODUCTION_RUNBOOK.md` |
| Why outbox? | `DECISIONS.md` ADR-005 |
| Is Phase-3B done? | `certificates/PHASE_3B.md` — pending pilot evidence |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 2.0 | 2026-07-06 | Onboarding entry point + governance index |
