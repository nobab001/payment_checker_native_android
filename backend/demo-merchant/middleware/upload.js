const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const sharp = require('sharp');
const config = require('../config');

const UPLOAD_DIR = path.join(config.staticDir, 'uploads', 'products');
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function ensureUploadDir() {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_IMAGE_BYTES },
  fileFilter: (_req, file, cb) => {
    if (!file.mimetype?.startsWith('image/')) {
      cb(new Error('INVALID_IMAGE_TYPE'));
      return;
    }
    cb(null, true);
  },
});

async function saveProductImage(buffer) {
  ensureUploadDir();
  const filename = `prod_${crypto.randomBytes(8).toString('hex')}.webp`;
  const filepath = path.join(UPLOAD_DIR, filename);
  await sharp(buffer)
    .resize(800, 800, { fit: 'inside', withoutEnlargement: true })
    .webp({ quality: 82 })
    .toFile(filepath);
  return `/demo-merchant/uploads/products/${filename}`;
}

function productImageMiddleware() {
  return upload.single('image');
}

module.exports = {
  productImageMiddleware,
  saveProductImage,
};
