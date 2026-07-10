# Release Strategy — Payment Platform

> **Principle:** Pilot before scale. Evidence before tag. **Release Complete ≠ Code Complete.**  
> **Gate:** `PRODUCTION_ACCEPTANCE_GATE.md` (PAG) required before production pilot.

---

## Version Roadmap

```text
v3.0.0-payment-foundation     ✅ Foundation frozen (tag exists)
        ↓
Staging Validation            🔶 Engineering mode (current)
        ↓
PAG + Pilot                   🔶 Evidence required
        ↓
v3.0.1                        Pilot bugfixes only
        ↓
v3.1.0                        Commission Engine (Registry-driven)
        ↓
v3.2.0                        Multi-provider (adapters only)
        ↓
v3.3.0                        Operations Dashboard
        ↓
v4.0.0                        High Availability
```

---

## Tag Rules

| Tag | Contents | Gate |
|-----|----------|------|
| `v3.0.0-*` | Foundation | Automated gate PASS |
| `v3.0.1` | Pilot hotfixes | Golden suite + evidence |
| `v3.1.0` | Commission | PAG + Exit Criteria 10/10 (`PRODUCTION_ACCEPTANCE_GATE.md`) |
| `v3.2.0` | +providers | Per-provider certification + evidence package |
| `v3.3.0` | Ops dashboard | DLQ, trace explorer, live monitoring |
| `v4.0.0` | HA | Load test + failover drill |

---

## Controlled Rollout

| Phase | Merchants | Min Monitor |
|-------|-----------|-------------|
| Pilot-1 | 1 website | 48h |
| Pilot-2 | 3 websites | 72h |
| Pilot-3 | 10 websites | 1 week |
| Pilot-4 | 25 websites | 1 week |
| GA | All | Ongoing KPIs |

**Rollback trigger:** See `PRODUCTION_KPIS.md` incident thresholds.

---

## Pre-Release Checklist (every tag)

- [ ] `npm run test:payment-gate`
- [ ] `npm run staging:preflight` on target env
- [ ] `GOLDEN_TEST_SUITE.md` 12/12 + evidence
- [ ] Metrics baseline compared (no regression > 2× p95)
- [ ] `DOCUMENTATION_FREEZE.md` contracts respected
- [ ] `PRODUCTION_ACCEPTANCE_GATE.md` PAG 5/5
- [ ] `RELEASE_EVIDENCE_PACKAGE.md` bundle created
- [ ] `OPERATIONS_POLICY.md` change freeze respected

---

## Pilot Retrospective Template

After each pilot phase (48–72h):

```
Phase: Pilot-1 | Pilot-2 | ...
Period:
Transactions:
Success rate:
Duplicate callbacks:
Outbox dead count:
Incidents:
Root causes:
Action items:
Go / No-go for next expansion:
```

**Mandatory:** Complete `certificates/PILOT_LESSONS_LEARNED_TEMPLATE.md` → save under `releases/<pilot-id>/PILOT_LESSONS_LEARNED.md`

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial release strategy |
