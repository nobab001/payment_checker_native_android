# Payment Events — FROZEN v1.0

> **Status:** FROZEN — do not rename events. Add new events only with explicit v2 namespace.

Implementation: `payment/events/payment-events.js`  
Bus API: `emitPaymentEvent()` / `onPaymentEvent()`

---

## Frozen Event Names

| Constant | Event String | When Emitted |
|----------|--------------|--------------|
| `PAYMENT_CREATED` | `PaymentCreated` | Session created after initiate |
| `PAYMENT_REDIRECTED` | `PaymentRedirected` | Customer redirected to gateway |
| `GATEWAY_CALLBACK_RECEIVED` | `GatewayCallbackReceived` | Inbound provider callback received |
| `PAYMENT_VERIFIED` | `PaymentVerified` | `adapter.verify()` succeeded |
| `MERCHANT_CALLBACK_SENT` | `MerchantCallbackSent` | Outbound merchant webhook delivered |
| `PAYMENT_COMPLETED` | `PaymentCompleted` | Terminal success |
| `PAYMENT_FAILED` | `PaymentFailed` | Terminal failure |
| `PAYMENT_EXPIRED` | `PaymentExpired` | Session TTL exceeded |
| `PAYMENT_CANCELLED` | `PaymentCancelled` | User / merchant cancel |

---

## Rules

1. Event string values are **immutable** (e.g. always `PaymentCreated`, never `payment.created`).
2. Every event payload SHOULD include `traceId` when available.
3. Listeners MUST be side-effect only — never change API response bodies.
4. New events in Phase-3B+ require `LIFECYCLE_SPEC.md` version bump.

---

## Version History

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial freeze — 9 lifecycle events |
