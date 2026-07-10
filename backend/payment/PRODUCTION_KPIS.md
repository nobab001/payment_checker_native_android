# Production KPIs — Payment Platform Health Score

> Monitor these continuously during Pilot and Production.  
> **Source:** `GET /api/v1/payment/metrics` + DB queries + merchant webhook logs.

---

## Core KPIs

| KPI | Definition | Source | Pilot Warning | Incident |
|-----|------------|--------|---------------|----------|
| Payment Success Rate | `completed / (completed + failed + expired)` | `payment_sessions` | < 95% (24h) | < 90% (1h) |
| Merchant Callback Success Rate | `outbox sent / (sent + dead)` | `merchant_callback_outbox` | < 98% | < 95% |
| Duplicate Callback Count | Merchant HTTP > 1 per session | Webhook logs / mock | > 0 | > 0 (P0) |
| Avg Callback Latency | Mean `merchant_callback` stage ms | metrics latencies | > 3s | > 10s |
| p95 Latency | p95 per stage | `/api/v1/payment/metrics` | 2× baseline | 3× baseline |
| Outbox Pending Count | `status IN (pending, processing)` | metrics / SQL | > 10 for 15m | > 50 |
| Dead Queue Count | outbox `dead` + retry dead queue | metrics | any growth | +5/hour |
| Circuit Breaker OPEN | Provider breaker state | metrics `circuitBreakers` | any OPEN > 5m | OPEN > 15m |
| Retry Count | Sum outbox `attempts` / retry logs | outbox + logs | spike 3× avg | sustained spike |
| Session Expired % | `expired / created` | `payment_sessions` | > 20% | > 40% |
| Avg Redirect Time | `redirect` stage p50 | metrics | > 2s | > 5s |

---

## Metrics Endpoint

```bash
curl -s -H "X-Payment-Metrics-Key: $PAYMENT_METRICS_API_KEY" \
  https://YOUR-HOST/api/v1/payment/metrics | jq '.metrics'
```

Capture baseline after Golden Test Suite:

```bash
curl -s -H "X-Payment-Metrics-Key: $KEY" \
  https://staging.example/api/v1/payment/metrics \
  > baseline-$(date +%Y%m%d).json
```

---

## SQL Helpers

```sql
-- Success rate (24h)
SELECT
  SUM(status = 'completed') AS ok,
  SUM(status IN ('failed','expired')) AS not_ok,
  COUNT(*) AS total
FROM payment_sessions
WHERE created_at > NOW() - INTERVAL 24 HOUR;

-- Outbox health
SELECT status, COUNT(*) FROM merchant_callback_outbox GROUP BY status;

-- Stuck pending (> 10 min)
SELECT * FROM merchant_callback_outbox
WHERE status IN ('pending','processing')
  AND created_at < NOW() - INTERVAL 10 MINUTE;
```

---

## Health Score (simple)

```
Score = 100
  - 20 if Payment Success Rate < 95%
  - 30 if Duplicate Callback > 0
  - 15 if Outbox pending > 10 (15m)
  - 15 if Circuit OPEN > 5m
  - 10 if p95 > 2× baseline
```

| Score | Status |
|-------|--------|
| 90–100 | Healthy |
| 70–89 | Degraded — investigate |
| < 70 | Incident — consider rollback |

---

## Version

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-07-06 | Initial KPI definitions for v3.0 pilot |
