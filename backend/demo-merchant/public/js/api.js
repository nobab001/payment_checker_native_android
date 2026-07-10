const API_BASE = '/demo-merchant/api';
const TOKEN_KEY = 'demo_merchant_token';
const ADMIN_MODE_KEY = 'demo_merchant_admin_mode';

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

function isAdminMode() {
  return localStorage.getItem(ADMIN_MODE_KEY) === '1';
}

function setAdminMode(on) {
  localStorage.setItem(ADMIN_MODE_KEY, on ? '1' : '0');
}

async function api(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.message || data.error || `HTTP ${res.status}`);
    err.code = data.error;
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

async function apiForm(path, formData, { method = 'POST', auth = true } = {}) {
  const headers = {};
  if (auth) {
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { method, headers, body: formData });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.message || data.error || `HTTP ${res.status}`);
    err.code = data.error;
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

window.DemoApi = {
  getToken,
  setToken,
  isAdminMode,
  setAdminMode,
  register: (payload) => api('/auth/register', { method: 'POST', body: payload, auth: false }),
  login: (payload) => api('/auth/login', { method: 'POST', body: payload, auth: false }),
  forgotPassword: (payload) => api('/auth/forgot-password', { method: 'POST', body: payload, auth: false }),
  logout: () => api('/auth/logout', { method: 'POST' }),
  session: () => api('/auth/session'),
  dashboard: () => api('/dashboard'),
  walletBalance: () => api('/wallet/balance'),
  walletHistory: () => api('/wallet/history'),
  paymentPresets: () => api('/payments/presets'),
  walletRecharge: (amount) => api('/payments/wallet-recharge', { method: 'POST', body: { amount } }),
  productCheckout: (productId) => api('/payments/product-checkout', { method: 'POST', body: { productId } }),
  confirmPayment: (orderNumber) => api('/payments/confirm', { method: 'POST', body: { orderNumber } }),
  returnConfirmPayment: (orderNumber, sessionToken) => api('/payments/return-confirm', {
    method: 'POST',
    body: { orderNumber, sessionToken },
    auth: false,
  }),
  products: () => api('/products', { auth: false }),
  orders: (status) => api(`/orders${status ? `?status=${status}` : ''}`),
  transactions: (type) => api(`/transactions${type ? `?type=${type}` : ''}`),
  adminStats: () => api('/admin/stats'),
  adminUsers: () => api('/admin/users'),
  adminSetRole: (userId, role) => api(`/admin/users/${userId}/role`, { method: 'PATCH', body: { role } }),
  adminProducts: () => api('/admin/products'),
  adminCreateProduct: (formData) => apiForm('/admin/products', formData, { method: 'POST' }),
  adminUpdateProduct: (id, formData) => apiForm(`/admin/products/${id}`, formData, { method: 'PUT' }),
  adminDeleteProduct: (id) => api(`/admin/products/${id}`, { method: 'DELETE' }),
};
