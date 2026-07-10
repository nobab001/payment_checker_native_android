# Deprecation Policy — Payment Platform

> **Applies to:** Merchant callbacks, PaymentContext, error codes, provider contracts, public APIs  
> **Pair with:** `CALLBACK_VERSIONING.md`, `DOCUMENTATION_FREEZE.md`, `OPERATIONS_POLICY.md`

---

## Lifecycle Stages

```text
Released → Supported → Deprecated → Removal Notice → Removed
```

| Stage | Meaning | Merchant / integrator action |
|-------|---------|------------------------------|
| **Released** | Current default | Use documented version |
| **Supported** | Still fully maintained | No action required |
| **Deprecated** | Still works; no new features | Plan migration |
| **Removal Notice** | End-of-life date published | Migrate before EOL |
| **Removed** | No longer served | Must use new version |

---

## Minimum Timelines (payment contracts)

| Change type | Overlap period | Notice before removal |
|-------------|----------------|------------------------|
| Additive (V1.1 optional fields) | 30 days recommended | N/A |
| Breaking (V2) | **90 days** parallel run | **60 days** written notice |
| Error code renumber | **Never** — add-only | N/A |
| Event rename | New event name; old deprecated 90 days | 60 days |

---

## MerchantCallback Version Sunset (example)

```text
MerchantCallbackV1.0 (frozen)
        ↓
V1.1 optional fields released — V1.0 still supported
        ↓
MerchantCallbackV2 announced — V1 deprecated
        ↓
90-day parallel: V1 + V2 webhooks both accepted
        ↓
Removal notice: V1 EOL date
        ↓
V1 Removed — V2 only
```

**Implementation rules:**

- V2 = new module (`merchant-callback-v2.js`), not edits to V1
- Header `X-Paychek-Callback-Version: 2` or new webhook path
- Changelog + staging webhook test before deprecation announcement
- Never remove V1 fields without V2 migration path

See `CALLBACK_VERSIONING.md` for schema rules.

---

## Provider Adapter Deprecation

| Step | Action |
|------|--------|
| 1 | Set registry `enabled: false` or `maintenance: true` |
| 2 | ADR + merchant notice |
| 3 | 30-day maintenance mode (no new sessions) |
| 4 | Existing sessions complete; no new init |
| 5 | Remove adapter in major version only (e.g. v4.0.0) |

---

## API Route Deprecation

1. Add `Deprecation: true` header + log warning  
2. Document successor route in ADR  
3. Minimum 90 days overlap  
4. Remove in minor/major per `RELEASE_STRATEGY.md`

---

## Emergency Deprecation

Security vulnerability may shorten timeline. Requires:

- P0 incident ID
- Engineering lead approval
- Merchant notification within 24h
- ADR documenting shortened timeline

---

## Checklist Before Deprecating Anything

- [ ] ADR added to `DECISIONS.md`
- [ ] Migration guide written
- [ ] Golden tests updated for new version
- [ ] Staging evidence for both versions (overlap period)
- [ ] `DEFINITION_OF_DONE.md` satisfied for replacement

---

## Version

| Version | Date |
|---------|------|
| 1.0 | 2026-07-06 |
