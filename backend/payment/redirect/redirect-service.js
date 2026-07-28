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
   * JSON payload for checkout live-init.
   * Prefer direct gateway URL (bkashURL) so the client can location.replace()
   * without visiting /pay/:token HTML intermediate.
   * @param {string} redirectUrl
   * @param {{ bkashURL?: string|null, sessionToken?: string|null }} [extra]
   */
  liveInitJson(redirectUrl, extra = {}) {
    const payload = { success: true, redirectUrl };
    if (extra.bkashURL) payload.bkashURL = extra.bkashURL;
    if (extra.sessionToken) payload.sessionToken = extra.sessionToken;
    return payload;
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
   * Leave to gateway URL without leaving a sticky HTML page in history.
   * Prefer HTTP 302 (browser typically does not keep the intermediate URL).
   * Ngrok free-tier still needs a one-time warm-up UI.
   */
  redirectBreakout(res, url, opts = {}) {
    if (!url || typeof url !== 'string') {
      throw new Error('RedirectService: invalid url');
    }
    const publicBase = String(opts.publicBase || '').replace(/\/$/, '');
    const needsNgrokWarmup = /ngrok(-free)?\.(app|dev|io)$/i.test(publicBase)
      || /ngrok-free\.dev/i.test(publicBase);

    if (!needsNgrokWarmup) {
      // Direct 302 — no "Redirecting to bKash…" HTML for Back to stick on.
      res.set('Cache-Control', 'no-store');
      return res.redirect(302, url);
    }

    const safe = String(url).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    const jsUrl = JSON.stringify(url);
    const jsWarm = JSON.stringify(publicBase || '');

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
      + `<p class="hint" style="margin-top:16px">অথবা সরাসরি: <a id="bkashLink" href="${safe}">পেমেন্ট লিংক</a></p>`
      + `</div><script>
        (function(){
          var u=${jsUrl};
          var warm=${jsWarm};
          function go(){
            try { (window.top||window).location.replace(u); } catch(e) { window.location.replace(u); }
          }
          document.getElementById('goBkash').addEventListener('click', go);
          var link = document.getElementById('bkashLink');
          if (link) link.addEventListener('click', function(ev){ ev.preventDefault(); go(); });
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
