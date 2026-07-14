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

  /**
   * Escape iframe + optional ngrok free-tier warm-up before leaving to bKash.
   * After PIN, bKash sends the browser to PUBLIC_BASE_URL — free ngrok shows a
   * interstitial unless the user already clicked "Visit Site" once in this browser.
   */
  redirectBreakout(res, url, opts = {}) {
    if (!url || typeof url !== 'string') {
      throw new Error('RedirectService: invalid url');
    }
    const publicBase = String(opts.publicBase || '').replace(/\/$/, '');
    const needsNgrokWarmup = /ngrok(-free)?\.(app|dev|io)$/i.test(publicBase)
      || /ngrok-free\.dev/i.test(publicBase);

    const safe = String(url).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    const jsUrl = JSON.stringify(url);
    const jsWarm = JSON.stringify(publicBase || '');

    if (!needsNgrokWarmup) {
      res.status(200).type('html').send(
        `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">`
        + `<title>Redirecting…</title></head><body style="font-family:system-ui,sans-serif;padding:24px;text-align:center;max-width:480px;margin:40px auto">`
        + `<p>পেমেন্ট পেজে নিয়ে যাওয়া হচ্ছে…</p>`
        + `<p><a href="${safe}">এখানে ক্লিক করুন</a></p>`
        + `<script>(function(){var u=${jsUrl};try{(window.top||window).location.replace(u);}catch(e){window.location.replace(u);}})();</script>`
        + `</body></html>`,
      );
      return;
    }

    res.status(200).type('html').send(
      `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">`
      + `<title>bKash — একবার প্রস্তুতি</title>`
      + `<style>
        body{font-family:system-ui,sans-serif;padding:24px;max-width:520px;margin:40px auto;line-height:1.5;color:#0f172a}
        .card{border:1px solid #e2e8f0;border-radius:14px;padding:20px;background:#fff}
        h1{font-size:1.15rem;margin:0 0 12px}
        ol{margin:0 0 16px;padding-left:1.2rem}
        a.btn,button.btn{display:inline-block;margin:6px 6px 0 0;padding:12px 16px;border-radius:10px;border:none;font-weight:600;cursor:pointer;text-decoration:none}
        a.primary,button.primary{background:#E2136E;color:#fff}
        a.secondary{background:#f1f5f9;color:#0f172a}
        .hint{font-size:0.9rem;color:#64748b}
      </style></head><body><div class="card">`
      + `<h1>bKash পেমেন্ট — এক ধাপ আগে</h1>`
      + `<p class="hint">ফ্রি ngrok রিটার্ন URL-এ সতর্কতা পেজ দেখায়। পেমেন্টের <b>আগে</b> একবার অনুমতি দিন, নাহলে পিন দেওয়ার পর আবার সেই পেজে আটকে যাবেন।</p>`
      + `<ol>`
      + `<li><a class="btn secondary" href=${jsWarm} target="_blank" rel="noopener">Gateway bridge খুলুন</a> → <b>Visit Site</b> চাপুন</li>`
      + `<li>ট্যাব বন্ধ করে নিচে <b>bKash এ যান</b> চাপুন</li>`
      + `</ol>`
      + `<button type="button" class="btn primary" id="goBkash">bKash এ যান</button>`
      + `<p class="hint" style="margin-top:16px">অথবা সরাসরি: <a href="${safe}">পেমেন্ট লিংক</a></p>`
      + `</div><script>
        (function(){
          var u=${jsUrl};
          var warm=${jsWarm};
          document.getElementById('goBkash').addEventListener('click', function(){
            try { (window.top||window).location.href = u; } catch(e) { window.location.href = u; }
          });
          try {
            if (warm && !sessionStorage.getItem('pc_ngrok_warm')) {
              sessionStorage.setItem('pc_ngrok_warm','1');
              window.open(warm, '_blank', 'noopener');
            }
          } catch(e) {}
        })();
      </script></body></html>`,
    );
  },
};

module.exports = RedirectService;
