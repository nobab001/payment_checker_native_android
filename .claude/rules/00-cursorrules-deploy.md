<!-- Synced from .cursorrules -->

# PayChek — post-task deploy rules (updated)

After completing a coding task, apply **all** of the following that match what changed. Keep the live VPS and local artifacts up to date.

## 1. App changed (`app/**`)
1. Build **release** APK — `build-apk.bat` (repo root) **or** `gradlew assembleRelease` inside `app/`.
2. Report path: `app/app/build/outputs/apk/release/app-release.apk`.
3. **Do not** build debug APK unless the user explicitly asks for debug.
4. Device check: `adb devices` — if a device is `device`, `adb install -r` the **release** APK and launch `online.paychek.app/.MainActivity`.
5. Upload the same release APK to VPS so the website download stays current:
   - Target: `/var/www/payment-checker/shared/downloads/paycheck.apk`
   - `chmod 644` on the file
   - Public URL: `https://paycheckbd.com/downloads/paycheck.apk`

## 2. Backend / website / checkout (`backend/**`, public assets, API)
1. **Always deploy to VPS** when the task finishes — do **not** wait for a separate “deploy” ask.
2. Prefer syncing **local** changed files to the live app (SSH `paycheckbd`) then `pm2 reload payment-checker-api` (only this PM2 process), then health-check `https://paycheckbd.com/`.
3. Do **not** require a GitHub push before VPS deploy.
4. Local Node on port 3000: **keep restarting for now** when backend changed and local testing is useful. This does not replace VPS deploy.

## 3. Both app + backend
- Release APK (+ adb if device) + VPS APK upload **and** VPS backend deploy + health check.
- Local Node restart may still be done for local backend smoke tests.

## 4. GitHub
- Do **not** auto stage / commit / push after tasks.
- Push **only** when the user explicitly asks (e.g. end-of-day “push করো”).
- Never force-push `main`.

## 5. Keep everything up to date
- After each task: live VPS reflects the work; release APK path (and download link when app changed) is current; tell the user what was deployed.
