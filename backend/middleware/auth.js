const jwt = require('jsonwebtoken');
const JWT_SECRET = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';
const prisma = require('../db/prisma');

function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  // Token should be formatted as "Bearer <token>"
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ error: 'Invalid or expired access token' });
    }
    req.user = user;
    next();
  });
}

// device_role=restricted marks a staff physical device (owner PIN at login).
// In-app API access is not gated here; staff devices have full owner-equivalent access after login.
async function restrictDevice(req, res, next) {
  try {
    const userId = req.user.userId;
    const deviceId = req.user.deviceId;

    if (!userId || !deviceId) {
      return res.status(403).json({ error: 'Access denied: Device context missing.' });
    }

    const device = await prisma.registered_devices.findFirst({
      where: { user_id: userId, device_id: deviceId },
      select: { device_role: true }
    });

    if (device && device.device_role === 'restricted') {
      return res.status(403).json({ success: false, error: 'Access denied: Restricted devices cannot modify settings.' });
    }

    next();
  } catch (err) {
    console.error('[Middleware] restrictDevice error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

authenticateToken.restrictDevice = restrictDevice;

async function requireOwnerCaller(req, res, next) {
  try {
    const userId = req.user.userId;
    const deviceId = req.user.deviceId;
    if (!userId || !deviceId) {
      return res.status(403).json({ success: false, error: 'Access denied: Device context missing.' });
    }
    const device = await prisma.registered_devices.findFirst({
      where: { user_id: Number(userId), device_id: deviceId },
      select: { device_role: true },
    });
    if (!device || device.device_role !== 'owner') {
      return res.status(403).json({ success: false, error: 'শুধুমাত্র মালিক ডিভাইস এই কাজ করতে পারবে।' });
    }
    next();
  } catch (err) {
    console.error('[Middleware] requireOwnerCaller error:', err);
    return res.status(500).json({ error: 'Internal Server Error' });
  }
}

authenticateToken.requireOwnerCaller = requireOwnerCaller;
module.exports = authenticateToken;
