<!-- Synced from .cursor/rules/deploy-follow-up.mdc -->

# Deploy follow-up (PayChek)

After finishing a coding task, keep **VPS + release artifacts** up to date. Decide by **what changed**.

## App changed (`app/**` or Android UI/services)
1. Build **release** APK: `build-apk.bat` (repo root) **or** `gradlew assembleRelease` in `app/`.
2. Report: `app/app/build/outputs/apk/release/app-release.apk`.
3. **Do not** use `assembleDebug` unless the user asks for a debug build.
4. **Device check:** `adb devices` → if `device`, `adb install -r` the release APK → launch `online.paychek.app/.MainActivity`.
5. **Always** upload that release APK to VPS:
   - `/var/www/payment-checker/shared/downloads/paycheck.apk`
   - `chmod 644`
   - URL: `https://paycheckbd.com/downloads/paycheck.apk`

## Backend / website / checkout (`backend/**`, npm, checkout JS, official site under backend)
1. **Always deploy to VPS** when the task completes — no waiting for an extra deploy request.
2. Sync from **local workspace** to VPS (SSH alias `paycheckbd`). Do **not** gate VPS deploy on a GitHub push.
3. Reload only PM2 **`payment-checker-api`**, then health-check `https://paycheckbd.com/`.
4. Local Node (port 3000): **still restart for now** when useful for local smoke tests — it does **not** replace VPS deploy.

## Both app + backend
- Release APK (+ adb) + VPS APK upload **and** VPS backend deploy + health check.

## GitHub
- Do **not** auto stage/commit/push after tasks.
- Push to GitHub **only** when the user explicitly asks (typically once at end of day).

## Never
- Force-push `main`
- Touch other PM2 apps (`smartcalc-api`, `telecom-bot`, `telecom-dashboard`)
- Overwrite VPS `shared/.env` DB credentials with local `.env` blindly
