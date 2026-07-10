# Merchant Callback Versioning Strategy

> Frozen baseline: **MerchantCallbackV1** (`payment/core/merchant-callback-v1.js`)

---

## Rules

1. **V1** — `additionalProperties: false`. Do not add root fields without version bump.
2. **V1.1 (additive)** — New optional fields only. Old merchants ignore unknown fields. Bump `merchantCallbackVersion` in registry to `1.1`.
3. **V2 (breaking)** — New schema file `merchant-callback-v2.js`, new webhook path or `X-Paychek-Callback-Version: 2` header. Run V1 and V2 in parallel during migration.

---

## Version Timeline

```
V1.0  (frozen) ──► V1.1 optional fields ──► V2 breaking (new contract)
```

---

## Registry Field

Each provider entry:
```javascript
merchantCallbackVersion: '1.0'
```

Adapter `normalize()` → `buildMerchantCallbackV1()` until V2 exists.

---

## Merchant Communication

When bumping version:
1. Document changelog
2. Staging webhook test
3. 30-day overlap period for V1 + V1.1
4. Never remove V1 fields without V2 path

See also: `DEPRECATION_POLICY.md` for sunset timelines.
