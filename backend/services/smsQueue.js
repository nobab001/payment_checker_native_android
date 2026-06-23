const { Queue } = require('bullmq');
const Redis = require('ioredis');

const connection = new Redis({
  host: process.env.REDIS_HOST || '127.0.0.1',
  port: process.env.REDIS_PORT || 6379,
  maxRetriesPerRequest: null, // Required by BullMQ
});

const smsQueue = new Queue('smsIngestQueue', { connection });

module.exports = { smsQueue, connection };
