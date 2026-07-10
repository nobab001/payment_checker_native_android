# Risk Register — Payment Platform

> **Review:** Before each pilot expansion and each release.  
> **Update:** After every P0/P1 incident.  
> **Pair with:** `PRODUCTION_RUNBOOK.md`, `PRODUCTION_KPIS.md`

---

## Active Risks

| ID | Risk | Probability | Impact | Mitigation | Residual | Owner |
|----|------|-------------|--------|------------|----------|-------|
| R01 | **Redis down** — idempotency memory fallback single-instance only | Medium | High | Memory fallback; monitor duplicates; restore Redis < 15m; no multi-instance without Redis | Medium | Ops |
| R02 | **Merchant callback slow/down** | High | Medium | Outbox + retry; dead queue alert; `PRODUCTION_RUNBOOK.md` §6 | Low | Ops |
| R03 | **Duplicate callback** — merchant double-charge perception | Medium | High | 4-layer idempotency; golden G05; KPI duplicate count = 0 | Low | Eng |
| R04 | **Gateway timeout** — bKash API unavailable | High | Medium | Circuit breaker; maintenance response; probe health | Medium | Eng |
| R05 | **DB slow / lock** — stuck outbox `processing` | Medium | High | 5m stale lock reclaim; monitor pending; runbook §5 | Medium | Ops |
| R06 | **Worker crash** after outbox insert | Low | Medium | Outbox persists; cron worker 15s; inline batch on callback | Low | Eng |
| R07 | **Wrong amount** in callback vs session | Low | Critical | Server-side session amount authoritative; adapter normalize; golden G07 | Low | Eng |
| R08 | **Secret leak** in logs | Low | Critical | `trace-logger` redaction; security audit Stage-9 | Low | Eng |
| R09 | **Schema drift** — frozen contract changed without PR | Medium | High | `OPERATIONS_POLICY.md` freeze; PR required | Low | Eng |
| R10 | **Pilot expanded too fast** | Medium | High | Controlled rollout 1→3→10→25; PAG each phase | Medium | PM |
| R11 | **Commission before validation** (Phase-3C early) | Medium | High | Exit Criteria block; ADR-008 | Low | Eng |
| R12 | **bKash sandbox ≠ production** behavior | High | Medium | Production pilot 1 merchant; golden re-run on prod creds | Medium | Ops |

---

## Risk Matrix (summary)

```text
Impact →
Prob ↓     Low      Medium      High       Critical
High       R04      R02,R12    —          —
Medium     R06,R09  R01,R05    R03,R10    R07
Low        —        —          R08        —
```

---

## Incident → Risk Review

After any P0/P1:

1. Was risk in register? Update probability/impact.
2. Was mitigation effective? If not, new ADR or engineering task.
3. Add row to pilot summary / release evidence.

---

## Accepted Risks (pilot phase)

| Risk | Acceptance reason | Review date |
|------|-------------------|-------------|
| R12 sandbox vs prod | Pilot uses sandbox first | Before prod creds |
| R01 Redis single-instance fallback | Single staging/pilot node | Before HA (v4.0.0) |

---

## Closed Risks

| ID | Risk | Closed | Notes |
|----|------|--------|-------|
| — | — | — | — |

---

## Version

| Version | Date | Reviewer |
|---------|------|----------|
| 1.0 | 2026-07-06 | — |
