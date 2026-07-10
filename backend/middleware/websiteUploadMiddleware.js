/**
 * websiteUploadMiddleware.js — multer config for merchant branding logo uploads.
 */
const multer = require('multer');
const websiteLogoService = require('../services/websiteLogoService');

const logoUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: websiteLogoService.MAX_BYTES, files: 1 },
  fileFilter: (_req, file, cb) => {
    const mime = String(file.mimetype || '').toLowerCase();
    if (websiteLogoService.ALLOWED_MIME.has(mime)) {
      cb(null, true);
    } else {
      cb(new Error('INVALID_FILE_TYPE'));
    }
  },
});

module.exports = { logoUpload };
