const express = require('express');
const cors = require('cors');
const cron = require('node-cron');
const { query } = require('./db/connection');
require('dotenv').config();

const authRoutes     = require('./routes/authRoutes');
const paymentRoutes  = require('./routes/paymentRoutes');
const checkoutRoutes = require('./routes/checkoutRoutes');
const gatewayRoutes  = require('./routes/gatewayRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

// Enable CORS for frontend API requests
app.use(cors());

// Serve static webpage assets (like public/checkout.html)
app.use(express.static('public'));

// Parse incoming JSON body payloads
app.use(express.json());

// Log incoming REST API requests
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// Root check route
app.get('/health', (req, res) => {
  res.json({ status: 'UP', timestamp: new Date() });
});

// Mount Authentication & Device Trial Routes
app.use('/api', authRoutes);
app.use('/api', paymentRoutes);
app.use('/api', checkoutRoutes);
app.use('/api', gatewayRoutes);

// General 404 Route handler
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint Not Found' });
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Express Error Handler caught:', err);
  res.status(500).json({ error: 'Internal Server Error' });
});

// Schedule a daily midnight task to reset email limits
cron.schedule('0 0 * * *', async () => {
  try {
    await query('UPDATE email_accounts SET sent_today = 0');
    console.log('[CRON] Reset daily email send limits successfully.');
  } catch (err) {
    console.error('[CRON] Error resetting daily email limits:', err);
  }
});

// Start listening for connections
app.listen(PORT, () => {
  console.log(`=============================================`);
  console.log(` Payment Checker API Server running on port ${PORT}`);
  console.log(` Database Target: ${process.env.DB_HOST}:${process.env.DB_PORT || 3306}`);
  console.log(` Database Name  : ${process.env.DB_NAME}`);
  console.log(`=============================================`);
});
