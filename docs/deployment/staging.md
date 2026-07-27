# Staging

## Purpose

Validate API, checkout, SMS matching, and Android clients against non-production data before touching live merchants.

## Recommended setup

| Item | Staging | Production |
|------|---------|------------|
| Domain | `staging.paycheckbd.com` (or tunnel) | `paycheckbd.com` |
| Database | Separate MySQL schema | Live MySQL |
| Redis | Separate DB index/instance | Live |
| APK `BASE_URL` | Staging URL | Production URL |
| SMS | Test templates / sandbox numbers | Real operators |
| Callbacks | Merchant webhook testers | Live merchant URLs |

## Practice

1. Deploy backend to staging first for schema/API changes.  
2. Install a staging-flavored APK (or temporarily point `BASE_URL` — never leave staging URL in production builds).  
3. Run checkout happy path + failure path.  
4. Only then schedule production deploy.

## If staging host does not exist yet

Use a protected local/LAN environment with a disposable DB dump, or a VPS subdirectory/port behind basic auth. Document the chosen staging URL here when provisioned.

## Data rules

Never copy production secret keys into developer laptops casually. Prefer anonymized DB dumps.
