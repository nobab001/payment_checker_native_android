# Backend Architecture

## Runtime

- **Runtime:** Node.js  
- **Framework:** Express (`backend/app.js` and related bootstrap)  
- **ORM:** Prisma (`backend/prisma/schema.prisma`) → **MySQL**  
- **Cache / realtime aides:** Redis (presence, caching — see `services/` and presence workers)  
- **Process manager (prod):** PM2 (`payment-checker-api` on VPS)

## Layering

```
HTTP Request
  → routes/*Routes.js
    → controllers/*
      → services/*  (domain)
        → prisma / redis / payment engine
```

Payment-specific logic lives under `backend/payment/` (e.g. `payment-flow-engine.js`) and checkout static assets under `backend/public/js/checkout/`.

## Major modules

| Area | Locations |
|------|-----------|
| Auth / OTP | `controllers/authController.js`, `routes/authRoutes.js` |
| Websites | `websiteController.js`, `websiteRoutes.js` |
| Gateways | `gatewayController.js`, `gatewayRoutes.js` |
| Payments / checkout | `paymentController.js`, `checkoutController.js`, `payment/*` |
| Admin | `adminController.js`, `adminRoutes.js` |
| Heartbeat / presence | `heartbeatController.js`, `services/presenceV25/` |
| SMS worker | `workers/smsWorker.js` |
| Merchant callback | `services/merchantCallback.js`, outbox table |
| Official website / demo | `official-website/` |

## Configuration

- Environment via `.env` (never commit secrets). Example keys documented in `backend/.env.example`.
- Production shared env on VPS: `/var/www/payment-checker/shared/.env`.

## Cross-cutting services

- `commPolicyService.js` — commission / campaign policy  
- `checkoutDataService.js` — checkout payloads  
- `dataSyncCache.js` — sync/caching  
- `pendingApprovalExpiry.js` — expire pending approvals  
- `websitePurpose.js` — website purpose validation (`add_balance` / `payment` / `both`)

## Error handling

Controllers should catch domain errors, map to HTTP status, and return JSON `{ success, message, ... }` consistent with existing clients. Do not leak stack traces to clients in production.

## Extending the backend

1. Add/adjust Prisma model if persistence changes  
2. Service method for business rules  
3. Controller + route  
4. Update `docs/api/*`  
5. Restart/reload process per deployment docs  
