# PayCheck Checkout UX — Full Specification

> **Purpose:** Single source of truth for Cursor and developers.  
> **Scope:** Customer checkout (`checkout.html`), **not** Official Test Experience chrome, **not** payment engine internals.  
> **Principle:** Backward compatible. No breaking API changes.
>
> **Related:** Website Purpose + Settlement A–Z → [`backend/docs/WEBSITE_PURPOSE_AND_SETTLEMENT.md`](../../docs/WEBSITE_PURPOSE_AND_SETTLEMENT.md)

---

## 0. Session purpose (Add Balance vs Payment)

| Session `purpose` | Customer sends | UI badge | Verify |
|-------------------|----------------|----------|--------|
| `add_balance` | Checkout amount (always) | Wallet credit info | Exact amount match |
| `payment` | `expectedPayable` (rounded) | Send more/less | Multi-Trx settlement (max 5); overpay → SUCCESS |

See full architecture doc for callbacks (`walletCredit`, `overPaid`, `transactions[]`).

---

## 1. Requirements Summary

### General

| # | Requirement | Status |
|---|-------------|--------|
| G1 | Existing Checkout UI preserved | ✅ `checkout.html` + modular `js/checkout/` |
| G2 | Existing Payment Engine preserved | ✅ Live via `live-init` only |
| G3 | Existing Provider Engine preserved | ✅ Registry/adapters untouched |
| G4 | No breaking changes | ✅ Additive API only |
| G5 | Premium UX | 🎯 Ongoing (animations, sheet, skeleton) |
| G6 | Production quality | 🎯 Verify idempotency, vibe match safety |

### Normal Mode (`checkout_mode = transaction`)

| # | Acceptance | How it works |
|---|------------|--------------|
| N1 | Customer can always submit Transaction ID | `#verify-fab` (Verify Payment) → modal → `#trx-input` |
| N2 | Popup can reopen | Bottom sheet closes; FAB/modal always available |
| N3 | Floating button | `#verify-fab` with 🧾 + "Verify Payment" (not "Transaction") |
| N4 | Mobile responsive | Modal + bottom sheet; 420px max-width |

### Vibe Mode (`checkout_mode = merchant_vibe`)

| # | Acceptance | How it works |
|---|------------|--------------|
| V1 | Sender number before checkout | `#vibe-step` → `vibe-init` |
| V2 | Automatic verification waits | `#vibe-auto-panel` — **no TrxID until fallback** |
| V3 | Manual fallback | 45s timer OR "Verify Manually" → Automatic \| Manual tabs |
| V4 | Auto success on payment | Poll `matched` → redirect |
| V5 | Copy timer in popup | `Waiting for payment 00:45` in sheet footer |

### UX Rules (mandatory)

```
Never ask for Transaction ID before Automatic Verification gets a chance (Vibe Mode).
Manual = fallback only in Vibe. Primary in Normal.

Normal  → Primary: TrxID via Verify Payment modal
Vibe    → Primary: Automatic | Fallback: TrxID
Live    → Primary: Gateway redirect | No verify UI if no SIM numbers
```

### Merchant Live

| # | Acceptance | How it works |
|---|------------|--------------|
| L1 | Existing behavior unchanged | `data-live` → `live-init` → `/pay/:token` → engine |

---

## 2. UI Map

```
checkout.html
├── #vibe-step              (Vibe only — sender number)
├── #checkout-main
│   ├── #tab-bar
│   ├── #pay-content
│   └── #vibe-auto-panel    (Vibe only — auto wait; manual tabs on fallback)
├── #verify-fab             (Normal — "Verify Payment" + 🧾)
├── #verify-modal           (Normal primary / shared manual form)
└── #checkout-sheet-overlay
    ├── body (provider + copy)
    ├── #sheet-mode-tabs    (Automatic | Manual — after fallback)
    ├── #sheet-wait-footer  (timer 00:45 + manual link)
    └── #sheet-manual-panel (TrxID in sheet)
```

### Interaction flows

**Normal:** Copy number → pay → tap **Verify Payment** FAB → modal → TrxID → Verify.

**Vibe:** Sender number → checkout → copy → **wait (auto only)** → after 45s or manual link → tabs → Manual → TrxID.

**Live:** Tap live provider → redirect. No verify FAB if merchant has no SIM copy numbers.

---

## 3. Architecture

### Client modules (`js/checkout/`)

| Module | Responsibility |
|--------|----------------|
| `app.js` | Bootstrap, fetch layout, verify, vibe, live-init glue |
| `model.js` | Normalize API → checkout model |
| `checkout-renderer.js` | DOM render (tabs, providers) |
| `interaction/verify-ux-controller.js` | FAB, modal, vibe auto/manual, wait timer |
| `components/*` | Header, tabs, instruction shells |

**Rule:** Never put payment verification business rules in `interaction/*`.

### Server (`checkoutController.js`)

| Function | Role |
|----------|------|
| `getCheckoutLayout` | Returns merchant config + tabs + `checkoutMode` |
| `verifyTransaction` | TrxID + amount + apiKey → claim sms_history |
| `vibeInit` / `vibeStatus` | Waiting request lifecycle |
| `matchVibeForHistory` | SMS worker hook — auto match |
| `live-init` (route) | Delegates to payment flow |

### Frozen boundaries

```
❌ backend/payment/**        — do not change for checkout UX
❌ MerchantCallbackV1 shape  — do not change from checkout
✅ checkoutController.js     — extend with additive endpoints only
✅ checkout.html + js/checkout — UX improvements allowed
```

---

## 4. API Reference (frozen contracts)

### GET `/api/checkout/:apiKey`

Returns layout including `checkoutMode`: `transaction` | `merchant_vibe`.

### POST `/api/checkout/verify`

```json
{ "apiKey": "pk_...", "trxId": "8A47K89J2B", "amount": 500, "session": "optional" }
```

### POST `/api/checkout/:apiKey/vibe-init`

```json
{ "customerNumber": "01712345678", "amount": 500 }
```

### GET `/api/checkout/vibe-status/:requestId`

Returns `{ status: "waiting" | "matched" | "expired", trxId? }`.

### POST `/api/checkout/:apiKey/live-init`

```json
{ "provider": "bkash_live", "amount": 500 }
```

→ `{ success, redirectUrl }` — **behavior must not change**.

---

## 5. Rules for AI / Developers

1. **Backward compatibility first** — old merchant links (`?apiKey=&amount=`) must work.
2. **No breaking API changes** — only add optional JSON fields.
3. **Preserve verify path** — TrxID submit must work in Normal AND Vibe (fallback).
4. **Preserve live path** — `startLivePay` → `live-init` unchanged.
5. **Vibe pre-step** — never skip sender number when mode is `merchant_vibe`.
6. **Mobile** — test 375px width; bottom sheet for card providers; verify box reachable (FAB if needed).
7. **Popup reopen** — `BottomSheetController.close()` must allow `open()` again on same provider.
8. **Do not duplicate** payment engine logic in checkout JS.
9. **Bengali copy** for customer-facing strings (Hind Siliguri font already loaded).
10. **Commit checklist:** Normal verify ✓ | Vibe auto ✓ | Vibe manual ✓ | Live redirect ✓ | Mobile ✓

---

## 6. Test Checklist (manual)

### Normal Mode
- [ ] Open `checkout.html?apiKey=VALID&amount=100`
- [ ] Submit empty TrxID → error message
- [ ] Submit valid TrxID → success or clear failure
- [ ] Open provider sheet → close → reopen same provider
- [ ] Mobile: FAB / verify box visible and usable

### Vibe Mode
- [ ] Merchant with `checkout_mode=merchant_vibe`
- [ ] Invalid phone blocked; valid phone proceeds to checkout
- [ ] Waiting spinner shows; poll runs
- [ ] Manual TrxID works while waiting
- [ ] Matched SMS → auto success redirect

### Merchant Live
- [ ] Tap live provider → redirects to payment URL
- [ ] Complete/cancel returns per existing session URLs
- [ ] No change vs baseline recording

---

## 7. File Index

| Path | Notes |
|------|-------|
| `backend/public/checkout.html` | Shell + styles |
| `backend/public/js/checkout/app.js` | Entry point |
| `backend/controllers/checkoutController.js` | Server logic |
| `backend/routes/checkoutRoutes.js` | Route mount |
| `backend/workers/smsWorker.js` | Vibe match hook |
| `.cursor/rules/checkout-ux-requirements.mdc` | Cursor rule (auto-attached on checkout files) |

---

*Version: 1.0 — aligned with acceptance criteria for Normal, Vibe, Merchant Live, and General constraints.*
