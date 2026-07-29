# CLAUDE.md — PayChek (payment-checker)

Project-specific rules for Claude Code. Read this every session. For deeper detail
see `payment_checker_blueprint.md` and `docs/`.

## What this project is

A monorepo for **PayChek** (`paycheckbd.com`) — a payment-verification system:

- `app/` — **native Android app** (Kotlin, Gradle, package `online.paychek.app`). User + Admin flavors.
- `backend/` — **Node.js / Express** API (Prisma → MySQL, Redis), checkout/payment engines, workers.
- `docs/` — architecture + deployment documentation.
- `payment_checker_blueprint.md` — master technical blueprint (living document).

> Note: the blueprint still describes an older **Flutter** architecture. The shipped
> app is **native Android (Kotlin)**. Treat on-device code under `app/` as the source
> of truth; update the blueprint when you reconcile a section.

## Post-task deploy pipeline (decide by WHAT CHANGED)

After finishing a coding task, pick the action by the files touched:

### App changed (`app/**`, Android UI/services/DTOs)
1. Build debug APK: run `build-apk.bat` (repo root) **or** `gradlew assembleDebug` inside `app/`.
2. Report the APK path: `app/app/build/outputs/apk/debug/app-debug.apk`.
3. **Device check (every build):** run `adb devices`.
   - If a device shows as `device` (USB/wireless): `adb install -r app/app/build/outputs/apk/debug/app-debug.apk`
     then launch `adb shell am start -n online.paychek.app/.MainActivity`.
   - If no device: only give the APK path. Do **not** ask for IP/PORT unless the user wants wireless install.

### Backend only (`backend/**`, npm, env, DB, server-served checkout JS)
1. Restart the local Node server (port 3000) so changes load.
2. Do **not** build an APK.

### Both app + backend
- Restart local server **and** build the APK (with the adb device check above).

## VPS / production deploy — ONLY on explicit request

Never deploy to the VPS or production unless the user explicitly asks. The VPS hosts
**multiple projects** — touch **only** this one.

- SSH: `ssh paycheckbd` (alias for `root@37.60.224.231`, Contabo VPS `vmi3182621`,
  CloudPanel on `:8443`). Key auth via `~/.ssh/id_ed25519` — config in `~/.ssh/config`.
- Domain: `paycheckbd.com`
- App root: `/var/www/payment-checker/` → live code at `current/` (symlink to `releases/<ts>/`);
  also `shared/` (env, uploads, downloads), `backups/`, `logs/`, `scripts/`.
- Shared env: `/var/www/payment-checker/shared/.env`
- PM2 process (this project only): **`payment-checker-api`**
- Production APK: `/var/www/payment-checker/shared/downloads/paycheck.apk` → `https://paycheckbd.com/downloads/paycheck.apk`

**Other PM2 processes on this VPS — NEVER touch:** `smartcalc-api`, `telecom-bot`,
`telecom-dashboard`. (`payment-otp` also runs here — confirm with the user whether it
belongs to PayChek before ever restarting it.)

Backend live deploy (only when asked): deploy code → `npm install --production` if deps
changed → run Prisma/ensure scripts if schema changed → `pm2 reload payment-checker-api`
→ health-check `https://paycheckbd.com/` + a known API route → watch PM2 logs.
See `docs/deployment/vps.md` and `docs/deployment/production.md` for the full checklist.

Building a local APK does **not** update the website download button — publishing the
production APK is a separate explicit step (copy to the shared path, `chmod 644`).

### Never on the VPS
- Restart/redeploy any PM2 process other than `payment-checker-api`.
- Overwrite VPS `shared/.env` DB credentials with local `.env`.
- Force-push `main`.

## Deployment pipeline (repeatable, gated)

Production deploys run through versioned scripts in `backend/scripts/` (runner copies
live on the VPS at `/var/www/payment-checker/scripts/`, kept in sync by `deploy.sh`):

- `deploy.sh [ref]` — backup DB → clone GitHub `main` into `/tmp/deploy/payment-checker`
  → `npm ci` → `prisma generate` → lint (`node --check`) → unit tests
  (`test:payment-unit`, DB-free) → cut `releases/<ts>/` → symlink `shared/`
  (.env/uploads/downloads) → switch `current` → `pm2 reload payment-checker-api`
  → post-health (auto-rollback on failure) → retention (10 releases / 10 backups)
  → cleanup temp. **STOP-on-fail before the symlink switch.** `DRY_RUN=1` builds+tests
  without switching; `SKIP_TESTS=1` skips the test gate (not recommended).
- `rollback.sh [release-dir]` — switch `current` to a previous release + `pm2 reload`.
  Never uses git checkout; recoverable within 2 minutes.
- `healthcheck.sh` — PM2 online + crash-loop check, HTTPS site, local API, an API route,
  Redis PING, MySQL `SELECT 1`, and a recent-fatal scan of the PM2 error log.
- `backup.sh` — `mysqldump` to `backups/db_<ts>.sql.gz`, keeps the latest 10.

Pipeline rules: GitHub `main` is the only production source (never hand-edit production
files); build only in `/tmp/deploy/payment-checker`; reload **only** `payment-checker-api`
(plus `payment-checker-worker` / `payment-checker-socket` if they exist); schema changes
go through additive runtime ensure-guards — never `prisma db push` in the pipeline;
every deploy/rollback/backup writes a timestamped log under `/var/www/payment-checker/logs/`.

## Git — NEVER auto push

- Do **not** stage / commit / push unless the user **explicitly asks**.
- Never force-push `main`; never amend pushed history; no interactive git (`-i`).
- Never commit secrets (`.env`, keystores, private keys, uploaded PII).
- Commit messages: short why-focused (fix/add/update).
- Recommended feature flow: `feature/<name>` branch → local test → commit → push →
  merge to `main` → run `deploy.sh`. Avoid committing substantial work directly to `main`.
- Bug fixes: analyze logs → find root cause → fix locally → test → push → deploy.
  Emergency production hotfix is allowed only if production is down, and the same fix
  must be committed to GitHub immediately afterward.

## Blueprint maintenance

When you add/modify/remove screens, UI elements, API routes, DB fields, or config keys,
update the matching section of `payment_checker_blueprint.md` to reflect the current state.
Keep it a clean current-state blueprint — no changelog summaries at the end.

## Backend conventions

- `routes/` thin wiring → `controllers/` orchestration → `services/` domain logic → `payment/` checkout engines.
- `prisma/schema.prisma` is the schema source of truth; prefer additive `db/ensure-*.js` guards for staged prod schema changes.
- Validate inputs at the edge; consistent JSON error shapes; correct HTTP status codes.
- Never log secrets; never hardcode prod credentials — config from env.
- Keep payment-flow changes isolated under `payment/` + checkout JS; don't break merchant callbacks.

## Pointers

- Deploy detail: `docs/deployment/{vps,staging,production,github-workflow}.md`
- Backend detail: `docs/backend/`
- VPS bootstrap (one-time setup): `backend/scripts/bootstrap-paycheckbd-vps.sh`
- Cursor-era rules (reference only; this file is authoritative for Claude Code): `.cursor/rules/`, `.cursorrules`
