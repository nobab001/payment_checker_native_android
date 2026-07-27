# Security Policy

## Objectives

Protect merchant funds flows, user PII, device integrity signals, and API secrets across Android, API, and VPS.

## Principles

1. Least privilege — tokens and keys scoped to role/resource  
2. Defense in depth — TLS + authn + authz + validation + rate limits  
3. Secure defaults — system theme ok; debug never ships production secrets  
4. Assume hostile clients — never trust mobile input alone  

## Controls checklist

| Control | Requirement |
|---------|-------------|
| Transport | HTTPS in production |
| Auth | JWT bearer for app; API keys for merchant server calls |
| Integrity | HMAC on SMS/device sensitive paths |
| Storage (mobile) | Encrypted prefs / Keystore-backed where used |
| Storage (server) | Env secrets; DB least-privilege user |
| Input | Validate types/lengths/enums; Prisma parameterized access |
| Output | No stack traces or secrets in API errors |
| Abuse | Device bind limits, trial logs, rate limits |
| Admin | Strong admin secret; audit sensitive actions |

## Prohibited

- Committing `.env`, private keys, or production dumps to git  
- Disabling TLS verification  
- Logging OTP codes or raw HMAC secrets  
- Running arbitrary SQL from user strings  

## Incident response (lightweight)

1. Rotate leaked secrets (JWT secret, HMAC, API keys, SMTP)  
2. Invalidate sessions if token secret rotated  
3. Review `audit_logs` / server logs  
4. Patch and deploy via production checklist  

## Related

- `hmac.md` — SMS/device integrity  
- `token-system.md` — JWT & API keys  
- `.cursor/rules/06-security.mdc`  
