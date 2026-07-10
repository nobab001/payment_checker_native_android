(function () {
  const { DemoApi } = window;
  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));

  let currentView = 'dashboard';
  let selectedAmount = null;
  let currentUser = null;
  let adminMode = DemoApi.isAdminMode();

  const customerViews = {
    dashboard: $('#panel-dashboard'),
    wallet: $('#panel-wallet'),
    addBalance: $('#panel-add-balance'),
    products: $('#panel-products'),
    orders: $('#panel-orders'),
    transactions: $('#panel-transactions'),
  };

  const adminViews = {
    adminDashboard: $('#panel-admin-dashboard'),
    adminProducts: $('#panel-admin-products'),
    adminUsers: $('#panel-admin-users'),
  };

  function showAlert(el, message, type = 'info') {
    if (!el) return;
    el.className = `alert ${type}`;
    el.textContent = message;
    el.classList.remove('hidden');
  }

  function hideAlert(el) {
    if (el) el.classList.add('hidden');
  }

  function formatMoney(n) {
    return `৳${Number(n || 0).toLocaleString('en-BD', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  function formatDate(d) {
    if (!d) return '—';
    return new Date(d).toLocaleString('en-BD', { timeZone: 'Asia/Dhaka' });
  }

  function statusBadge(status) {
    const s = String(status || '').toLowerCase();
    return `<span class="badge ${s}">${s}</span>`;
  }

  function productImage(url) {
    if (!url) return '<div style="height:140px;background:var(--bg);border-radius:8px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:0.85rem">ছবি নেই</div>';
    return `<img src="${url}" alt="product" loading="lazy" />`;
  }

  function updateAdminToggle() {
    const btn = $('#btn-admin-mode');
    if (!currentUser?.isAdmin) {
      btn.classList.add('hidden');
      return;
    }
    btn.classList.remove('hidden');
    btn.textContent = adminMode ? 'কাস্টমার মোড' : 'এডমিন মোড';
    btn.classList.toggle('btn-danger', adminMode);
    btn.classList.toggle('btn-outline', !adminMode);

    $('#nav-customer').classList.toggle('hidden', adminMode);
    $('#nav-admin').classList.toggle('hidden', !adminMode);
  }

  function setView(name) {
    currentView = name;
    const nav = adminMode ? '#nav-admin' : '#nav-customer';
    $$(`${nav} .nav-btn`).forEach((btn) => btn.classList.toggle('active', btn.dataset.view === name));

    Object.values(customerViews).forEach((el) => el?.classList.add('hidden'));
    Object.values(adminViews).forEach((el) => el?.classList.add('hidden'));

    const views = adminMode ? adminViews : customerViews;
    if (views[name]) views[name].classList.remove('hidden');

    if (name === 'dashboard') loadDashboard();
    if (name === 'wallet') loadWallet();
    if (name === 'addBalance') loadAddBalance();
    if (name === 'products') loadProducts();
    if (name === 'orders') loadOrders($('#order-filter')?.value || '');
    if (name === 'transactions') loadTransactions($('#txn-filter')?.value || '');
    if (name === 'adminDashboard') loadAdminDashboard();
    if (name === 'adminProducts') loadAdminProducts();
    if (name === 'adminUsers') loadAdminUsers();
  }

  function toggleAdminMode() {
    if (!currentUser?.isAdmin) return;
    adminMode = !adminMode;
    DemoApi.setAdminMode(adminMode);
    updateAdminToggle();
    setView(adminMode ? 'adminDashboard' : 'dashboard');
  }

  function showAuth() {
    $('#view-auth').classList.remove('hidden');
    $('#view-app').classList.add('hidden');
    $('#header-actions').classList.add('hidden');
    resetAuthForms();
  }

  function resetAuthForms() {
    hideAlert($('#auth-alert'));
    $('#auth-tab-login').classList.add('active');
    $('#auth-tab-register').classList.remove('active');
    $('#form-login').classList.remove('hidden');
    $('#form-register').classList.add('hidden');
    $('#form-forgot').classList.add('hidden');
    $('#login-password').value = '';
    $('#forgot-password').value = '';
    $('#forgot-password-confirm').value = '';
  }

  function showApp(user) {
    currentUser = user;
    $('#view-auth').classList.add('hidden');
    $('#view-app').classList.remove('hidden');
    $('#header-actions').classList.remove('hidden');
    $('#header-actions').style.display = 'flex';

    const adminLabel = user.isAdmin ? ' <span class="admin-badge">Admin</span>' : '';
    $('#user-email').innerHTML = `${user.fullName || user.email}${adminLabel}`;
    $('#sidebar-balance').textContent = formatMoney(user.walletBalance);

    if (!user.isAdmin) {
      adminMode = false;
      DemoApi.setAdminMode(false);
    }

    updateAdminToggle();
    setView(adminMode ? 'adminDashboard' : 'dashboard');
  }

  async function bootstrap() {
    const token = DemoApi.getToken();
    if (!token) {
      showAuth();
      return;
    }
    try {
      const { user } = await DemoApi.session();
      showApp(user);
    } catch {
      DemoApi.setToken(null);
      showAuth();
    }
  }

  async function loadDashboard() {
    try {
      const { dashboard } = await DemoApi.dashboard();
      $('#dash-balance').textContent = formatMoney(dashboard.walletBalance);
      $('#sidebar-balance').textContent = formatMoney(dashboard.walletBalance);

      const stats = dashboard.paymentStatistics.ordersByStatus;
      $('#stat-pending').textContent = stats.pending || 0;
      $('#stat-paid').textContent = stats.paid || 0;
      $('#stat-failed').textContent = stats.failed || 0;
      $('#stat-cancelled').textContent = stats.cancelled || 0;
      $('#stat-paid-amount').textContent = formatMoney(stats.totalPaidAmount);

      renderOrdersTable('#dash-orders', dashboard.recentOrders);
      renderTxnTable('#dash-txns', dashboard.recentTransactions);
    } catch (err) {
      showAlert($('#dash-alert'), err.message, 'error');
    }
  }

  async function loadAdminDashboard() {
    try {
      const { stats } = await DemoApi.adminStats();
      $('#admin-stats-grid').innerHTML = `
        <div class="stat"><div class="label">মোট ইউজার</div><div class="value">${stats.userCount}</div></div>
        <div class="stat"><div class="label">সক্রিয় প্রোডাক্ট</div><div class="value">${stats.productCount}</div></div>
        <div class="stat"><div class="label">মোট অর্ডার</div><div class="value">${stats.orderCount}</div></div>
        <div class="stat"><div class="label">পেইড অর্ডার</div><div class="value">${stats.paidOrders}</div></div>
      `;
    } catch (err) {
      $('#admin-stats-grid').innerHTML = `<p class="empty">${err.message}</p>`;
    }
  }

  async function loadWallet() {
    try {
      const [{ balance }, { history }] = await Promise.all([
        DemoApi.walletBalance(),
        DemoApi.walletHistory(),
      ]);
      $('#wallet-balance').textContent = formatMoney(balance);
      $('#sidebar-balance').textContent = formatMoney(balance);

      const tbody = $('#wallet-history-body');
      if (!history.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty">No ledger entries yet.</td></tr>';
        return;
      }
      tbody.innerHTML = history.map((row) => `
        <tr>
          <td>${formatDate(row.createdAt)}</td>
          <td>${row.entryType}</td>
          <td>${formatMoney(row.amount)}</td>
          <td>${formatMoney(row.balanceAfter)}</td>
          <td>${row.description || '—'}</td>
        </tr>
      `).join('');
    } catch (err) {
      showAlert($('#wallet-alert'), err.message, 'error');
    }
  }

  async function loadAddBalance() {
    hideAlert($('#recharge-alert'));
    try {
      const { amounts } = await DemoApi.paymentPresets();
      const container = $('#amount-presets');
      container.innerHTML = amounts.map((a) =>
        `<button type="button" data-amount="${a}" class="${selectedAmount === a ? 'selected' : ''}">${formatMoney(a)}</button>`
      ).join('');
      container.querySelectorAll('button').forEach((btn) => {
        btn.addEventListener('click', () => {
          selectedAmount = Number(btn.dataset.amount);
          $('#custom-amount').value = '';
          loadAddBalance();
        });
      });
    } catch (err) {
      showAlert($('#recharge-alert'), err.message, 'error');
    }
  }

  async function startRecharge() {
    hideAlert($('#recharge-alert'));
    const custom = parseFloat($('#custom-amount').value);
    const amount = custom > 0 ? custom : selectedAmount;
    if (!(amount > 0)) {
      showAlert($('#recharge-alert'), 'একটা পরিমাণ বেছে নিন বা লিখুন।', 'warn');
      return;
    }

    const btn = $('#btn-recharge');
    btn.disabled = true;
    try {
      const result = await DemoApi.walletRecharge(amount);
      if (result.checkoutUrl) window.location.href = result.checkoutUrl;
    } catch (err) {
      showAlert($('#recharge-alert'), err.message || 'Checkout failed', 'error');
    } finally {
      btn.disabled = false;
    }
  }

  async function loadProducts() {
    try {
      const { products } = await DemoApi.products();
      const grid = $('#product-grid');
      if (!products.length) {
        grid.innerHTML = '<p class="empty">কোনো প্রোডাক্ট নেই।</p>';
        return;
      }
      grid.innerHTML = products.map((p) => `
        <article class="product-card">
          ${productImage(p.imageUrl)}
          <h3>${p.name}</h3>
          <p>${p.description || ''}</p>
          <div class="price">${formatMoney(p.price)}</div>
          <button class="btn btn-sm" data-product-id="${p.id}">Buy Now</button>
        </article>
      `).join('');

      grid.querySelectorAll('button[data-product-id]').forEach((btn) => {
        btn.addEventListener('click', async () => {
          btn.disabled = true;
          try {
            const result = await DemoApi.productCheckout(Number(btn.dataset.productId));
            if (result.checkoutUrl) window.location.href = result.checkoutUrl;
          } catch (err) {
            alert(err.message || 'Checkout failed');
          } finally {
            btn.disabled = false;
          }
        });
      });
    } catch (err) {
      $('#product-grid').innerHTML = `<p class="empty">${err.message}</p>`;
    }
  }

  function resetAdminProductForm() {
    $('#admin-product-id').value = '';
    $('#admin-product-sku').value = '';
    $('#admin-product-name').value = '';
    $('#admin-product-desc').value = '';
    $('#admin-product-price').value = '';
    $('#admin-product-image').value = '';
    $('#admin-product-form-title').textContent = 'নতুন প্রোডাক্ট যোগ করুন';
    $('#btn-admin-product-cancel').classList.add('hidden');
  }

  async function loadAdminProducts() {
    hideAlert($('#admin-products-alert'));
    try {
      const { products } = await DemoApi.adminProducts();
      const list = $('#admin-product-list');
      if (!products.length) {
        list.innerHTML = '<p class="empty">কোনো প্রোডাক্ট নেই। উপরে ফর্ম দিয়ে যোগ করুন।</p>';
        return;
      }
      list.innerHTML = products.map((p) => `
        <article class="product-card">
          ${productImage(p.imageUrl)}
          <h3>${p.name} ${p.isActive ? '' : '<span class="badge cancelled">inactive</span>'}</h3>
          <p>SKU: ${p.sku}</p>
          <p>${p.description || ''}</p>
          <div class="price">${formatMoney(p.price)}</div>
          <div class="admin-actions">
            <button class="btn btn-sm btn-outline" data-edit-id="${p.id}">এডিট</button>
            <button class="btn btn-sm btn-danger" data-delete-id="${p.id}">ডিলিট</button>
          </div>
        </article>
      `).join('');

      list.querySelectorAll('[data-edit-id]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const p = products.find((x) => x.id === Number(btn.dataset.editId));
          if (!p) return;
          $('#admin-product-id').value = p.id;
          $('#admin-product-sku').value = p.sku;
          $('#admin-product-name').value = p.name;
          $('#admin-product-desc').value = p.description || '';
          $('#admin-product-price').value = p.price;
          $('#admin-product-form-title').textContent = 'প্রোডাক্ট এডিট করুন';
          $('#btn-admin-product-cancel').classList.remove('hidden');
          window.scrollTo({ top: 0, behavior: 'smooth' });
        });
      });

      list.querySelectorAll('[data-delete-id]').forEach((btn) => {
        btn.addEventListener('click', async () => {
          if (!confirm('এই প্রোডাক্টটি ডিলিট করবেন?')) return;
          try {
            await DemoApi.adminDeleteProduct(Number(btn.dataset.deleteId));
            loadAdminProducts();
          } catch (err) {
            showAlert($('#admin-products-alert'), err.message, 'error');
          }
        });
      });
    } catch (err) {
      showAlert($('#admin-products-alert'), err.message, 'error');
    }
  }

  async function loadAdminUsers() {
    hideAlert($('#admin-users-alert'));
    try {
      const { users } = await DemoApi.adminUsers();
      $('#admin-users-table').innerHTML = `
        <table>
          <thead><tr><th>নাম</th><th>ইমেইল</th><th>রোল</th><th>তারিখ</th><th>অ্যাকশন</th></tr></thead>
          <tbody>
            ${users.map((u) => `
              <tr>
                <td>${u.fullName}</td>
                <td>${u.email}</td>
                <td>${u.role === 'admin' ? '<span class="badge paid">admin</span>' : '<span class="badge pending">user</span>'}</td>
                <td>${formatDate(u.createdAt)}</td>
                <td>
                  ${u.role === 'admin'
                    ? `<button class="btn btn-sm btn-outline" data-role-user="${u.id}" data-role="user">Admin সরান</button>`
                    : `<button class="btn btn-sm" data-role-user="${u.id}" data-role="admin">Admin দিন</button>`}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>`;

      $('#admin-users-table').querySelectorAll('[data-role-user]').forEach((btn) => {
        btn.addEventListener('click', async () => {
          const userId = Number(btn.dataset.roleUser);
          const role = btn.dataset.role;
          try {
            await DemoApi.adminSetRole(userId, role);
            if (userId === currentUser.id && role !== 'admin') {
              alert('আপনার admin অ্যাক্সেস সরানো হয়েছে। আবার লগইন করুন।');
              DemoApi.setToken(null);
              showAuth();
              return;
            }
            loadAdminUsers();
            const session = await DemoApi.session();
            showApp(session.user);
          } catch (err) {
            showAlert($('#admin-users-alert'), err.message, 'error');
          }
        });
      });
    } catch (err) {
      showAlert($('#admin-users-alert'), err.message, 'error');
    }
  }

  function renderOrdersTable(selector, orders) {
    const el = $(selector);
    if (!orders?.length) {
      el.innerHTML = '<p class="empty">No orders yet.</p>';
      return;
    }
    el.innerHTML = `
      <table>
        <thead><tr><th>Order</th><th>Type</th><th>Amount</th><th>Status</th><th>Date</th></tr></thead>
        <tbody>
          ${orders.map((o) => `
            <tr>
              <td>${o.orderNumber}</td>
              <td>${o.orderType.replace('_', ' ')}</td>
              <td>${formatMoney(o.amount)}</td>
              <td>${statusBadge(o.status)}</td>
              <td>${formatDate(o.createdAt)}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;
  }

  function renderTxnTable(selector, txns) {
    const el = $(selector);
    if (!txns?.length) {
      el.innerHTML = '<p class="empty">No transactions yet.</p>';
      return;
    }
    el.innerHTML = `
      <table>
        <thead><tr><th>Type</th><th>Amount</th><th>Status</th><th>Description</th><th>Date</th></tr></thead>
        <tbody>
          ${txns.map((t) => `
            <tr>
              <td>${t.txnType.replace('_', ' ')}</td>
              <td>${formatMoney(t.amount)}</td>
              <td>${statusBadge(t.status)}</td>
              <td>${t.description || '—'}</td>
              <td>${formatDate(t.createdAt)}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>`;
  }

  async function loadOrders(status) {
    try {
      const { orders } = await DemoApi.orders(status || undefined);
      renderOrdersTable('#orders-table', orders);
    } catch (err) {
      $('#orders-table').innerHTML = `<p class="empty">${err.message}</p>`;
    }
  }

  async function loadTransactions(type) {
    try {
      const { transactions } = await DemoApi.transactions(type || undefined);
      renderTxnTable('#transactions-table', transactions);
    } catch (err) {
      $('#transactions-table').innerHTML = `<p class="empty">${err.message}</p>`;
    }
  }

  $('#form-login').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert($('#auth-alert'));
    try {
      const { token, user } = await DemoApi.login({
        email: $('#login-email').value.trim(),
        password: $('#login-password').value,
      });
      DemoApi.setToken(token);
      const session = await DemoApi.session();
      showApp(session.user);
    } catch (err) {
      showAlert($('#auth-alert'), err.message, 'error');
    }
  });

  $('#form-register').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert($('#auth-alert'));
    try {
      const { token } = await DemoApi.register({
        email: $('#reg-email').value,
        password: $('#reg-password').value,
        fullName: $('#reg-name').value,
      });
      DemoApi.setToken(token);
      const session = await DemoApi.session();
      showApp(session.user);
    } catch (err) {
      showAlert($('#auth-alert'), err.message, 'error');
    }
  });

  $('#btn-logout').addEventListener('click', async () => {
    try { await DemoApi.logout(); } catch { /* ignore */ }
    DemoApi.setToken(null);
    adminMode = false;
    DemoApi.setAdminMode(false);
    showAuth();
  });

  $('#btn-admin-mode').addEventListener('click', toggleAdminMode);

  $$('#nav-customer .nav-btn, #nav-admin .nav-btn').forEach((btn) => {
    btn.addEventListener('click', () => setView(btn.dataset.view));
  });

  $('#btn-recharge').addEventListener('click', startRecharge);
  $('#order-filter').addEventListener('change', (e) => loadOrders(e.target.value));
  $('#txn-filter').addEventListener('change', (e) => loadTransactions(e.target.value));

  $('#auth-tab-login').addEventListener('click', () => {
    $('#auth-tab-login').classList.add('active');
    $('#auth-tab-register').classList.remove('active');
    $('#form-login').classList.remove('hidden');
    $('#form-register').classList.add('hidden');
    $('#form-forgot').classList.add('hidden');
  });

  $('#auth-tab-register').addEventListener('click', () => {
    $('#auth-tab-register').classList.add('active');
    $('#auth-tab-login').classList.remove('active');
    $('#form-register').classList.remove('hidden');
    $('#form-login').classList.add('hidden');
    $('#form-forgot').classList.add('hidden');
  });

  $('#btn-forgot-password').addEventListener('click', () => {
    hideAlert($('#auth-alert'));
    $('#forgot-email').value = $('#login-email').value.trim();
    $('#form-login').classList.add('hidden');
    $('#form-forgot').classList.remove('hidden');
  });

  $('#btn-back-to-login').addEventListener('click', () => {
    hideAlert($('#auth-alert'));
    $('#form-forgot').classList.add('hidden');
    $('#form-login').classList.remove('hidden');
  });

  $('#form-forgot').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert($('#auth-alert'));
    const email = $('#forgot-email').value.trim();
    const newPassword = $('#forgot-password').value;
    const confirm = $('#forgot-password-confirm').value;
    if (newPassword !== confirm) {
      showAlert($('#auth-alert'), 'Passwords do not match.', 'error');
      return;
    }
    try {
      await DemoApi.forgotPassword({ email, newPassword });
      showAlert($('#auth-alert'), 'Password reset done. Log in with your new password.', 'info');
      $('#login-email').value = email;
      $('#form-forgot').classList.add('hidden');
      $('#form-login').classList.remove('hidden');
      $('#login-password').value = '';
      $('#login-password').focus();
    } catch (err) {
      showAlert($('#auth-alert'), err.message, 'error');
    }
  });

  $('#form-admin-product').addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlert($('#admin-products-alert'));
    const formData = new FormData();
    formData.append('sku', $('#admin-product-sku').value);
    formData.append('name', $('#admin-product-name').value);
    formData.append('description', $('#admin-product-desc').value);
    formData.append('price', $('#admin-product-price').value);
    const file = $('#admin-product-image').files[0];
    if (file) formData.append('image', file);

    const productId = $('#admin-product-id').value;
    try {
      if (productId) {
        await DemoApi.adminUpdateProduct(productId, formData);
      } else {
        await DemoApi.adminCreateProduct(formData);
      }
      resetAdminProductForm();
      loadAdminProducts();
      showAlert($('#admin-products-alert'), 'প্রোডাক্ট সেভ হয়েছে!', 'info');
    } catch (err) {
      showAlert($('#admin-products-alert'), err.message, 'error');
    }
  });

  $('#btn-admin-product-cancel').addEventListener('click', resetAdminProductForm);

  bootstrap();
})();
