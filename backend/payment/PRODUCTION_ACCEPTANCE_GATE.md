# Production Acceptance Gate (PAG)

> **Principle:** Code Complete ≠ Release Complete.  
> **Rule:** Production Pilot starts only when **all 5 PAG categories PASS with evidence**.  
> **One FAIL → Pilot suspended.**

---

## PAG — Five Categories (all required)

| Category | Requirement | Evidence | Status |
|----------|-------------|----------|--------|
| **Functional** | Golden Test Suite 12/12 PASS | `GOLDEN_TEST_SUITE.md` rows + `PRODUCTION_GATE_EVIDENCE.md` | ⬜ |
| **Reliability** | Chaos Test 10/10 PASS | `CHAOS_TESTING.md` expected vs actual log | ⬜ |
| **Operational** | KPI baseline captured | `baseline-YYYYMMDD.json` from `/api/v1/payment/metrics` | ⬜ |
| **Business** | Merchant confirms callback & settlement | Signed merchant ack / email / ticket | ⬜ |
| **Security** | Secrets, signatures, metrics auth verified | Security audit checklist (Stage-9) | ⬜ |

**Sign-off:** No category is PASS without attached evidence files.

---

## Exit Criteria — Phase-3B → Phase-3C

Phase-3C (Commission Engine) starts **only** when every item below is checked with evidence:

| # | Criterion | Target | Evidence |
|---|-----------|--------|----------|
| 1 | Golden Test Suite | 12/12 PASS | Golden evidence sheet |
| 2 | Sandbox transactions | ≥ 20 successful, evidence-backed | Transaction log |
| 3 | Chaos tests | 10/10 PASS | Chaos report |
| 4 | Pilot stability | 48–72h no critical incident | Pilot monitor log |
| 5 | Duplicate callback | 0 | Webhook counts |
| 6 | Data corruption | 0 | SQL integrity queries |
| 7 | Merchant callback success | ≥ 99.9% | outbox `sent` rate |
| 8 | Rollback procedure | Rehearsed successfully | Rollback test log |
| 9 | Incidents | None unresolved | Incident tracker |
| 10 | Architecture review | Approved | Review notes / PR |

---

## Phase-3C Entry Gate (strict)

```
Golden Test ............ 100%     ⬜
Sandbox QA ............. PASS      ⬜
Chaos Test ............. PASS      ⬜
Pilot .................. 72h stable ⬜
Success Rate ........... ≥ 99.5%   ⬜
Duplicate Callback ..... 0         ⬜
Dead Queue ............. 0         ⬜
Critical Incident ...... 0         ⬜
Merchant Complaint ..... 0         ⬜
PAG (5 categories) ..... ALL PASS  ⬜
```

**All boxes ticked → Phase-3C authorized.**

---

## Release Sign-off (before Production deploy)

```
Architecture Review      ⬜
Code Review              ⬜
Migration Review         ⬜
Golden Tests             ⬜
Sandbox QA               ⬜
Chaos Test               ⬜
Rollback Verified        ⬜
Pilot Approved           ⬜
Release Evidence Package ⬜  → see RELEASE_EVIDENCE_PACKAGE.md
Release Approved         ⬜  ← Production deploy blocked until this
```

| Role | Name | Date |
|------|------|------|
| Engineering Lead | | |
| QA / Validator | | |
| Operations | | |
| Release Approver | | |

---

## Pilot Summary (one-page report)

Copy after each pilot phase. Attach to Release Evidence Package.

```text
Pilot Summary
=============

Release / Phase:     Pilot-1 | v3.0.1 | ...
Period:              YYYY-MM-DD → YYYY-MM-DD
Environment:         staging | production-pilot

Transactions:        __
Success Rate:        __%
Merchant Callback Success: __%
Duplicate Callback:  __
Average Callback Latency: __ms
p95 Latency:         __ms
Outbox Pending:      __
Dead Queue:          __
Circuit OPEN (hours): __
Rollback Needed:     Yes | No
Critical Incidents:  __
Merchant Complaints: __

PAG Status:
  Functional:    PASS | FAIL
  Reliability:   PASS | FAIL
  Operational:   PASS | FAIL
  Business:      PASS | FAIL
  Security:      PASS | FAIL

Recommendation:
  [ ] GO — expand pilot / proceed to Phase-3C
  [ ] NO-GO — hold / rollback / fix

Signed: _______________  Date: __________
```

---

## Execution Flow

```text
Staging Validation
        ↓
Golden + Sandbox + Chaos (evidence)
        ↓
PAG — 5 categories PASS
        ↓
Production Pilot (controlled rollout)
        ↓
Pilot Summary + Retrospective
        ↓
Exit Criteria 10/10
        ↓
Phase-3C Commission Engine
```

---

## Related Docs

| Doc | Purpose |
|-----|---------|
| `RELEASE_EVIDENCE_PACKAGE.md` | Per-release evidence bundle |
| `OPERATIONS_POLICY.md` | Change freeze + incident severity |
| `GOLDEN_TEST_SUITE.md` | 12 golden tests |
| `PRODUCTION_GATE_EVIDENCE.md` | Per-transaction evidence |
| `PRODUCTION_KPIS.md` | KPI thresholds |
| `RELEASE_STRATEGY.md` | Version roadmap |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial PAG + exit criteria |
