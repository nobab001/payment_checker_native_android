# PayChek Engineering Handbook

This directory is the **source of truth** for humans and AI agents working on Payment Checker.

Before implementing features, read the relevant section. Cursor rules under `.cursor/rules/` enforce the same policy.

## Start here

1. [Project context](project/project-context.md)
2. [Design system](design/design-system.md)
3. [Android architecture](android/android-architecture.md)
4. [Backend architecture](backend/backend-architecture.md)
5. [Deployment — VPS](deployment/vps.md)

## Index

### Project
- [project-context.md](project/project-context.md)

### Design
- [design-system.md](design/design-system.md)
- [brand-guideline.md](design/brand-guideline.md)
- [color-system.md](design/color-system.md)
- [typography.md](design/typography.md)
- [component-library.md](design/component-library.md)
- [animation.md](design/animation.md)

### Android
- [android-architecture.md](android/android-architecture.md)
- [accessibility.md](android/accessibility.md)
- [services.md](android/services.md)

### Backend
- [backend-architecture.md](backend/backend-architecture.md)
- [database.md](backend/database.md)
- [folder-structure.md](backend/folder-structure.md)

### API
- [api-guideline.md](api/api-guideline.md)
- [authentication.md](api/authentication.md)
- [callback-system.md](api/callback-system.md)

### Security
- [security-policy.md](security/security-policy.md)
- [hmac.md](security/hmac.md)
- [token-system.md](security/token-system.md)

### Deployment
- [vps.md](deployment/vps.md)
- [github-workflow.md](deployment/github-workflow.md)
- [staging.md](deployment/staging.md)
- [production.md](deployment/production.md)

## Cursor rules

| File | Topic |
|------|-------|
| `00-project-context.mdc` | Identity & pre-reads |
| `01-ui-ux.mdc` | UI/UX |
| `02-design-system.mdc` | Tokens |
| `03-android.mdc` | Android |
| `04-backend.mdc` | Backend |
| `05-api.mdc` | API contracts |
| `06-security.mdc` | Security |
| `07-deployment.mdc` | Deploy follow-ups |
| `08-git-workflow.mdc` | Git |
| `09-code-quality.mdc` | Quality |
| `10-ai-behaviour.mdc` | Agent behaviour |

Existing specialized rules (kept): `deploy-follow-up.mdc`, `checkout-ux-requirements.mdc`.
