/**
 * GET /api/v1/payment/metrics — payment health dashboard (admin / monitoring).
 */
const paymentMonitor = require('../payment/monitoring/payment-monitor');

function sanitizeMetrics(snapshot) {
  const providers = (snapshot.providers || []).map((p) => ({
    providerId: p.providerId,
    health: p.health ? { status: p.health.status, message: p.health.message } : null,
    ping: p.ping ? { ok: p.ping.ok, latencyMs: p.ping.latencyMs } : null,
    error: p.error || null,
  }));

  return {
    at: snapshot.at,
    latencies: snapshot.latencies,
    providers,
    circuitBreakers: snapshot.circuitBreakers,
    retryDeadQueue: snapshot.retryDeadQueue,
    outbox: snapshot.outbox,
  };
}

async function getPaymentMetrics(req, res) {
  try {
    const snapshot = await paymentMonitor.getDashboardMetrics();
    return res.json({
      success: true,
      auth: req.metricsAuth || 'unknown',
      metrics: sanitizeMetrics(snapshot),
    });
  } catch (err) {
    console.error('[PAYMENT METRICS]', err);
    return res.status(500).json({ success: false, error: 'INTERNAL_ERROR' });
  }
}

module.exports = { getPaymentMetrics, sanitizeMetrics };
