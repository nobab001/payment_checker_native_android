const express = require('express');
const router = express.Router();
const { loadOfficialWebsiteCms, HELPLINE_ICON_IDS } = require('../../services/officialWebsiteCms');

/** Public CMS for marketing site (no auth). */
router.get('/site', async (_req, res) => {
  try {
    const content = await loadOfficialWebsiteCms();
    res.set('Cache-Control', 'public, max-age=30');
    return res.json({ success: true, content, icons: HELPLINE_ICON_IDS });
  } catch (err) {
    console.error('[OfficialWebsite] site CMS error:', err);
    return res.status(500).json({ success: false, error: 'Internal Server Error' });
  }
});

module.exports = router;
