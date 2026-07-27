# Callback / Webhook System

## Purpose

Notify merchant systems when payment sessions reach terminal or important intermediate states (paid, failed, expired, etc.).

## Outbox pattern

Table: `merchant_callback_outbox`

Flow:

1. Payment engine decides a callback is required  
2. Persist outbox row (payload, URL, status, attempts)  
3. Delivery worker/service attempts HTTP POST  
4. On success → mark delivered; on failure → retry with backoff  

Implementation reference: `backend/services/merchantCallback.js` (and related payment engine hooks).

## Merchant expectations

- **Idempotency:** merchants should key on session/transaction id and ignore duplicates.
- **HTTPS** endpoints only in production.
- **Fast ack:** respond `2xx` quickly; process asynchronously on merchant side.
- Verify signatures/secrets if the deployment enables signed callbacks (see security docs / website credentials).

## Example (illustrative)

**Request to merchant**

```http
POST https://merchant.example.com/hooks/paychek
Content-Type: application/json

{
  "event": "payment.completed",
  "sessionId": "ses_...",
  "amount": 500,
  "status": "paid",
  "provider": "bkash"
}
```

**Merchant response**

```http
HTTP/1.1 200 OK
```

## Failure modes

| Issue | Handling |
|-------|----------|
| Timeout / 5xx | Retry via outbox |
| 410 / permanent reject | Stop retry; alert/log |
| Bad URL config | Fail validation at website settings save time |

## Change policy

Callback payload changes require a version field or additive fields only. Breaking changes need merchant communication and dual-publish window.
