# PayChek Payment Engine — Core Contract (v1)

> **Status:** Phase-3A Foundation + Pre-3B Reliability Layer  
> **Lifecycle Spec:** See [`LIFECYCLE_SPEC.md`](./LIFECYCLE_SPEC.md) (v1.0 FROZEN)  
> **Checkout UI:** FROZEN — no changes to `backend/public/js/checkout/**`

---

## 1. Architecture Layers

```
Merchant
    ↓
PayChek API (checkout / pay/init)
    ↓
PaymentEngine                    ← orchestrator
    ↓
SessionEngine                    ← payment_sessions (Provider never writes DB)
    ↓
ProviderFactory → Adapter        ← bkash-live, sslcommerz, …
    ↓
RedirectEngine                   ← HTTP 302 / JSON redirectUrl
    ↓
Official Payment Server
    ↓
CallbackEngine (Phase-3B)        ← signature, idempotency, merchant webhook
    ↓
CommissionEngine (Phase-3C)      ← type callback, commission rules
```

**Rule:** Checkout sends **only `providerId`** (e.g. `bkash_live`). It never knows bKash API details.

---

## 2. Folder Structure

```
backend/payment/
├── CONTRACT.md              ← this document
├── index.js                 ← public API
├── core/
│   ├── payment-types.js     ← SIM | LIVE | BANK | CARD
│   ├── payment-status.js    ← CREATED | SUCCESS | …
│   ├── provider-flags.js    ← supports.* capabilities
│   └── callback-schema.js   ← normalized merchant payload
├── registry/
│   ├── provider-registry.js ← canonical entries + capabilities
│   ├── provider-alias.js    ← bkash_live ↔ bkash_merchant
│   └── provider-factory.js  ← getProvider(id) → Adapter
├── providers/
│   ├── base-provider.js     ← mandatory interface
│   ├── bkash-live.js
│   ├── nagad-live.js
│   ├── rocket-live.js
│   ├── sslcommerz.js
│   ├── surjopay.js
│   ├── portwallet.js
│   ├── bank.js
│   └── card.js
├── session/
│   └── payment-session.js   ← Step 2 implementation
├── redirect/
│   └── redirect-service.js
├── callback/                  ← Phase-3B stub
├── commission/                ← Phase-3C stub
├── engine/
│   └── payment-engine.js      ← Step 2: initiate()
└── shared/
    ├── provider-errors.js
    ├── provider-utils.js
    └── provider-validator.js
```

---

## 3. Provider Registry Entry Schema

```json
{
  "id": "bkash_live",
  "displayName": "bKash Merchant",
  "type": "LIVE",
  "company": "bKash",
  "version": 1,
  "adapter": "bkash-live",
  "aliases": ["bkash_live", "bkash_merchant", "bkash"],
  "supports": {
    "redirect": true,
    "callback": true,
    "webhook": true,
    "otp": true,
    "pin": true,
    "commission": true,
    "typeCallback": true,
    "refund": false,
    "capture": false,
    "recurring": false,
    "polling": false
  },
  "callbackPath": "/payment/callback/bkash",
  "sessionTimeout": 1800,
  "dbProviderKey": "bkash_merchant"
}
```

### Registered Providers (v1)

| Canonical ID   | Adapter      | Legacy DB Key     |
|----------------|--------------|-------------------|
| `bkash_live`   | bkash-live   | bkash_merchant    |
| `nagad_live`   | nagad-live   | nagad_merchant    |
| `rocket_live`  | rocket-live  | rocket_merchant   |
| `sslcommerz`   | sslcommerz   | sslcommerz        |
| `surjopay`     | surjopay     | surjopay          |
| `portwallet`   | portwallet   | portwallet        |
| `bank`         | bank         | bank              |
| `card`         | card         | card              |

### Capability Version

`version: 1` on registry entry = **adapter contract version**, not bKash API version.

When bKash API v2 → v3: bump adapter implementation or add `apiVersion` in adapter config — **registry id stays `bkash_live`**.

---

## 4. Provider Alias Resolution

```
resolveProviderId("bkash")           → "bkash_live"
resolveProviderId("bkash_merchant")  → "bkash_live"
resolveProviderId("bkash_live")      → "bkash_live"
toDbProviderKey("bkash_live")        → "bkash_merchant"
```

No database migration required for frontend ↔ backend id alignment.

---

## 5. Adapter Interface (Mandatory)

Every adapter **extends `BaseProvider`** and implements:

| Method | Phase-3A | Responsibility |
|--------|----------|----------------|
| `initialize(ctx)` | ✅ | Validate merchant gateway config |
| `createPayment(ctx)` | ✅ | Provider-side payment intent (no DB) |
| `getRedirectUrl(ctx, payment)` | ✅ | Build redirect URL from template |
| `verify(ctx)` | stub | Poll/query provider status |
| `normalize(raw)` | stub | Raw → normalized callback JSON |
| `callback(req, ctx)` | stub | Inbound gateway webhook |
| `health()` | ✅ | Admin health probe |

**Providers MUST NOT:** create or update `payment_sessions` rows.

---

## 6. Payment Session Contract

```json
{
  "checkoutId": "optional",
  "paymentId": "internal id",
  "sessionToken": "ps_…",
  "merchantId": "website id",
  "providerId": "bkash_live",
  "amount": 500,
  "currency": "BDT",
  "status": "CREATED",
  "createdAt": "ISO-8601",
  "expiresAt": "ISO-8601",
  "callbackUrl": "merchant webhook",
  "returnUrl": "success/cancel"
}
```

SessionEngine owns CRUD on `payment_sessions`. Maps legacy `created|redirected|completed` via `toCanonicalStatus()`.

---

## 7. Normalized Merchant Callback (Phase-3B output)

All providers converge to this shape:

```json
{
  "paymentId": "…",
  "provider": "bKash",
  "providerId": "bkash_live",
  "providerType": "LIVE",
  "status": "SUCCESS",
  "amount": 500,
  "currency": "BDT",
  "transactionId": "…",
  "providerTransactionId": "…",
  "providerReference": "…",
  "type": "PAYMENT",
  "commission": 5,
  "charge": 0,
  "orderId": "…",
  "sessionId": "…",
  "completedAt": "…"
}
```

Schema ID: `paychek://schemas/normalized-callback/v1` (see `core/callback-schema.js`).

---

## 8. Phase Boundaries

| Phase | Deliverable | Checkout Touch |
|-------|-------------|----------------|
| **3A Contract** (now) | Registry, Factory, BaseProvider, stubs | ❌ |
| **3A Step 2** | SessionEngine + PaymentEngine.initiate + wire live-init | ❌ (thin delegate) |
| **3B** | callback/, signature, idempotency, merchant webhook | ❌ |
| **3C** | commission/, type callback, feature flags | ❌ |

---

## 9. Success Criteria (Phase-3A complete)

- [ ] `POST /live-init` delegates to `PaymentEngine.initiate()`
- [ ] New gateway = registry entry + adapter file only
- [ ] No if-else provider chains outside factory
- [ ] Alias map resolves all legacy ids
- [ ] Checkout UI zero diff
- [ ] `callback()` / `normalize()` remain stubs until 3B

---

## 10. Usage (backend only)

```js
const payment = require('./payment');

// Resolve alias
const entry = payment.registry.alias.resolveProviderEntry('bkash_merchant');

// Get adapter (no if-else)
const adapter = payment.registry.factory.getProvider('bkash_live');
await adapter.initialize(ctx);
const paymentResult = await adapter.createPayment(ctx);
const url = await adapter.getRedirectUrl(ctx, paymentResult);
```

---

*Document version: 1.0 — aligns with `backend/payment/` contract layer implementation.*
