/**
 * @file Redirect Service — URL building and HTTP redirect only. No payment logic.
 * @module payment/redirect/redirect-service
 */

const RedirectService = {
  /**
   * Checkout live-init response URL — preserves legacy `/pay/:token` flow.
   * External gateway redirect happens later in GET /pay/:token.
   * @param {string} baseUrl
   * @param {string} sessionToken
   */
  buildPayTokenUrl(baseUrl, sessionToken) {
    const base = String(baseUrl || '').replace(/\/$/, '');
    return `${base}/pay/${sessionToken}`;
  },

  /**
   * JSON payload for checkout live-init (unchanged API contract).
   * @param {string} redirectUrl
   */
  liveInitJson(redirectUrl) {
    return { success: true, redirectUrl };
  },

  /**
   * @param {import('express').Response} res
   * @param {string} url
   * @param {number} [status=302]
   */
  redirect(res, url, status = 302) {
    if (!url || typeof url !== 'string') {
      throw new Error('RedirectService: invalid url');
    }
    return res.redirect(status, url);
  },
};

module.exports = RedirectService;
