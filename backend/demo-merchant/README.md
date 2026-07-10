# Demo Merchant (v1)

A **test harness application** that simulates a real merchant consuming the PayCheck payment platform end-to-end. This is **not** production e-commerce.

## Scope (v1)

| Module | Features |
|--------|----------|
| Authentication | Register, login, logout, session |
| Dashboard | Wallet balance, recent orders/transactions, payment stats |
| Wallet | Balance, add balance, ledger history |
| Add Balance | Preset/custom amount → PayCheck Checkout |
| Products | Demo catalog + Buy Now |
| Checkout | Uses existing `POST /api/v1/pay/init` → `/pay/:token` → `checkout.html` |
| Orders | pending / paid / failed / cancelled |
| Transactions | wallet_recharge, product_purchase |

**Not in v1:** commission callback, type callback, refund, settlement, webhook retry, analytics.

## Architecture

```
backend/demo-merchant/
├── config.js              # Environment
├── index.js               # mountEarly (webhook) + mount (API + UI)
├── middleware/auth.js       # JWT session
├── routes/                # HTTP layer
├── services/              # Business logic
│   └── paychek-client.js  # Thin client — calls PayCheck APIs only
├── db/migrate.sql         # Shared DB tables
└── public/                # Merchant UI
```

**Rules enforced:**
- Does **not** modify `backend/payment/` or checkout UI
- Does **not** duplicate payment logic
- Reuses shared MySQL database (Prisma models in `prisma/schema.prisma`)
- Receives `MerchantCallbackV1` webhooks to finalize orders/wallet

## Setup

### 1. Database

```bash
cd backend
npm run demo-merchant:migrate
npm run demo-merchant:seed
# Or: npx prisma db push   (includes demo_merchant_* models)
npx prisma generate
```

### 2. Environment

Add to `backend/.env`:

```env
# PayCheck merchant credentials (gateway_layouts row)
DEMO_MERCHANT_PAYCHEK_API_KEY=your_api_key
DEMO_MERCHANT_PAYCHEK_API_SECRET=your_api_secret

# Public URL PayCheck can reach for callbacks (no trailing slash)
DEMO_MERCHANT_PUBLIC_URL=http://YOUR_LAN_IP:3000

# Optional
DEMO_MERCHANT_JWT_SECRET=change-me-in-production
DEMO_MERCHANT_PAYCHEK_API_URL=http://127.0.0.1:3000
```

Point the merchant's `gateway_layouts.callback_url` to:

```
{DEMO_MERCHANT_PUBLIC_URL}/demo-merchant/api/webhooks/paychek
```

Or pass `callbackUrl` per payment (demo app does this automatically).

### 3. Run

```bash
cd backend
npm start
```

Open: **http://localhost:3000/demo-merchant/**

## Payment flow

1. User selects **Add Balance** or **Buy Now** on a product.
2. Demo app creates a local order (`pending`) and calls:

   `POST /api/v1/pay/init` with `channel: "paycheck"`, `orderId`, `successUrl`, `cancelUrl`, `callbackUrl`.

3. User is redirected to `checkoutUrl` (`/pay/:token` → existing PayCheck Checkout).
4. On success, PayCheck sends `MerchantCallbackV1` to the demo webhook (HMAC `X-Paychek-Signature`).
5. Demo app credits wallet (recharge) or marks order paid (product).
6. Success page polls `POST /demo-merchant/api/payments/confirm` as a fallback.

## API reference

| Method | Path | Auth |
|--------|------|------|
| POST | `/demo-merchant/api/auth/register` | No |
| POST | `/demo-merchant/api/auth/login` | No |
| POST | `/demo-merchant/api/auth/logout` | JWT |
| GET | `/demo-merchant/api/auth/session` | JWT |
| GET | `/demo-merchant/api/dashboard` | JWT |
| GET | `/demo-merchant/api/wallet/balance` | JWT |
| GET | `/demo-merchant/api/wallet/history` | JWT |
| GET | `/demo-merchant/api/payments/presets` | JWT |
| POST | `/demo-merchant/api/payments/wallet-recharge` | JWT |
| POST | `/demo-merchant/api/payments/product-checkout` | JWT |
| POST | `/demo-merchant/api/payments/confirm` | JWT |
| GET | `/demo-merchant/api/products` | No |
| GET | `/demo-merchant/api/orders` | JWT |
| GET | `/demo-merchant/api/transactions` | JWT |
| POST | `/demo-merchant/api/webhooks/paychek` | HMAC |

## Integration note

This project is mounted in `backend/app.js` with minimal wiring:

- `mountEarly()` — webhook raw body (before `express.json`)
- `mount()` — API + static UI (before 404 handler)

No changes to payment engine, checkout JavaScript, or callback architecture.
