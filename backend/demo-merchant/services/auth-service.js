const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const prisma = require('../../db/prisma');
const config = require('../config');
const { ROLES } = require('./roles');
const roleStore = require('./user-role-store');

const SALT_ROUNDS = 10;

function signToken(user) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      role: user.role || ROLES.USER,
      scope: 'demo_merchant',
    },
    config.jwtSecret,
    { expiresIn: config.jwtExpiresIn },
  );
}

function verifyToken(token) {
  return jwt.verify(token, config.jwtSecret);
}

function formatUser(user) {
  return {
    id: user.id,
    email: user.email,
    fullName: user.full_name,
    role: user.role || ROLES.USER,
    isAdmin: (user.role || ROLES.USER) === ROLES.ADMIN,
    createdAt: user.created_at,
  };
}

async function register({ email, password, fullName }) {
  const normalizedEmail = String(email || '').trim().toLowerCase();
  if (!normalizedEmail || !password || password.length < 6) {
    const err = new Error('Invalid registration data');
    err.code = 'VALIDATION_ERROR';
    throw err;
  }

  const existing = await prisma.demo_merchant_users.findUnique({ where: { email: normalizedEmail } });
  if (existing) {
    const err = new Error('Email already registered');
    err.code = 'EMAIL_EXISTS';
    throw err;
  }

  const role = await roleStore.resolveFirstUserRole();
  const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);

  const created = await prisma.demo_merchant_users.create({
    data: {
      email: normalizedEmail,
      password_hash: passwordHash,
      full_name: String(fullName || 'Demo Merchant').slice(0, 120),
      wallet: { create: { balance: 0 } },
    },
    select: { id: true, email: true, full_name: true, created_at: true },
  });

  await roleStore.setRole(created.id, role);
  created.role = role;

  const token = signToken(created);
  return { user: formatUser(created), token };
}

async function login({ email, password }) {
  const normalizedEmail = String(email || '').trim().toLowerCase();
  const user = await prisma.demo_merchant_users.findUnique({ where: { email: normalizedEmail } });
  if (!user) {
    const err = new Error('Invalid credentials');
    err.code = 'INVALID_CREDENTIALS';
    throw err;
  }

  const ok = await bcrypt.compare(password, user.password_hash);
  if (!ok) {
    const err = new Error('Invalid credentials');
    err.code = 'INVALID_CREDENTIALS';
    throw err;
  }

  user.role = await roleStore.getRoleByUserId(user.id);

  const token = signToken(user);
  return {
    user: formatUser(user),
    token,
  };
}

async function getSession(userId) {
  const user = await prisma.demo_merchant_users.findUnique({
    where: { id: userId },
    select: {
      id: true,
      email: true,
      full_name: true,
      created_at: true,
      wallet: { select: { balance: true, updated_at: true } },
    },
  });

  if (!user) {
    const err = new Error('User not found');
    err.code = 'NOT_FOUND';
    throw err;
  }

  user.role = await roleStore.getRoleByUserId(user.id);

  return {
    ...formatUser(user),
    walletBalance: Number(user.wallet?.balance || 0),
  };
}

async function resetPassword({ email, newPassword }) {
  const normalizedEmail = String(email || '').trim().toLowerCase();
  if (!normalizedEmail || !newPassword || newPassword.length < 6) {
    const err = new Error('Enter a valid email and password (min 6 characters)');
    err.code = 'VALIDATION_ERROR';
    throw err;
  }

  const user = await prisma.demo_merchant_users.findUnique({ where: { email: normalizedEmail } });
  if (!user) {
    const err = new Error('No account found with this email');
    err.code = 'NOT_FOUND';
    throw err;
  }

  const passwordHash = await bcrypt.hash(newPassword, SALT_ROUNDS);
  await prisma.demo_merchant_users.update({
    where: { id: user.id },
    data: { password_hash: passwordHash },
  });

  return { email: normalizedEmail, reset: true };
}

function generateOrderNumber(userId) {
  const rand = crypto.randomBytes(4).toString('hex');
  return `DM-${userId}-${Date.now()}-${rand}`;
}

module.exports = {
  signToken,
  verifyToken,
  formatUser,
  register,
  login,
  getSession,
  resetPassword,
  generateOrderNumber,
};
