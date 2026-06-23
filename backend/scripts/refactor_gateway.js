const fs = require('fs');

let content = fs.readFileSync('D:/payment_checker_native_android/backend/controllers/gatewayController.js', 'utf8');

// 1. fetchGatewayMethodsForUser signature and logic
content = content.replace(
  /async function fetchGatewayMethodsForUser\(userId\) \{/,
  `async function fetchGatewayMethodsForUser(userId, deviceId) {
  if (deviceId) {
    try {
      await prisma.gateway_methods.updateMany({
        where: { user_id: String(userId), device_id: '' },
        data: { device_id: deviceId }
      });
    } catch(e) { console.error('Fallback update error', e); }
  }`
);

// 2. update query
content = content.replace(
  /WHERE gm.user_id = \$\{userId\}/,
  'WHERE gm.user_id = ${userId} AND gm.device_id = ${deviceId}'
);

// 3. fetchGatewayMethodsForUser calls
content = content.replace(
  /const data = await fetchGatewayMethodsForUser\(userId\);/g,
  `const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const data = await fetchGatewayMethodsForUser(userId, deviceId);`
);
content = content.replace(
  /const updatedData = await fetchGatewayMethodsForUser\(userId\);/g,
  `const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';
    const updatedData = await fetchGatewayMethodsForUser(userId, deviceId);`
);

// 4. Update where clauses
content = content.replace(
  /where: \{ id: item.id, user_id: String\(userId\) \}/g,
  'where: { id: item.id, user_id: String(userId), device_id: String(deviceId) }'
);
content = content.replace(
  /where: \{ id: methodId, user_id: String\(userId\) \}/g,
  'where: { id: methodId, user_id: String(userId), device_id: String(deviceId) }'
);
content = content.replace(
  /where: \{ user_id: String\(userId\) \}/g,
  'where: { user_id: String(userId), device_id: String(deviceId) }'
);

// 5. addGatewayMethod deviceId extract
content = content.replace(
  /const userId = req.user.userId;\n    const \{ sim_slot, provider, template_id, number \} = req.body;/,
  `const userId = req.user.userId;\n    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';\n    const { sim_slot, provider, template_id, number } = req.body;`
);

// 6. updatePriority deviceId extract
content = content.replace(
  /const userId = req.user.userId;\n    const \{ items \} = req.body;/,
  `const userId = req.user.userId;\n    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';\n    const { items } = req.body;`
);

// 7. toggleMethod deviceId extract
content = content.replace(
  /const userId   = req.user.userId;\n    const methodId = parseInt\(req.params.id\);/,
  `const userId   = req.user.userId;\n    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';\n    const methodId = parseInt(req.params.id);`
);

// 8. updateMethod deviceId extract
content = content.replace(
  /const userId   = req.user.userId;\n    const methodId = parseInt\(req.params.id\);/,
  `const userId   = req.user.userId;\n    const deviceId = req.headers['x-device-id'] || req.body.deviceId || req.user.deviceId || '';\n    const methodId = parseInt(req.params.id);`
);

// 9. addGatewayMethod create
content = content.replace(
  /user_id: String\(userId\),/,
  'user_id: String(userId),\n        device_id: String(deviceId),'
);

fs.writeFileSync('D:/payment_checker_native_android/backend/controllers/gatewayController.js', content);
