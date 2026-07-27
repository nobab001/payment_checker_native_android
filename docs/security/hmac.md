# HMAC

## Purpose

Authenticate and integrity-check sensitive payloads between Android SMS pipeline and backend so forged SMS posts cannot credit payments.

## Typical usage

1. Server provisions a per-account or per-device **HMAC secret** (returned on successful auth when applicable).  
2. App stores secret encrypted (`SecurePreferences`, key referenced from SMS receiver constants).  
3. Outbound SMS ingest requests include signature headers/fields derived from payload + secret.  
4. Server recomputes HMAC and rejects mismatches.

Exact header/field names follow the live implementation in SMS receiver + auth/payment controllers — when changing them, update **both** client and server atomically and bump compatibility carefully.

## Replay protection

Where implemented, include:

- Timestamp window (reject stale requests)  
- Nonce / unique SMS id / message hash uniqueness  
- Server-side dedupe on `sms_history` constraints  

Do not remove replay checks to “make testing easier” in production builds.

## Key management

- Rotate secrets on compromise  
- Never commit secrets  
- Do not print secrets in Logcat or PM2 logs  

## Testing

Use staging keys. For local tests, isolated secrets only — never production HMAC material on developer devices that leave the team.
