# Definition of Done (DoD) — Payment Platform

> **Rule:** Code Complete ≠ Feature Complete ≠ Release Complete  
> **Applies to:** Every phase (3C, 4, 5…), every release tag, every hotfix

---

## Feature / Phase Done Checklist

A phase or feature is **Done** only when **all** are true:

| # | Criterion | Evidence |
|---|-----------|----------|
| 1 | **Code complete** | Merged PR, scope locked |
| 2 | **Tests complete** | Unit + gate tests PASS; golden updated if behavior changed |
| 3 | **Documentation updated** | README, ADR, freeze docs if contracts touched |
| 4 | **Monitoring updated** | Metrics, health endpoints, KPIs in `PRODUCTION_KPIS.md` |
| 5 | **Runbook updated** | `PRODUCTION_RUNBOOK.md` — new failure modes |
| 6 | **Risk register updated** | `RISK_REGISTER.md` — new risks or mitigations |
| 7 | **Release evidence ready** | `RELEASE_EVIDENCE_PACKAGE.md` folder populated |
| 8 | **Review approved** | Code review + architecture review (if Platform change) |

**Missing any item → not Done.**

---

## Release Done (additional)

| # | Criterion |
|---|-----------|
| 9 | PAG 5/5 PASS (`PRODUCTION_ACCEPTANCE_GATE.md`) |
| 10 | **Release Approved** signature |
| 11 | Rollback version documented |
| 12 | Known issues documented |

---

## Phase Done (additional)

| # | Criterion |
|---|-----------|
| 13 | Phase Completion Certificate signed (`certificates/`) |
| 14 | Deferred items listed explicitly |
| 15 | Lessons learned recorded |

---

## Hotfix Done (reduced)

| # | Criterion |
|---|-----------|
| 1 | Fix merged |
| 2 | Regression test or golden test for bug |
| 3 | Runbook updated if new ops step |
| 4 | Evidence in release package (patch version) |
| 5 | Severity classified (`OPERATIONS_POLICY.md`) |

---

## What "Not Done" Looks Like

- Code merged but no golden test evidence  
- Docs say "TODO" for runbook  
- Metrics not checked after deploy  
- No ADR for architectural decision  
- Pilot started without PAG  

---

## Phase-3B Current Status

| DoD item | Status |
|----------|--------|
| Code | ✅ (pending pilot validation) |
| Tests | ✅ automated gate; ⬜ staging golden |
| Docs | ✅ governance complete |
| Monitoring | ✅ endpoints exist |
| Runbook | ✅ |
| Risk register | ✅ |
| Release evidence | ⬜ pilot pending |
| Review | ⬜ post-pilot |
| **Phase Done** | **⬜ NOT YET** — see `certificates/PHASE_3B.md` |

---

## Version

| Version | Date |
|---------|------|
| 1.0 | 2026-07-06 |
