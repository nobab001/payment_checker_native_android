# Token System

## JWT (app sessions)

- Issued after OTP/admin verify  
- Carried as `Authorization: Bearer <token>`  
- Claims include user identity and role (as implemented)  
- Stored on device via encrypted preferences  
- Server must verify signature and expiry on protected routes  

### Rotation

Rotating JWT signing secret invalidates existing sessions — coordinate with a forced re-login notice if done in production.

## API keys (merchant websites)

| Key | Exposure | Use |
|-----|----------|-----|
| Publishable `pk_…` | Allowed in client init contexts that are designed for it | Identify website |
| Secret `sk_…` | Server-side only | Privileged merchant API calls |

Keys are created/rotated in website settings. Leaked `sk_` requires immediate rotate + audit of sessions.

## Device & profile flags

Alongside JWT, the app persists flags such as profile completion, device approval, owner device, setup completed. Treat them as UX accelerators — **authorization still happens on the server**.

## PIN / security gate

Local PIN / security gate can lock the UI after resume. Remains complementary to server auth — not a replacement.

## Admin tokens

Admin sessions are high privilege. Prefer shorter idle tolerance and stronger secrets (`ADMIN_SECRET_USERNAME` and related env). Monitor admin route access.
