const express = require('express');
const router  = express.Router();
const cred    = require('../controllers/credentialController');
const auth    = require('../middleware/auth');

// GET    /api/credentials            → ইউজারের সব credentials
router.get('/credentials',              auth, cred.listCredentials);

// POST   /api/credentials/send-otp   → নতুন credential-এ OTP পাঠাও
router.post('/credentials/send-otp',    auth, cred.sendCredentialOtp);

// POST   /api/credentials/verify     → OTP যাচাই করে credential verify করো
router.post('/credentials/verify',      auth, cred.verifyCredential);

// DELETE /api/credentials/:id        → একটি credential মুছে ফেলো
router.delete('/credentials/:id',       auth, cred.removeCredential);

module.exports = router;
