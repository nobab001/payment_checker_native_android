const { Queue } = require('bullmq');
const { getRedisClient } = require('./redisClient');

const connection = getRedisClient();

const smsQueue = new Queue('smsIngestQueue', { connection });

module.exports = { smsQueue, connection };
