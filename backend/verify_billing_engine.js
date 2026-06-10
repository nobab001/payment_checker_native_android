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

  // 1. Verify Global Billing Settings (FCM reminder/settings check)
  console.log('\n--- 1. Verifying Global Billing Settings and subscription_plans Schema ---');
  const plans = await query('SELECT * FROM subscription_plans');
  console.log(`Found ${plans.length} subscription plans seeded.`);
  
  const columns = await query('SHOW COLUMNS FROM subscription_plans');
  const colNames = columns.map(c => c.Field);
  if (!colNames.includes('duration_days') || colNames.includes('credits_given')) {
    console.error('❌ FAILED: subscription_plans has incorrect columns. duration_days is required, credits_given should be dropped.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: subscription_plans schema verified.');

  // 2. Register New User & Check Free Status
  console.log('\n--- 2. Registering New User & Verifying Free Level ---');
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

  // Retrieve user from db
  const [userRow] = await query('SELECT is_paid, active_plan_name, expiry_date FROM users WHERE id = ?', [testUserId]);
  console.log(`Initial User Status: is_paid=${userRow.is_paid}, active_plan_name=${userRow.active_plan_name}, expiry_date=${userRow.expiry_date}`);
  if (userRow.is_paid !== 0 || userRow.active_plan_name !== 'FREE_LEVEL' || userRow.expiry_date !== null) {
    console.error(`❌ FAILED: Expected clean free user (is_paid=0, FREE_LEVEL, expiry_date=null)`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: New user registered as FREE_LEVEL with no credits or active plan.');

  // 3. Child Device Block for Free Level
  console.log('\n--- 3. Testing Child Device Block for Free Level ---');
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

  console.log(`Child device registration status (Expected 403): ${childVerifyOtpRes.status}`);
  if (childVerifyOtpRes.status !== 403) {
    console.error('❌ FAILED: Child device addition was not blocked for Free Level user.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: Child device registration correctly blocked for Free user.');

  // 4. Website Block for Free Level
  console.log('\n--- 4. Testing Website Registration Block for Free Level ---');
  const siteRes = await postJson('/api/admin/sites/add', {
    site_name: 'Test Store',
    site_url: 'https://teststore.com'
  }, userToken);

  console.log(`Site creation status (Expected 400 or 402): ${siteRes.status}`);
  if (siteRes.status !== 400 && siteRes.status !== 402) {
    console.error('❌ FAILED: Website creation was not blocked for Free Level user.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: Website creation correctly blocked for Free user.');

  // 5. Subscription Purchase (Standard Plan)
  console.log('\n--- 5. Purchasing Subscription (Standard Plan) ---');
  // Get plan details
  const [stdPlan] = await query("SELECT price, duration_days FROM subscription_plans WHERE plan_name = 'Standard' LIMIT 1");
  const purchaseRes = await postJson('/api/v1/subscription/purchase', { plan_name: 'Standard' }, userToken);
  
  console.log(`Purchase status (Expected 200): ${purchaseRes.status}`);
  if (purchaseRes.status !== 200) {
    console.error('❌ FAILED: Subscription purchase failed.');
    process.exit(1);
  }

  const [userRowPaid] = await query('SELECT is_paid, active_plan_name, expiry_date FROM users WHERE id = ?', [testUserId]);
  console.log(`Paid User Status: is_paid=${userRowPaid.is_paid}, active_plan_name=${userRowPaid.active_plan_name}, expiry_date=${userRowPaid.expiry_date}`);
  
  if (userRowPaid.is_paid !== 1 || userRowPaid.active_plan_name !== 'Standard' || !userRowPaid.expiry_date) {
    console.error('❌ FAILED: User was not activated or expiry_date is null.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: Subscription purchased and plan splayed.');

  // Test stacking: purchase again
  const secondPurchaseRes = await postJson('/api/v1/subscription/purchase', { plan_name: 'Standard' }, userToken);
  console.log(`Second Purchase status (Expected 200): ${secondPurchaseRes.status}`);
  const [userRowStacked] = await query('SELECT expiry_date FROM users WHERE id = ?', [testUserId]);
  console.log(`Stacked Expiry Date: ${userRowStacked.expiry_date}`);

  const diffMs = new Date(userRowStacked.expiry_date) - new Date(userRowPaid.expiry_date);
  const diffDays = Math.round(diffMs / (1000 * 60 * 60 * 24));
  console.log(`Difference in days: ${diffDays}`);
  if (diffDays !== stdPlan.duration_days) {
    console.error(`❌ FAILED: Expiry date was not stacked correctly. Difference must be ${stdPlan.duration_days} days, got ${diffDays}`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: Date stacking verified successfully.');

  // 6. Child Device & Site Addition Allowed after Subscribed
  console.log('\n--- 6. Testing Child Device and Site Addition under Paid Subscription ---');
  
  // Insert a new OTP for the second verification attempt
  const childOtpCode2 = '888888';
  const expiresAt2 = new Date(Date.now() + 5 * 60 * 1000);
  await query(
    'INSERT INTO otps (contact, code, expires_at) VALUES (?, ?, ?)',
    [testPhone, childOtpCode2, expiresAt2]
  );

  // Add child device
  const childVerifyOtpResPaid = await postJson('/api/verify-otp', {
    contact: testPhone,
    code: childOtpCode2,
    deviceId: 'device_test_9992', 
    androidId: 'android_test_9992',
    hardwareFingerprint: 'fingerprint_test_9992',
    simSlotIds: 'sim_test_9992',
    deviceModel: 'Test Device 2',
    androidVersion: '13'
  });
  console.log(`Child Device status (Expected 200): ${childVerifyOtpResPaid.status}`);
  if (childVerifyOtpResPaid.status !== 200) {
    console.error('❌ FAILED: Child device addition failed under paid plan.');
    process.exit(1);
  }

  // Add site
  const siteResPaid = await postJson('/api/admin/sites/add', {
    site_name: 'Test Store 2',
    site_url: 'https://teststore2.com'
  }, userToken);
  console.log(`Site status (Expected 200): ${siteResPaid.status}`);
  if (siteResPaid.status !== 200) {
    console.error('❌ FAILED: Site registration failed under paid plan.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: Child device and Site added successfully under subscription.');

  // 7. FCM Token Registration
  console.log('\n--- 7. Testing FCM Token Registration ---');
  const fcmRes = await postJson('/api/v1/subscription/fcm-token', { token: 'mock_firebase_push_token_xyz_123' }, userToken);
  console.log(`FCM Update Status (Expected 200): ${fcmRes.status}`);
  const [fcmUserRow] = await query('SELECT fcm_token FROM users WHERE id = ?', [testUserId]);
  console.log(`Saved token in DB: ${fcmUserRow.fcm_token}`);
  if (fcmUserRow.fcm_token !== 'mock_firebase_push_token_xyz_123') {
    console.error('❌ FAILED: FCM token was not saved correctly.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: FCM token registered successfully.');

  // 8. Admin manualGrace extension
  console.log('\n--- 8. Testing Admin manualGrace extension ---');
  const graceRes = await postJson(`/api/admin/users/${testUserId}/manual-grace`, { credits: 15 }, adminToken);
  console.log(`Grace Extension Status (Expected 200): ${graceRes.status}`);
  const [userRowGrace] = await query('SELECT expiry_date FROM users WHERE id = ?', [testUserId]);
  console.log(`Grace Expiry Date: ${userRowGrace.expiry_date}`);
  const diffGraceMs = new Date(userRowGrace.expiry_date) - new Date(userRowStacked.expiry_date);
  const diffGraceDays = Math.round(diffGraceMs / (1000 * 60 * 60 * 24));
  console.log(`Grace difference in days: ${diffGraceDays}`);
  if (diffGraceDays !== 15) {
    console.error(`❌ FAILED: manualGrace did not add exactly 15 days.`);
    process.exit(1);
  }
  console.log('✅ SUCCESS: Admin manual grace extension working.');

  // 9. Billing Lock Middleware (HTTP 402)
  console.log('\n--- 9. Testing Billing Lock Guard (HTTP 402) ---');
  // Complete profile so standard operations work
  await query('UPDATE users SET profile_complete = 1 WHERE id = ?', [testUserId]);

  // Set user to expired/unpaid
  await query(
    "UPDATE users SET is_paid = 0, expiry_date = NULL, active_plan_name = 'FREE_LEVEL' WHERE id = ?",
    [testUserId]
  );

  // Call stats route (guarded by billing status check)
  const lockedRes = await getJson('/api/dashboard/stats', userToken);
  console.log(`Guarded Endpoint response status for locked user (Expected 402): ${lockedRes.status}`);
  console.log(`Response body error (Expected ACCOUNT_SUSPENDED):`, lockedRes.body.error);
  if (lockedRes.status !== 402 || lockedRes.body.error !== 'ACCOUNT_SUSPENDED') {
    console.error('❌ FAILED: Billing lock guard failed to lock unpaid user with HTTP 402.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: User locked with HTTP 402 ACCOUNT_SUSPENDED.');

  // Restore paid status, call stats again, should succeed
  await query("UPDATE users SET is_paid = 1, active_plan_name = 'Basic', expiry_date = DATE_ADD(CURRENT_DATE(), INTERVAL 5 DAY) WHERE id = ?", [testUserId]);
  const unlockedRes = await getJson('/api/dashboard/stats', userToken);
  console.log(`Guarded Endpoint response status after plan restoration (Expected 200): ${unlockedRes.status}`);
  if (unlockedRes.status !== 200) {
    console.error('❌ FAILED: User remains locked after subscription restored.');
    process.exit(1);
  }
  console.log('✅ SUCCESS: User unlocked after subscription restoration.');

  console.log('\n🎉 ALL BILLING ENGINE VERIFICATION TESTS PASSED SUCCESSFULLY! 🎉');
  process.exit(0);
}

runTests().catch(e => {
  console.error('Test execution failed:', e);
  process.exit(1);
});
