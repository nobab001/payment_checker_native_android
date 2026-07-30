const sharp = require('sharp');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Load configurations
const tokens = JSON.parse(fs.readFileSync(path.join(__dirname, '../design/logo/tokens.json'), 'utf8'));
const versionConf = JSON.parse(fs.readFileSync(path.join(__dirname, '../design/logo/version.json'), 'utf8'));
const brandVersion = versionConf.brandVersion;

const masterSvgPath = path.join(__dirname, '../design/logo/logo-master.svg');
const launcherSvgPath = path.join(__dirname, '../design/logo/launcher_foreground.svg');
const monochromeSvgPath = path.join(__dirname, '../design/logo/logo-monochrome.svg');

const masterSvg = fs.readFileSync(masterSvgPath, 'utf8');
const launcherSvg = fs.readFileSync(launcherSvgPath, 'utf8');
const monochromeSvg = fs.readFileSync(monochromeSvgPath, 'utf8');

// Ensure directories exist
const brandPublicDir = path.join(__dirname, '../backend/public/assets/brand');
fs.mkdirSync(brandPublicDir, { recursive: true });

const resDir = path.join(__dirname, '../app/app/src/main/res');

const manifestPath = path.join(__dirname, '../design/logo/asset-manifest.json');
const manifestEntries = [];

// Helper to calculate sha256 checksum
function getSha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

// Register manifest entry
function addManifest(filePath, width, height, format, size, sha256) {
  const relativePath = path.relative(path.join(__dirname, '..'), filePath).replace(/\\/g, '/');
  manifestEntries.push({
    file: relativePath,
    width,
    height,
    format,
    size,
    sha256,
    brandVersion,
    createdAt: new Date().toISOString()
  });
}

// Generate single asset
async function generateAsset(buffer, outPath, width, height, format, options = {}) {
  let pipeline = sharp(buffer);
  
  if (options.background) {
    // Add white background for legacy launch icons
    const canvas = sharp({
      create: {
        width,
        height,
        channels: 4,
        background: options.background
      }
    });
    
    const resizedSvg = await sharp(buffer).resize(Math.round(width * 0.72), Math.round(height * 0.72)).png().toBuffer();
    pipeline = canvas.composite([{ input: resizedSvg }]);
  } else {
    pipeline = pipeline.resize(width, height);
  }
  
  if (format === 'webp') {
    pipeline = pipeline.webp({ lossless: true });
  } else if (format === 'png') {
    pipeline = pipeline.png();
  } else if (format === 'ico') {
    pipeline = pipeline.png(); // Modern ico uses PNG stream
  }
  
  const outBuffer = await pipeline.toBuffer();
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, outBuffer);
  
  addManifest(outPath, width, height, format, outBuffer.length, getSha256(outBuffer));
  console.log(`Generated: ${outPath} (${width}x${height})`);
}

async function run() {
  console.log(`Starting asset generation for Brand Version: ${brandVersion}`);
  
  // 1. Web / General UI assets
  await generateAsset(Buffer.from(masterSvg), path.join(__dirname, '../design/logo/logo-ui.webp'), 512, 512, 'webp');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'logo-ui.webp'), 512, 512, 'webp');
  
  // Favicons & Apple / Chrome touch icons
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'favicon.ico'), 48, 48, 'ico');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'favicon-16.png'), 16, 16, 'png');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'favicon-32.png'), 32, 32, 'png');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'favicon-48.png'), 48, 48, 'png');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'apple-touch-icon.png'), 180, 180, 'png');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'android-chrome-192.png'), 192, 192, 'png');
  await generateAsset(Buffer.from(masterSvg), path.join(brandPublicDir, 'android-chrome-512.png'), 512, 512, 'png');

  // 2. Android App UI Logo
  await generateAsset(Buffer.from(masterSvg), path.join(resDir, 'drawable/logo_app.webp'), 512, 512, 'webp');
  
  // 3. Android Mipmap densities
  const densities = [
    { name: 'mdpi', size: 48 },
    { name: 'hdpi', size: 72 },
    { name: 'xhdpi', size: 96 },
    { name: 'xxhdpi', size: 144 },
    { name: 'xxxhdpi', size: 192 }
  ];
  
  for (const d of densities) {
    const dPath = path.join(resDir, `mipmap-${d.name}`);
    
    // Legacy launcher icon: shield logo on white background
    await generateAsset(
      Buffer.from(masterSvg),
      path.join(dPath, 'ic_launcher.webp'),
      d.size,
      d.size,
      'webp',
      { background: '#FFFFFF' }
    );
    
    // Legacy round launcher icon: shield logo on white circle (handled via sharp flatten+compositing on white)
    await generateAsset(
      Buffer.from(masterSvg),
      path.join(dPath, 'ic_launcher_round.webp'),
      d.size,
      d.size,
      'webp',
      { background: '#FFFFFF' }
    );
    
    // Adaptive foreground (PNG format for compatibility)
    await generateAsset(
      Buffer.from(launcherSvg),
      path.join(dPath, 'ic_launcher_foreground.png'),
      d.size,
      d.size,
      'png'
    );
    
    // Android 13 Dynamic Monochrome launcher icon
    await generateAsset(
      Buffer.from(monochromeSvg),
      path.join(dPath, 'ic_launcher_monochrome.png'),
      d.size,
      d.size,
      'png'
    );
  }
  
  // Write the asset manifest
  fs.writeFileSync(manifestPath, JSON.stringify({ version: brandVersion, assets: manifestEntries }, null, 2));
  console.log(`Asset manifest successfully written to: ${manifestPath}`);
  console.log('All branding assets generated successfully.');
}

run().catch(err => {
  console.error('Asset generation failed:', err);
  process.exit(1);
});
