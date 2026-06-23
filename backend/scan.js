const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function scanDir(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      if (!['node_modules', '.git', 'prisma'].includes(file)) {
        scanDir(fullPath);
      }
    } else if (file.endsWith('.js')) {
      try {
        execSync(`node -c "${fullPath}"`, { stdio: 'pipe' });
      } catch (err) {
        console.log(`\nSyntax Error in ${fullPath}:\n${err.stderr.toString()}`);
      }
    }
  }
}

console.log('Scanning all .js files for syntax errors...');
scanDir(__dirname);
console.log('Scan complete.');
