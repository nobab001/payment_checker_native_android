const { rateLimit, ipKeyGenerator } = require('express-rate-limit');

/** Gateway callback endpoints — per IP + token. */
const callbackRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  validate: { xForwardedForHeader: false, default: true },
  keyGenerator: (req) => `${ipKeyGenerator(req)}:${req.query?.token || req.params?.token || 'unknown'}`,
  handler: (req, res) => {
    res.status(429).json({
      success: false,
      error: 'TOO_MANY_CALLBACK_REQUESTS',
      errorCode: 'PAY_1020',
    });
  },
});

module.exports = callbackRateLimiter;
