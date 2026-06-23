const rateLimit = require('express-rate-limit');
const { RedisStore } = require('rate-limit-redis');
const Redis = require('ioredis');

// Connect to Redis
const redisClient = new Redis({
  host: process.env.REDIS_HOST || '127.0.0.1',
  port: process.env.REDIS_PORT || 6379,
});

const apiRateLimiter = rateLimit({
  windowMs: 1 * 60 * 1000, // 1 minute window
  max: 60, // Limit each user to 60 requests per window
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => {
    // Strictly track by user ID if authenticated
    if (req.user && req.user.userId) {
      return `rate-limit:user:${req.user.userId}`;
    }
    return req.ip; // express-rate-limit correctly handles req.ip natively
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

module.exports = apiRateLimiter;
