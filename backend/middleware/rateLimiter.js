const rateLimit = require('express-rate-limit');
const { RedisStore } = require('rate-limit-redis');
const { getRedisClient } = require('../services/redisClient');

const redisClient = getRedisClient();

const apiRateLimiter = rateLimit({
  windowMs: 1 * 60 * 1000, // 1 minute window
  max: 60, // Limit each user to 60 requests per window
  standardHeaders: true,
  legacyHeaders: false,
  validate: { xForwardedForHeader: false, default: true, ip: false, keyGeneratorIpFallback: false },
  keyGenerator: (req) => {
    return req.user?.userId || req.ip;
  },
  handler: (req, res) => {
    res.status(429).json({
      error: 'TOO_MANY_REQUESTS',
      message: 'Too many requests from this account, please try again after a minute.'
    });
  },
  store: new RedisStore({
    sendCommand: (...args) => redisClient.call(...args),
  }),
});

// Strict limiter for sensitive unauthenticated/OTP/PIN endpoints.
// Keyed by IP + contact (or IP alone) to throttle brute-force and OTP spam.
const otpRateLimiter = rateLimit({
  windowMs: 10 * 60 * 1000, // 10 minute window
  max: 10, // Limit each key to 10 requests per window
  standardHeaders: true,
  legacyHeaders: false,
  validate: { xForwardedForHeader: false, default: true, ip: false, keyGeneratorIpFallback: false },
  keyGenerator: (req) => {
    const contact = req.body?.contact || req.body?.phone || req.body?.email || '';
    return `${req.ip}:${contact}`;
  },
  handler: (req, res) => {
    res.status(429).json({
      error: 'TOO_MANY_REQUESTS',
      message: 'Too many attempts. Please try again after a few minutes.'
    });
  },
  store: new RedisStore({
    sendCommand: (...args) => redisClient.call(...args),
  }),
});

module.exports = apiRateLimiter;
module.exports.apiRateLimiter = apiRateLimiter;
module.exports.otpRateLimiter = otpRateLimiter;
