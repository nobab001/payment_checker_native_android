const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const SKIP_DIRS = new Set(['node_modules', '.git', 'prisma']);

const stats = {
  filesScanned: 0,
  errorCount: 0,
  errors: [],
};

function checkSyntax(fullPath) {
  const result = spawnSync(process.execPath, ['-c', fullPath], { encoding: 'utf8' });
  stats.filesScanned++;

  if (result.status === 0) return;

  const message =
    (result.stderr && result.stderr.trim()) ||
    (result.stdout && result.stdout.trim()) ||
    result.error?.message ||
    'Unknown error';

  stats.errorCount++;
  stats.errors.push({ path: fullPath, message });
  console.error(`\nSyntax Error in ${fullPath}:\n${message}`);
}

function scanDir(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    if (fs.statSync(fullPath).isDirectory()) {
      if (!SKIP_DIRS.has(file)) {
        scanDir(fullPath);
      }
    } else if (file.endsWith('.js')) {
      checkSyntax(fullPath);
    }
  }
}

const startMs = Date.now();
console.log('Scanning all .js files for syntax errors...');
scanDir(__dirname);

const elapsedSec = ((Date.now() - startMs) / 1000).toFixed(1);
const errorLabel = stats.errorCount === 1 ? 'error' : 'errors';
console.log(
  `Scanned ${stats.filesScanned} .js files in ${elapsedSec}s — ${stats.errorCount} ${errorLabel}`
);

process.exit(stats.errorCount > 0 ? 1 : 0);
