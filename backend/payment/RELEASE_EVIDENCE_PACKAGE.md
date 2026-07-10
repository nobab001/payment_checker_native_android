# Release Evidence Package

> **Principle:** Six months later you must answer: *"What exactly was tested for this release?"*  
> **Rule:** No **Release Approved** without a completed evidence package.  
> **Storage:** `backend/payment/releases/<version>/` (create per release; do not commit secrets)

---

## Package Structure

```text
releases/
└── v3.0.1-pilot-20260706/
    ├── README.md                 ← this checklist filled in
    ├── git-commit.txt            ← git rev-parse HEAD
    ├── git-tag.txt               ← tag name
    ├── migration-version.txt     ← SQL / prisma migration ids applied
    ├── environment-version.txt   ← APP_ENV, node version, deploy host
    ├── golden-test-result.md     ← 12/12 with links to evidence
    ├── sandbox-evidence/         ← per-tx logs, screenshots (no secrets)
    │   ├── tx-001.json
    │   └── ...
    ├── kpi-baseline.json         ← curl /api/v1/payment/metrics snapshot
    ├── chaos-report.md           ← 10/10 expected vs actual
    ├── rollback-result.md        ← rollback drill log
    ├── known-issues.md           ← open P2/P3 or accepted risks
    ├── rollback-version.txt      ← previous safe tag/commit
    ├── pilot-summary.md          ← from PRODUCTION_ACCEPTANCE_GATE.md template
    ├── pag-signoff.md            ← 5 PAG categories PASS + evidence links
    └── final-signoff.md          ← Release Approved signatures
```

---

## README Template (copy per release)

```markdown
# Release Evidence Package: vX.Y.Z

| Field | Value |
|-------|-------|
| Release | |
| Git commit | |
| Git tag | |
| Migration | |
| Environment | staging / production-pilot |
| Date | |

## Checklist

- [ ] Git commit recorded
- [ ] Git tag created and pushed
- [ ] Migration applied and verified (`staging:preflight`)
- [ ] Environment documented
- [ ] Golden Test 12/12 + evidence
- [ ] Sandbox ≥20 transactions + evidence
- [ ] KPI baseline JSON attached
- [ ] Chaos 10/10 report attached
- [ ] Rollback drill PASS
- [ ] Known issues documented
- [ ] Rollback version documented
- [ ] Pilot summary completed
- [ ] PAG 5/5 PASS
- [ ] Final sign-off (Release Approved)

## Rollback

Previous safe version: `v____________`
Rollback tested: Yes / No — date: ___________

## Recommendation

[ ] GO  [ ] NO-GO

Approver: _______________  Date: ___________
```

---

## What to Capture (minimum)

| Artifact | Source |
|----------|--------|
| Git commit | `git rev-parse HEAD` |
| Git tag | `git tag -l 'v3*'` |
| Migration | `npm run db:hardening-3b5` log + index verify |
| Golden | `GOLDEN_TEST_SUITE.md` evidence rows |
| Sandbox | `PRODUCTION_GATE_EVIDENCE.md` template × ≥20 |
| KPI | `curl …/api/v1/payment/metrics` → JSON |
| Chaos | `CHAOS_TESTING.md` sign-off |
| Rollback | `PRODUCTION_RUNBOOK.md` §3 drill |

---

## Do NOT Commit

- Production credentials
- Merchant `api_secret`
- Full callback bodies with PII (redact or store offline)
- bKash sandbox passwords in repo

Store sensitive evidence in secure ticket system; reference ticket ID in package README.

---

## First Package (suggested)

When pilot starts, create:

```bash
mkdir -p backend/payment/releases/v3.0.1-pilot-YYYYMMDD
cp backend/payment/releases/README-TEMPLATE.md \
   backend/payment/releases/v3.0.1-pilot-YYYYMMDD/README.md
```

---

## Related

- `PRODUCTION_ACCEPTANCE_GATE.md` — PAG + sign-off
- `OPERATIONS_POLICY.md` — change freeze
- `RELEASE_STRATEGY.md` — version roadmap

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial release evidence package spec |
