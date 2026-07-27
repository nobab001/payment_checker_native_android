# Payment Checker — Project Context

## Product

**Payment Checker (PayChek)** is an SMS-based payment verification platform. Merchants connect websites and gateways; Android devices receive MFS SMS (bKash, Nagad, Rocket, Upay, and custom templates); the backend matches SMS to checkout sessions and notifies merchants via callbacks.

Public site / API host: **https://paycheckbd.com/**

## Goals

- Reliable SMS capture and matching under real-device constraints
- Merchant-facing websites, API keys, checkout pages, and settlement tooling
- Admin controls for templates, billing, devices, and abuse prevention
- Premium, trustworthy mobile UX for operators and merchants

## System overview

```
┌─────────────────┐     HTTPS/JWT      ┌──────────────────────┐
│  Android App    │ ◄────────────────► │  Node Express API    │
│  Compose client │   SMS heartbeat    │  Prisma → MySQL      │
│  SMS + workers  │                    │  Redis / workers     │
└─────────────────┘                    └──────────┬───────────┘
                                                  │
                     ┌────────────────────────────┼────────────────────────────┐
                     ▼                            ▼                            ▼
              Checkout JS                  Merchant webhook              Official website
           (hosted by API)                 (callback outbox)             + APK download
```

## Repository layout

| Path | Role |
|------|------|
| `app/` | Android Gradle project (`online.paychek.app`) |
| `backend/` | API, payment engine, workers, static checkout/official site |
| `docs/` | Engineering handbook (this tree) |
| `.cursor/rules/` | AI enforcement rules |

## Environments

| Env | App `BASE_URL` | Notes |
|-----|----------------|-------|
| Local emulator | `http://10.0.2.2:3000/` | Maps to host localhost |
| LAN device | `http://192.168.x.x:3000/` | Same Wi-Fi as PC |
| Production | `https://paycheckbd.com/` | VPS + PM2 + Nginx |

Configured in `app/.../config/AppConfig.kt`.

## Core domains

1. **Auth & devices** — OTP/email login, device binding, admin bypass, PIN/security gate
2. **SMS pipeline** — templates, history, parse failures, heartbeat, presence
3. **Websites & gateways** — layouts, methods, purpose (`add_balance` / `payment` / `both`)
4. **Checkout & sessions** — init, provider UI, verify, expire pending approvals
5. **Merchant callbacks** — signed/retryable outbox delivery
6. **Billing** — subscription/addon plans, limits
7. **Admin** — templates, users, branding assets, maintenance mode

## Quality bar

Production-ready means: validated inputs, explicit authz, observable failures, reversible deploys, and UI that matches the design system. Every feature should be explainable from this handbook.
