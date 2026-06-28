const Redis = require('ioredis');

let client = null;

function getRedisClient() {
  if (!client) {
    client = new Redis({
      host: process.env.REDIS_HOST || '127.0.0.1',
      port: parseInt(process.env.REDIS_PORT || '6379', 10),
      maxRetriesPerRequest: null,
      lazyConnect: false,
    });

    client.on('error', (err) => {
      console.warn('[Redis] Connection error:', err.message);
    });
  }
  return client;
}

module.exports = { getRedisClient };
