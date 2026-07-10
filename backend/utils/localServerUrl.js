/**
 * URL helpers for colocated PayCheck + demo merchant on one Node process.
 * Windows hairpin: server must not HTTP-call its own LAN IP (hangs ~10s/attempt).
 */

function serverPort() {
  return Number(process.env.PORT) || 3000;
}

function isLoopbackHost(hostname) {
  return hostname === '127.0.0.1' || hostname === 'localhost' || hostname === '::1';
}

/** Rewrite same-server LAN/WAN URLs to 127.0.0.1 for in-process HTTP callbacks. */
function resolveInternalServerUrl(url) {
  if (!url || typeof url !== 'string') return url;
  try {
    const parsed = new URL(url);
    const port = serverPort();
    const urlPort = parsed.port ? Number(parsed.port) : (parsed.protocol === 'https:' ? 443 : 80);
    if (urlPort !== port) return url;
    if (isLoopbackHost(parsed.hostname)) return url;
    parsed.hostname = '127.0.0.1';
    return parsed.href;
  } catch {
    return url;
  }
}

/** Align redirect URL with a stored browser origin (from pay/init). */
function rewriteUrlForOrigin(url, origin) {
  if (!url || !origin) return url;
  try {
    const parsed = new URL(url);
    const o = new URL(origin);
    parsed.protocol = o.protocol;
    parsed.host = o.host;
    return parsed.href;
  } catch {
    return url;
  }
}

/** @deprecated Prefer rewriteUrlForOrigin with session.meta.clientOrigin */
function rewriteUrlForRequest(url, req) {
  if (!url || !req) return url;
  try {
    const parsed = new URL(url);
    const proto = req.headers['x-forwarded-proto'] || req.protocol || 'http';
    const host = req.headers['x-forwarded-host'] || req.get('host');
    if (!host) return url;
    parsed.protocol = `${proto}:`;
    parsed.host = host;
    return parsed.href;
  } catch {
    return url;
  }
}

module.exports = {
  serverPort,
  isLoopbackHost,
  resolveInternalServerUrl,
  rewriteUrlForOrigin,
  rewriteUrlForRequest,
};
