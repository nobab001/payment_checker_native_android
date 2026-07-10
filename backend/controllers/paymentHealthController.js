/**
 * GET /api/v1/payment/health — payment layer readiness (no secrets).
 */
const prisma = require('../db/prisma');
const { getRedisClient } = require('../services/redisClient');
const paymentMonitor = require('../payment/monitoring/payment-monitor');
const { getSnapshot: getCircuitSnapshot } = require('../payment/reliability/circuit-breaker');
const { getPendingCount, getDeadCount } = require('../payment/reliability/merchant-callback-outbox');
const { getDeadQueue } = require('../payment/retry/retry-engine');

async function pingRedis() {
  try {
    const redis = getRedisClient();
    const pong = await Promise.race([
      redis.ping(),
      new Promise((_, reject) => setTimeout(() => reject(new Error('REDIS_TIMEOUT')), 2000)),
    ]);
    return { status: pong === 'PONG' ? 'UP' : 'DEGRADED', connected: pong === 'PONG' };
  } catch (err) {
    return { status: 'DOWN', connected: false, error: err.message, fallback: 'memory_idempotency' };
  }
}

async function pingDatabase() {
  try {
    await prisma.$queryRaw`SELECT 1`;
    return { status: 'UP', connected: true };
  } catch (err) {
    return { status: 'DOWN', connected: false, error: err.message };
  }
}

async function getPaymentHealth(req, res) {
  try {
    const [db, redis, providers] = await Promise.all([
      pingDatabase(),
      pingRedis(),
      paymentMonitor.runProviderProbes(),
    ]);

    const circuitBreakers = getCircuitSnapshot();
    const [outboxPending, outboxDead] = await Promise.all([
      getPendingCount().catch(() => -1),
      getDeadCount().catch(() => -1),
    ]);

    const providerSummary = providers.map((p) => ({
      providerId: p.providerId,
      up: !p.error && (p.health?.status === 'ok' || p.health?.status === 'configured'),
      health: p.health?.status || (p.error ? 'error' : 'unknown'),
      circuit: circuitBreakers[p.providerId]?.state || 'CLOSED',
    }));

    const allProvidersUp = providerSummary.every((p) => p.up);
    const circuitsClosed = providerSummary.every((p) => p.circuit === 'CLOSED');
    const overall = db.status === 'UP' && allProvidersUp && circuitsClosed ? 'UP' : 'DEGRADED';
    if (db.status === 'DOWN') {
      return res.status(503).json({
        success: false,
        status: 'DOWN',
        env: process.env.APP_ENV || 'unknown',
        at: new Date().toISOString(),
        checks: { database: db, redis, providers: providerSummary, circuitBreakers },
        outbox: { pending: outboxPending, dead: outboxDead },
        retryDeadQueue: getDeadQueue().length,
      });
    }

    return res.json({
      success: true,
      status: overall,
      env: process.env.APP_ENV || 'unknown',
      at: new Date().toISOString(),
      checks: {
        database: db,
        redis,
        providers: providerSummary,
        circuitBreakers,
      },
      outbox: { pending: outboxPending, dead: outboxDead },
      retryDeadQueue: getDeadQueue().length,
    });
  } catch (err) {
    console.error('[PAYMENT HEALTH]', err);
    return res.status(500).json({ success: false, status: 'DOWN', error: 'INTERNAL_ERROR' });
  }
}

module.exports = { getPaymentHealth };
