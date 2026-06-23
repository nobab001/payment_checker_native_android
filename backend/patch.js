
const fs = require('fs');
const filesToPatch = [
  'app.js',
  'controllers/adminController.js',
  'controllers/authController.js',
  'controllers/billingController.js',
  'controllers/checkoutController.js',
  'controllers/gatewayController.js',
  'controllers/paymentController.js',
  'cron/billingScheduler.js',
  'services/DeviceBindingService.js',
  'testParse.js',
  'utils/parseRawSms.js'
];
for (const file of filesToPatch) {
  if (!fs.existsSync(file)) continue;
  let content = fs.readFileSync(file, 'utf8');
  const lines = content.split('\n');
  let inSelect = false;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes('select:') || lines[i].includes('select :') || lines[i].includes('include:') || lines[i].includes('include :')) {
      inSelect = true;
    }
    if (inSelect && lines[i].includes('}')) {
      inSelect = false;
    }
    if (!inSelect && !lines[i].includes('select') && !lines[i].includes('include')) {
      const regexTrue = /\b(is_[a-z_]+|has_[a-z_]+|blocked|secure|profileComplete|smsEnabled|gmailEnabled|sim_[a-z_]+_active)\s*:\s*true\b/g;
      const regexFalse = /\b(is_[a-z_]+|has_[a-z_]+|blocked|secure|profileComplete|smsEnabled|gmailEnabled|sim_[a-z_]+_active)\s*:\s*false\b/g;
      lines[i] = lines[i].replace(regexTrue, '1: 1');
      lines[i] = lines[i].replace(regexFalse, '1: 0');
    }
  }
  const newContent = lines.join('\n');
  if (content !== newContent) {
    fs.writeFileSync(file, newContent);
    console.log('Patched', file);
  }
}

