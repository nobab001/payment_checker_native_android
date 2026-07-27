# API Guideline

## Base

- Production: `https://paycheckbd.com/api/`
- App constant: `AppConfig.API_BASE_URL`

Route modules live in `backend/routes/` (`authRoutes`, `websiteRoutes`, `gatewayRoutes`, `paymentRoutes`, `checkoutRoutes`, `adminRoutes`, `billingRoutes`, `pinRoutes`, `credentialRoutes`, `paymentFlowRoutes`, `paymentMetricsRoutes`, …).

## Contract standard

Every endpoint (new or changed) should be describable as:

### Template

**`METHOD /api/resource`**

**Auth:** Bearer JWT | API key (pk/sk) | Public | Admin  

**Request**

```json
{ "field": "value" }
```

**Validation**

- required fields, formats (BD phone 11-digit `01…`, email), enums

**Success `200`**

```json
{ "success": true, "data": {} }
```

**Error `4xx/5xx`**

```json
{ "success": false, "message": "Human readable reason" }
```

**Status codes**

| Code | Meaning |
|------|---------|
| 200 | OK |
| 400 | Validation / bad request |
| 401 | Unauthenticated |
| 403 | Forbidden (device bound, role, abuse) |
| 404 | Missing resource |
| 409 | Conflict |
| 429 | Rate limited |
| 500 | Unexpected server error |

## Client expectations

Android DTOs under `data/remote/dto` must stay in sync with responses. Prefer additive JSON fields for backward compatibility.

## Documentation surfaces

- In-app API docs screen (`ApiDocsScreen` / catalog)
- Public docs pages under `backend/public/docs/`
- This handbook (`docs/api/`)

When behavior changes, update at least one human-facing doc plus DTOs.
