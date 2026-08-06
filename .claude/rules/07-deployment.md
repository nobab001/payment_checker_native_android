<!-- Synced from .cursor/rules/07-deployment.mdc -->

# Deployment Rules

Read: `docs/deployment/vps.md`, `docs/deployment/staging.md`, `docs/deployment/production.md`, `docs/deployment/github-workflow.md`

## After coding tasks (agent — current policy)

1. **App changed** (`app/**`): `gradlew assembleRelease` (or `build-apk.bat`); report `app/app/build/outputs/apk/release/app-release.apk`; adb install/launch if device; **upload** to `/var/www/payment-checker/shared/downloads/paycheck.apk`.
2. **Backend / website / checkout** (`backend/**`): **always deploy to VPS** from local changes (SSH `paycheckbd` → sync → `pm2 reload payment-checker-api` → health check). Do not wait for the user to ask.
3. **Both**: release APK + VPS APK upload + VPS backend deploy.
4. **Local Node** (port 3000): restart when useful for local testing — **keep for now**; does not replace VPS.
5. **GitHub**: never auto stage/commit/push unless the user explicitly asks.

## Production truth

- Live API/site: `https://paycheckbd.com/` on VPS PM2 `payment-checker-api`.
- Live download APK: `/var/www/payment-checker/shared/downloads/paycheck.apk`.
- `deploy.sh` clones GitHub `main` — use it when the user has pushed / asks for script-based release. Day-to-day agent deploys may sync local → VPS without a push.
- Never force-push `main`. Never overwrite VPS `shared/.env` DB credentials from local `.env` blindly.

## Never

- Force-push `main`
- Deploy by touching unrelated PM2 processes
- Skip VPS deploy after backend/website work “because GitHub was not pushed”
