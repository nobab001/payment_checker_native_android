# Architecture Decision Records (ADR)

> **Format:** ADR-NNN — Title — Status — Date  
> **Rule:** Significant payment-platform decisions are recorded here.  
> **Change:** Supersede with new ADR; do not delete history.

---

## ADR-001 — Checkout UI Frozen

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Checkout renderer is stable and shared across merchants. Provider-specific logic in UI creates regression risk.

**Decision:** Freeze `backend/public/js/checkout/**`. Future providers integrate via backend Registry + Adapter only.

**Consequences:** UI changes require explicit unfreeze approval. Payment behavior changes go through Platform Layer.

---

## ADR-002 — Registry-Driven Provider Architecture

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** `if (provider === 'bkash')` does not scale to Nagad, SSLCommerz, Bank, Card.

**Decision:** Provider Registry + Adapter Pattern. `ProviderFactory` resolves by id; each provider is a self-contained adapter implementing `BaseProvider`.

**Consequences:** New provider = new adapter file + registry entry. No core engine forks.

---

## ADR-003 — PaymentSessionEngine Sole Owner of Sessions

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Multiple writers to `payment_sessions` caused state inconsistency risk.

**Decision:** `PaymentSessionEngine` is the only module that mutates `payment_sessions`. State transitions go through `payment-state-machine.js`.

**Consequences:** Callback and engine code must call session engine methods, not raw Prisma updates (except atomic outbox transaction in callback-engine).

---

## ADR-004 — MerchantCallbackV1 Frozen

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Merchants integrate against callback JSON shape. Silent field changes break integrations.

**Decision:** Freeze `MerchantCallbackV1`. Additive changes → V1.1. Breaking changes → V2 with parallel run period. See `CALLBACK_VERSIONING.md` + `DEPRECATION_POLICY.md`.

**Consequences:** No root field changes without version bump and merchant notice.

---

## ADR-005 — Merchant Callback Outbox Pattern

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Direct HTTP callback after DB commit loses messages on worker crash or merchant downtime.

**Decision:** `merchant_callback_outbox` table + worker. Payment SUCCESS and outbox insert in same Prisma transaction. Delivery via worker + RetryEngine.

**Consequences:** Merchant callback is eventually consistent; payment `completed` may precede merchant HTTP by seconds. Monitor `outbox.pending` and `dead`.

---

## ADR-006 — Three-Layer Webhook Idempotency

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Duplicate gateway callbacks can double-settle or double-notify merchants.

**Decision:** Layer 1 Redis idempotency lock. Layer 2 DB unique `(website_id, trx_id)`. Layer 3 state machine terminal guards. Layer 4 outbox `delivery_key` unique.

**Consequences:** Redis down falls back to memory (single instance only) — documented in `RISK_REGISTER.md`.

---

## ADR-007 — Thin Controllers, Fat Engines

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Business logic in Express controllers is hard to test and reuse.

**Decision:** Controllers delegate to `PaymentEngine`, `PaymentFlowEngine`, `CallbackEngine`. Controllers handle HTTP only.

**Consequences:** New flows start in `payment/engine/` or `payment/callback/`, not routes.

---

## ADR-008 — Validation Before Phase-3C Commission

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Commission on an unproven payment layer multiplies debugging cost.

**Decision:** Phase-3C blocked until PAG + Exit Criteria pass (`PRODUCTION_ACCEPTANCE_GATE.md`). Commission must be Registry-driven Rule Engine, not provider if-chains.

**Consequences:** No commission code during Phase-3B.6 freeze window.

---

## ADR-009 — Release Evidence Package Required

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** "It worked on my machine" is insufficient for payment releases.

**Decision:** Every release has a folder under `payment/releases/<version>/` per `RELEASE_EVIDENCE_PACKAGE.md`. **Release Approved** blocked without bundle.

**Consequences:** Operational overhead per release; long-term auditability.

---

## ADR-010 — Documentation Freeze via PR

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Frozen contracts drift without review.

**Decision:** PaymentContext v1, MerchantCallbackV1, event names, error codes, registry schema — **no change without PR**. See `DOCUMENTATION_FREEZE.md`.

**Consequences:** Slower changes; safer production.

---

## ADR-011 — Foundation Freeze v3.0.0

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2026-07-06 |

**Context:** Need a single baseline reference for "what is frozen" before pilot and Phase-3C.

**Decision:** Declare `FOUNDATION_FREEZE_v3.md`. No architecture change during pilot window. Exit = PAG + lessons learned + GO.

**Consequences:** Pilot measures stable system; Phase-3C blocked until exit.

---

## Reserved ADRs (do not assign until work starts)

| ID | Title | Planned phase |
|----|-------|---------------|
| **ADR-012** | Commission Rule Engine | Phase-3C / v3.1.0 |
| **ADR-013** | Settlement Engine | Phase-3C / v3.1.0 |
| **ADR-014** | Dead Letter Queue Dashboard | v3.3.0 Operations |
| **ADR-015** | Trace Explorer | v3.3.0 Operations |
| **ADR-016** | High Availability Architecture | v4.0.0 |

When implementing, move reserved ADR to **Accepted** with full context/decision/consequences. Do not skip numbers.

---

## Template (for new ADRs)

```markdown
## ADR-NNN — Title

| Field | Value |
|-------|-------|
| **Status** | Proposed | Accepted | Superseded |
| **Date** | YYYY-MM-DD |

**Context:** ...

**Decision:** ...

**Consequences:** ...
```

---

## Version

| Version | Date |
|---------|------|
| 1.0 | 2026-07-06 |
