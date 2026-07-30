const express = require('express');
const router = express.Router();
const prisma = require('../../db/prisma');
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

/** Public Status / Health Endpoint */
router.get('/status', async (_req, res) => {
  try {
    // Check database connection
    await prisma.$queryRaw`SELECT 1`;
    return res.json({
      success: true,
      status: 'operational',
      message: 'All Systems Operational',
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    console.error('[OfficialWebsite] Status check failed:', err);
    return res.json({
      success: false,
      status: 'outage',
      message: 'Partial System Outage',
      timestamp: new Date().toISOString()
    });
  }
});

/** Public reviews list */
router.get('/reviews', async (_req, res) => {
  try {
    const reviews = await prisma.official_reviews.findMany({
      where: { status: 'approved' },
      orderBy: [
        { helpful_count: 'desc' },
        { created_at: 'desc' }
      ]
    });
    return res.json({ success: true, reviews });
  } catch (err) {
    console.error('[OfficialWebsite] fetch reviews error:', err);
    return res.status(500).json({ success: false, error: 'Failed to fetch reviews' });
  }
});

/** Public review submission */
router.post('/reviews', express.json(), async (req, res) => {
  try {
    const { rating, author_name, company, merchant_type, country_flag, comment } = req.body;
    if (!rating || !author_name || !comment) {
      return res.status(400).json({ success: false, error: 'Rating, author name, and comment are required.' });
    }
    const review = await prisma.official_reviews.create({
      data: {
        rating: parseInt(rating) || 5,
        author_name: String(author_name).slice(0, 64),
        company: company ? String(company).slice(0, 128) : null,
        merchant_type: merchant_type ? String(merchant_type).slice(0, 64) : null,
        country_flag: country_flag ? String(country_flag).slice(0, 16) : null,
        comment: String(comment).slice(0, 5000),
        status: 'pending'
      }
    });
    return res.json({ success: true, message: 'Review submitted and pending approval.', review });
  } catch (err) {
    console.error('[OfficialWebsite] create review error:', err);
    return res.status(500).json({ success: false, error: 'Failed to submit review' });
  }
});

/** Public trusted companies list */
router.get('/companies', async (_req, res) => {
  try {
    const companies = await prisma.official_companies.findMany({
      where: { is_verified: 1 },
      orderBy: { priority: 'asc' }
    });
    return res.json({ success: true, companies });
  } catch (err) {
    console.error('[OfficialWebsite] fetch companies error:', err);
    return res.status(500).json({ success: false, error: 'Failed to fetch companies' });
  }
});

module.exports = router;
