// backend/verify_billing_engine.js

const { query } = require('./db/connection.js');
const http = require('http');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'paychek_super_secret_jwt_key_987654321';

function postJson(path, payload, token = null) {
  return new Promise((resolve) => {
    const data = JSON.stringify(payload);
    const headers = {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(data)
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const options = {
      hostname: 'localhost',
      port: 3000,
      path,
      method: 'POST',
      headers
    };
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(body) });
        } catch (_) {
          resolve({ status: res.statusCode, body });
        }
      });
    });
    req.on('error', e => resolve({ status: 500, body: { error: e.message } }));
    req.write(data);
    req.end();
  });
}

function getJson(path, token = null) {
  return new Promise((resolve) => {
    const headers = {};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const options = {
      hostname: 'localhost',
      port: 3000,
      path,
      method: 'GET',
      headers
    };
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(body) });
        } catch (_) {
          resolve({ status: res.statusCode, body });
        }
      });
    });
    req.on('error', e => resolve({ status: 500, body: { error: e.message } }));
    req.end();
  });
}

async function runTests() {
  console.log('--- Cleaning Up Billing Test Data ---');
  const testPhone = '01711223344';
  const testEmail = 'billing_test@example.com';
  
  // Find test user to cleanup
  const existingUsers = await query('SELECT id FROM users WHERE phone = ? OR email = ?', [testPhone, testEmail]);
  for (const user of existingUsers) {
    await query('DELETE FROM gateway_layouts WHERE user_id = ?', [user.id]);
    await query('DELETE FROM registered_devices WHERE user_id = ?', [user.id]);
    await query('DELETE FROM user_credentials WHERE user_id = ?', [user.id]);
    await query('DELETE FROM users WHERE id = ?', [user.id]);
  }
  
  console.log('✅ Cleanup finished.');

  // Generate an admin token for admin API validation
  const adminToken = jwt.sign(
    { userId: 0, role: 'admin', deviceId: 'admin-console' },
    JWT_SECRET,
    { expiresIn: '1h' }
  );

  // 1. Verify Global Billing Settings Seeds
  console.log('\n--- 1. Verifying Global Billing Settings Seeds ---');
  const settings = await query('SELECT * FROM global_billing_settings');
  console.log(`Found ${settings.length} global billing settings seeds.`);
  const keys = settings.map(s => s.setting_key);
  const requiredKeys = ['default_signup_bonus', 'daily_maintenance_rate', 'one_time_site_fee', 'one_time_device_fee'];
  for (const rk of requiredKeys) {
    if (!keys.includes(rk)) {
      console.error(`❌ FAILED: Missing seed key ${rk}`);
      process.exit(1);
    }
  }
  console.log('✅ SUCCESS: All required global billing settings are seeded.');

  // 2. Signup Flow & Bonus Award Verification
  console.log('\n--- 2. Registering New User & Checking Signup Bonus ---');
  // Simulate OTP registration flow
  const sendOtpRes = await postJson('/api/auth/register-send-otp', {
    contact: testPhone,
    deviceId: 'device_test_9991',
    androidId: 'android_test_9991',
    hardwareFingerprint: 'fingerprint_test_9991',
    simSlotIds: 'sim_test_9991'
  });
  
  const otpRows = await query('SELECT code FROM otps WHERE contact = ? ORDER BY id DESC LIMIT 1', [testPhone]);
  if (otpRows.length === 0) {
    console.error('❌ FAILED: Could not retrieve OTP code from DB.');
    process.exit(1);
  }
  const code = otpRows[0].code;

  const verifyOtpRes = await postJson('/api/verify-otp', {
    contact: testPhone,
    code,
    deviceId: 'device_test_9991',
    androidId: 'android_test_9991',
    hardwareFingerprint: 'fingerprint_test_9991',
    simSlotIds: 'sim_test_9991',
    deviceModel: 'Test Device 1',
    androidVersion: '13'
  });

  const testUserId = verifyOtpRes.body.user.id;
  const userToken = verifyOtpRes.body.token;

  // Retrieve user balance from db
  const [userRow] = await query('SELECT wallet_credits FROM users WHERE id = ?', [testUserId]);
  console.log(`Initial wallet_credits: ৳${userRow.wallet_credits}`);
  if (parseFloat(userRow.wallet_credits) !== 30.00) {
    console.error(`❌ FAILED: Expected signup bonus of 30.00, got ${userRow.wallet_credits}`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: Default signup bonus of ৳30.00 credited.');

  // 3. Child Device Fee Deduction
  console.log('\n--- 3. Registering a Child Device & Checking Fee Deduction ---');
  const childOtpCode = '999999';
  const expiresAt = new Date(Date.now() + 5 * 60 * 1000);
  await query(
    'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
    [testPhone, childOtpCode, expiresAt]
  );

  const childVerifyOtpRes = await postJson('/api/verify-otp', {
    contact: testPhone,
    code: childOtpCode,
    deviceId: 'device_test_9992', // Second device
    androidId: 'android_test_9992',
    hardwareFingerprint: 'fingerprint_test_9992',
    simSlotIds: 'sim_test_9992',
    deviceModel: 'Test Device 2',
    androidVersion: '13'
  });

  const [userRowAfterChild] = await query('SELECT wallet_credits FROM users WHERE id = ?', [testUserId]);
  console.log(`Wallet credits after child device addition: ৳${userRowAfterChild.wallet_credits}`);
  if (parseFloat(userRowAfterChild.wallet_credits) !== 25.00) {
    console.error(`❌ FAILED: Expected balance 25.00 after 5.00 child fee, got ${userRowAfterChild.wallet_credits}`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: Child device fee of ৳5.00 deducted.');

  // 4. Recharge Endpoints (Enforces Minimum ৳50 limit validation)
  console.log('\n--- 4. Testing Recharge Endpoint Validation ---');
  const rechargeFail = await postJson('/api/v1/subscription/recharge', { amount: 49.99 }, userToken);
  console.log(`Recharging ৳49.99 Status (Expected 400): ${rechargeFail.status}`);
  if (rechargeFail.status !== 400) {
    console.error('❌ FAILED: Recharge under ৳50 was not blocked.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: Under ৳50 recharge blocked.');

  const rechargePass = await postJson('/api/v1/subscription/recharge', { amount: 50.00 }, userToken);
  console.log(`Recharging ৳50.00 Status (Expected 200): ${rechargePass.status}`);
  console.log(`New balance returned: ৳${rechargePass.body.wallet_credits}`);
  if (rechargePass.status !== 200 || parseFloat(rechargePass.body.wallet_credits) !== 75.00) {
    console.error(`❌ FAILED: Recharge ৳50.00 failed or incorrect balance returned.`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: ৳50.00 recharge successful.');

  // 5. FCM Token update verification
  console.log('\n--- 5. Testing FCM Token Registration ---');
  const fcmRes = await postJson('/api/v1/subscription/fcm-token', { token: 'mock_firebase_push_token_xyz_123' }, userToken);
  console.log(`FCM Update Status (Expected 200): ${fcmRes.status}`);
  const [fcmUserRow] = await query('SELECT fcm_token FROM users WHERE id = ?', [testUserId]);
  console.log(`Saved token in DB: ${fcmUserRow.fcm_token}`);
  if (fcmUserRow.fcm_token !== 'mock_firebase_push_token_xyz_123') {
    console.error('❌ FAILED: FCM token was not saved correctly.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: FCM token registered successfully.');

  // 6. One-time website creation fee deduction
  console.log('\n--- 6. Registering Site Layout & Checking Fee Deduction ---');
  const siteRes = await postJson('/api/admin/sites/add', {
    site_name: 'Test Store',
    site_url: 'https://teststore.com'
  }, userToken);

  console.log(`Site creation status (Expected 200): ${siteRes.status}`);
  console.log(`Deducted site fee. Remaining credits returned: ৳${siteRes.body.wallet_credits}`);
  if (siteRes.status !== 200 || parseFloat(siteRes.body.wallet_credits) !== 65.00) {
    console.error(`❌ FAILED: Website creation failed or incorrect fee deducted.`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: Website created, ৳10.00 fee deducted.');

  // 7. Admin Endpoint - Custom Rate & Settings
  console.log('\n--- 7. Testing Admin Billing Config Endpoints ---');
  const getBillingRes = await getJson('/api/admin/billing-settings', adminToken);
  console.log(`Admin GET billing settings status (Expected 200): ${getBillingRes.status}`);

  const updateCustomRateRes = await postJson(`/api/admin/users/${testUserId}/custom-rate`, {
    custom_daily_rate: 1.50
  }, adminToken);
  console.log(`Admin POST user custom rate status (Expected 200): ${updateCustomRateRes.status}`);
  const [customRateRow] = await query('SELECT custom_daily_rate FROM users WHERE id = ?', [testUserId]);
  console.log(`User custom rate in DB: ${customRateRow.custom_daily_rate}`);
  if (parseFloat(customRateRow.custom_daily_rate) !== 1.50) {
    console.error('❌ FAILED: Admin custom daily rate was not set in DB.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: Custom user rate set by admin.');

  // 8. Billing Lock Middleware (HTTP 402)
  console.log('\n--- 8. Testing Billing Lock Guard (HTTP 402) ---');
  // First, complete the user profile so they are a standard active user
  await query('UPDATE users SET profile_complete = 1 WHERE id = ?', [testUserId]);

  // Set user credits to 0.00 and set creation date to 31 days ago (forces lock)
  const thirtyOneDaysAgo = new Date();
  thirtyOneDaysAgo.setDate(thirtyOneDaysAgo.getDate() - 31);
  await query(
    'UPDATE users SET created_at = ?, wallet_credits = 0.00 WHERE id = ?',
    [thirtyOneDaysAgo, testUserId]
  );

  // Call stats route (guarded by billing status check)
  const lockedRes = await getJson('/api/dashboard/stats', userToken);
  console.log(`Guarded Endpoint response status for locked user (Expected 402): ${lockedRes.status}`);
  console.log(`Response body error (Expected ACCOUNT_SUSPENDED):`, lockedRes.body.error);
  if (lockedRes.status !== 402 || lockedRes.body.error !== 'ACCOUNT_SUSPENDED') {
    console.error('❌ FAILED: Billing lock guard failed to lock suspended user with HTTP 402.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: User locked with HTTP 402 ACCOUNT_SUSPENDED.');

  // Restore credits, call again, should be unlocked
  await query('UPDATE users SET wallet_credits = 5.00 WHERE id = ?', [testUserId]);
  const unlockedRes = await getJson('/api/dashboard/stats', userToken);
  console.log(`Guarded Endpoint response status after credit restoration (Expected 200): ${unlockedRes.status}`);
  if (unlockedRes.status !== 200) {
    console.error('❌ FAILED: User remains locked after credits restored.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: User unlocked after recharging.');

  console.log('\n🎉 ALL BILLING ENGINE VERIFICATION TESTS PASSED SUCCESSFULLY! 🎉');
  process.exit(0);
}

runTests().catch(e => {
  console.error('Test execution failed:', e);
  process.exit(1);
});
