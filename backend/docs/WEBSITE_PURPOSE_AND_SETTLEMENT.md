# PayChek — Website Purpose & Settlement Architecture (A–Z)

**Version:** 2.0  
**Audience:** Merchants, integrators, PayChek admins, developers  
**Status:** Implemented foundation (purpose lock, dual business logic, settlement, help CMS)

---

## 0. Decisions (open questions — fixed)

| # | Question | Decision |
|---|----------|----------|
| 1 | Who can unlock Purpose? | **Super Admin only** (`POST /api/admin/websites/:id/permissions` with `unlock_purpose: true`) |
| 2 | Both mode wrong/missing purpose? | **Hard Error** — `PURPOSE_REQUIRED` or `PURPOSE_INVALID` |
| 3 | Payment overpay? | **SUCCESS** + `overPaid` in callback; gateway does **not** refund |
| 4 | Max multi-Trx parts? | **5** (`MAX_SETTLEMENT_TXNS`) |
| 5 | New callback fields? | **Additive** — legacy `trxId` / `amount` / `status` stay |
| 6 | 50-paisa rounding? | **Global** — same for every provider (`<0.50` floor, `≥0.50` ceil) |

---

## 1. Three website purposes

| Purpose | Meaning | Customer sends | Callback focus |
|---------|---------|----------------|----------------|
| **Add Balance** | Wallet top-up | Always = checkout amount | `walletCredit` (info only) |
| **Payment** | Complete an order | = `expectedPayable` (charge/commission adjusted) | `orderAmount`, `receivedAmount`, `transactions[]` |
| **Both** | Same website, two button types | Depends on per-init `purpose` | Same as above per session |

### Lock policy

1. **New website:** Create wizard Step 2 → select purpose → Confirm → **LOCK**.
2. **Legacy website:** Settings → “Purpose not selected” → select once → Save → **LOCK**.
3. Merchant **cannot** change after lock.
4. Super Admin may unlock or set purpose via admin permissions API.

---

## 2. Add Balance flow (Wallet Top-up)

```
Merchant site → pay/init (purpose=add_balance or website fixed)
     → Checkout shows: "আপনি ৳500 পাঠান"
     → Per provider: "ওয়ালেটে ৳502 / ৳498 যোগ হবে"  (info only)
     → Customer sends ৳500
     → Verify: SMS amount must equal 500
     → Callback:
```

```json
{
  "purpose": "add_balance",
  "trxId": "...",
  "amount": 500,
  "checkoutAmount": 500,
  "receivedAmount": 500,
  "walletCredit": 502,
  "provider": "bkash",
  "status": "SUCCESS"
}
```

**Merchant responsibility:** `customerWallet += walletCredit`  
**PayChek does NOT credit any wallet.**

---

## 3. Payment flow (Order complete)

```
Order = 500
Charge on Nagad → expectedPayable = 502 (after rounding)
Checkout: "Order ৳500 — Please send ৳502"
```

### Exact pay → SUCCESS

```json
{
  "purpose": "payment",
  "orderAmount": 500,
  "expectedPayable": 502,
  "receivedAmount": 502,
  "transactions": [{ "trxId": "A", "amount": 502 }],
  "status": "SUCCESS"
}
```

### Underpay → Partial settlement

Customer sent 500, expected 502 → remaining 2.

Checkout message:

> আপনি ৳2 কম পাঠিয়েছেন। আরও ৳2 পাঠিয়ে Transaction ID দিন।

Second SMS ৳2 → total 502 → SUCCESS. Max **5** parts.

### Overpay → SUCCESS + notice

Expected 498, sent 500 → SUCCESS, `overPaid: 2`.

Checkout:

> আপনি ৳2 বেশি পাঠিয়েছেন। অতিরিক্ত টাকা ফেরত/সমন্বয়ের জন্য মার্চেন্টের সাথে যোগাযোগ করুন।

**Gateway never refunds.**

---

## 4. Paisa rounding (Payment mode only)

Applies when computing `expectedPayable` / customer send amount:

| Raw | Shown / matched |
|-----|-----------------|
| 505.48 | **505** |
| 505.50 | **506** |
| 505.52 | **506** |

Add Balance does **not** change what the customer sends (always checkout amount).

---

## 5. Both mode — integration

```js
// Add Balance button (usually one)
await payInit({ amount: 500, purpose: "add_balance", ... });

// Buy / Pay / Offer / Product buttons (many) — same purpose
await payInit({ amount: 500, purpose: "payment", orderId: "...", ... });
```

Missing purpose → `PURPOSE_REQUIRED`  
Invalid purpose → `PURPOSE_INVALID`

---

## 6. Callback unlock (Payment Type & Commission)

These flags exist so merchants can choose how much PayChek helps:

| Flag | Who unlocks | Who enables |
|------|-------------|-------------|
| `allow_payment_type_callback` | **Admin** | Merchant `receive_payment_type` |
| `allow_commission_callback` | **Admin** | Merchant `receive_commission` |

### Scenario A — Merchant has their own system

- They already know provider / can compute commission.
- Keep callbacks **locked** (default).
- Use purpose fields: `walletCredit` / `expectedPayable` / `transactions`.

### Scenario B — Merchant has no calculation system

- Callback amount alone is used to credit wallet / mark order.
- Contact PayChek admin → unlock Payment Type and/or Commission callbacks.
- Then merchant toggles “গ্রহণ” in Website Settings.
- Without unlock, toggles stay disabled and API returns `*_CALLBACK_LOCKED`.

**Admin API:**

```http
POST /api/admin/websites/:id/permissions
{
  "allow_payment_type_callback": true,
  "allow_commission_callback": true,
  "commission_enabled": true,
  "unlock_purpose": false
}
```

---

## 7. Global Purpose Help (ⓘ)

Admin CRUD (global, not per-merchant):

| Method | Path |
|--------|------|
| GET | `/api/admin/purpose-help` |
| PUT | `/api/admin/purpose-help` body `{ content: { overview, add_balance, payment, both } }` |
| DELETE | `/api/admin/purpose-help/:key` reset one key to default |

Keys: `overview` | `add_balance` | `payment` | `both`  
Each: `{ title, body }` — shown in merchant app ⓘ popup (not hardcoded).

---

## 8. API cheat-sheet

### Create website (locks purpose)

```http
POST /api/v1/websites
{ "domain": "shop.com", "website_name": "Shop", "website_purpose": "add_balance" }
```

### Legacy lock purpose

```http
PATCH /api/v1/websites/:id
{ "website_purpose": "payment" }
```
→ sets `purpose_locked=1`. Second change → `PURPOSE_LOCKED`.

### Pay init (Both)

```http
POST /api/v1/pay/init
{ "amount": 500, "purpose": "payment", "orderId": "ORD-1", ... }
```
Header: `X-Signature` HMAC of body with api_secret.

### Checkout verify

```http
POST /api/checkout/verify
{
  "apiKey": "...",
  "trxId": "...",
  "amount": 500,
  "session": "...",
  "expectedPayable": 502
}
```

Responses may include `partial: true`, `remaining`, `walletCredit`, `overPaid`, `transactions`.

---

## 9. What PayChek does vs does not

| Does | Does not |
|------|----------|
| Verify SMS / Trx | Credit merchant customer wallets |
| Compute walletCredit / expectedPayable | Refund overpay |
| Multi-Trx settlement (Payment) | Change locked purpose for merchants |
| Sign & deliver callbacks | Invent merchant business rules beyond purpose |

---

## 10. Migration notes

- Existing sites: `purpose_selected=0`, `purpose_locked=0` until merchant selects once.
- Default `website_purpose` remains `add_balance` for DB compatibility.
- Callback consumers: ignore unknown fields; adopt new fields when ready.
- Checkout UI: Add Balance badges show wallet credit; Payment badges show send amount.

---

## 11. Related code

| Area | Path |
|------|------|
| Purpose rules | `backend/services/websitePurpose.js` |
| Settlement | `backend/services/checkoutSettlementService.js` |
| Callbacks | `backend/services/merchantCallback.js` |
| Verify | `backend/controllers/checkoutController.js` |
| Website lock | `backend/controllers/websiteController.js` |
| Admin unlock / help | `backend/controllers/adminController.js` |
| Checkout incentives UI | `backend/public/js/checkout/model.js` |
| UX rules | `backend/public/js/checkout/CHECKOUT_UX_SPEC.md` |
