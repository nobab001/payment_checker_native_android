const fs = require('fs');
const path = require('path');

function fixFile(filePath) {
  let text = fs.readFileSync(filePath, 'utf8');
  let original = text;
  text = text.split('\`').join('`');
  text = text.split('\$').join('$');
  if (text !== original) {
    fs.writeFileSync(filePath, text);
    console.log(`Fixed ${filePath}`);
  }
}

const dirs = [
  __dirname,
  path.join(__dirname, 'controllers'),
  path.join(__dirname, 'services'),
  path.join(__dirname, 'utils'),
  path.join(__dirname, 'middleware'),
  path.join(__dirname, 'cron')
];

for (const dir of dirs) {
  if (fs.existsSync(dir)) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
      if (file.endsWith('.js')) {
        fixFile(path.join(dir, file));
      }
    }
  }
}
