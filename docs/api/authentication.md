# Authentication

## Mobile app (operators / merchants)

1. User enters phone or email on Login  
2. Backend validates device eligibility (device bind / abuse gates)  
3. OTP issued (SMS/email) unless admin bypass username  
4. Verify OTP → JWT (and related device flags) returned  
5. App stores token via `SecurePreferences` and role/profile flags  
6. Subsequent API calls send Bearer token  

Relevant code: `LoginViewModel`, `authController`, `authRoutes`.

## Roles

- **user / merchant** — websites, devices, billing  
- **admin** — admin dashboard, global templates/config  
- Device roles (e.g. owner) influence PIN and privileges  

## Admin bypass

Special username from `global_config` / public config (`admin_secret_username`) with password-style OTP field. Treat as highly sensitive; never log the secret.

## API keys (websites)

Websites use public/secret key pairs (`pk_…`, `sk_…`) for server-side init and verification. Secret keys must never ship in mobile clients or public JS beyond intended publishable keys.

## Session invalidation

On logout / remote lock / deactivation, clear local secrets and stop privileged background work.

## Errors

| Case | Typical status |
|------|----------------|
| Bad OTP | 400/401 |
| Device already bound to other account | 403 + bound identities |
| Package limit exceeded | dialog / error channel |
| Maintenance mode | soft banner + restricted ops |

See also: `docs/security/token-system.md`.
