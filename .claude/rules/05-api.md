<!-- Synced from .cursor/rules/05-api.mdc -->

# API Rules

Read: `docs/api/api-guideline.md`, `docs/api/authentication.md`, `docs/api/callback-system.md`

## Every endpoint change must document

1. Method + path
2. Auth (JWT bearer / API key / public / HMAC)
3. Request body / query schema + validation rules
4. Success response example
5. Error response example + status codes
6. Side effects (DB writes, callbacks, SMS)

## Conventions

- Base: `/api/...` (app uses `API_BASE_URL`)
- Prefer versioned or clearly named resources; do not break existing merchant integrations without migration plan.
- Merchant callbacks: idempotent where possible; use outbox pattern (`merchant_callback_outbox`) when retrying.
- Public checkout init endpoints must validate website purpose, amounts, and credentials carefully.

## Security on every write

- Authn + authz (account ownership / admin role)
- Input sanitization
- No sensitive fields in error messages

When adding routes, register in the matching `backend/routes/*Routes.js` and keep controller thin.
