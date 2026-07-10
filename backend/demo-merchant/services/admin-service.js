const prisma = require('../../db/prisma');
const walletService = require('./wallet-service');
const roleStore = require('./user-role-store');
const { ROLES } = require('./roles');

function formatUser(row) {
  return {
    id: row.id,
    email: row.email,
    fullName: row.full_name,
    role: row.role || ROLES.USER,
    createdAt: row.created_at,
  };
}

async function listUsers() {
  const rows = await prisma.demo_merchant_users.findMany({
    orderBy: { created_at: 'asc' },
    select: {
      id: true,
      email: true,
      full_name: true,
      created_at: true,
    },
  });

  const withRoles = await Promise.all(
    rows.map(async (row) => {
      row.role = await roleStore.getRoleByUserId(row.id);
      return formatUser(row);
    }),
  );

  return withRoles;
}

async function setUserRole(actorId, targetUserId, role) {
  if (![ROLES.USER, ROLES.ADMIN].includes(role)) {
    const err = new Error('Invalid role');
    err.code = 'VALIDATION_ERROR';
    throw err;
  }

  if (actorId === targetUserId && role !== ROLES.ADMIN) {
    const err = new Error('You cannot remove your own admin access');
    err.code = 'FORBIDDEN';
    throw err;
  }

  const target = await prisma.demo_merchant_users.findUnique({
    where: { id: targetUserId },
    select: { id: true, email: true, full_name: true, created_at: true },
  });

  if (!target) {
    const err = new Error('User not found');
    err.code = 'NOT_FOUND';
    throw err;
  }

  const currentRole = await roleStore.getRoleByUserId(targetUserId);

  if (currentRole === ROLES.ADMIN && role === ROLES.USER) {
    const adminCount = await roleStore.countAdmins();
    if (adminCount <= 1) {
      const err = new Error('At least one admin must remain');
      err.code = 'LAST_ADMIN';
      throw err;
    }
  }

  await roleStore.setRole(targetUserId, role);
  target.role = role;

  return formatUser(target);
}

async function getAdminStats() {
  const [userCount, productCount, orderCount, paidOrders] = await Promise.all([
    prisma.demo_merchant_users.count(),
    prisma.demo_merchant_products.count({ where: { is_active: 1 } }),
    prisma.demo_merchant_orders.count(),
    prisma.demo_merchant_orders.count({ where: { status: 'paid' } }),
  ]);

  return { userCount, productCount, orderCount, paidOrders };
}

async function createProduct({ sku, name, description, price, imageUrl }) {
  const parsedPrice = walletService.toNumber(price);
  if (!sku || !name || !(parsedPrice > 0)) {
    const err = new Error('Invalid product data');
    err.code = 'VALIDATION_ERROR';
    throw err;
  }

  const product = await prisma.demo_merchant_products.create({
    data: {
      sku: String(sku).trim().slice(0, 64),
      name: String(name).trim().slice(0, 120),
      description: description ? String(description).slice(0, 2000) : null,
      price: parsedPrice,
      image_url: imageUrl || null,
      is_active: 1,
    },
  });

  return productServiceFormat(product);
}

async function updateProduct(productId, fields) {
  const existing = await prisma.demo_merchant_products.findUnique({ where: { id: productId } });
  if (!existing) {
    const err = new Error('Product not found');
    err.code = 'NOT_FOUND';
    throw err;
  }

  const data = {};
  if (fields.sku != null) data.sku = String(fields.sku).trim().slice(0, 64);
  if (fields.name != null) data.name = String(fields.name).trim().slice(0, 120);
  if (fields.description != null) data.description = String(fields.description).slice(0, 2000);
  if (fields.price != null) {
    const p = walletService.toNumber(fields.price);
    if (!(p > 0)) {
      const err = new Error('Invalid price');
      err.code = 'VALIDATION_ERROR';
      throw err;
    }
    data.price = p;
  }
  if (fields.imageUrl !== undefined) data.image_url = fields.imageUrl;
  if (fields.isActive != null) data.is_active = fields.isActive ? 1 : 0;

  const updated = await prisma.demo_merchant_products.update({
    where: { id: productId },
    data,
  });

  return productServiceFormat(updated);
}

async function deleteProduct(productId) {
  const existing = await prisma.demo_merchant_products.findUnique({ where: { id: productId } });
  if (!existing) {
    const err = new Error('Product not found');
    err.code = 'NOT_FOUND';
    throw err;
  }

  await prisma.demo_merchant_products.update({
    where: { id: productId },
    data: { is_active: 0 },
  });

  return { id: productId, deleted: true };
}

async function listAllProducts() {
  const rows = await prisma.demo_merchant_products.findMany({
    orderBy: { id: 'desc' },
  });
  return rows.map(productServiceFormat);
}

function productServiceFormat(p) {
  return {
    id: p.id,
    sku: p.sku,
    name: p.name,
    description: p.description,
    price: walletService.toNumber(p.price),
    imageUrl: p.image_url,
    isActive: p.is_active === 1,
    createdAt: p.created_at,
  };
}

module.exports = {
  ROLES,
  listUsers,
  setUserRole,
  getAdminStats,
  createProduct,
  updateProduct,
  deleteProduct,
  listAllProducts,
};
