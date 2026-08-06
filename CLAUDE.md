# CLAUDE.md — PayChek (payment-checker)

Project rules for **Claude Code**. These must match Cursor Agent behaviour.

**Authoritative rule set (same as Cursor):** read and obey every file in:

- `.claude/rules/`  ← synced copy of Cursor rules (use these)
- `.cursor/rules/`  ← same content (Cursor native format)

If anything conflicts, prefer the more specific file (e.g. checkout / security / deploy) over this overview.

---

## Same-system contract (Cursor chat ↔ Claude Code)

| Surface | Rules source | Code folder |
|---------|--------------|-------------|
| Cursor Agent chat | `.cursor/rules/*.mdc` + `.cursorrules` | Open workspace on **D:** (this repo) |
| Claude Code extension | **this file** + `.claude/rules/*.md` | Same open workspace |

Do **not** invent a second architecture. Work only in this repo path — not a copy under `C:\Users\...` unless that path *is* the opened project.

---

## Always-on rules (quick index)

Read these from `.claude/rules/` every session (or when relevant):

| File | When |
|------|------|
| `00-project-context.md` | Always — stack, repo map, non-negotiables |
| `00-cursorrules-deploy.md` | Always — post-task APK / server / no auto-push |
| `10-ai-behaviour.md` | Always — how to plan and implement |
| `06-security.md` | Always — secrets, HMAC, JWT |
| `07-deployment.md` | Always — local vs VPS |
| `08-git-workflow.md` | Always — git discipline |
| `09-code-quality.md` | Always — clean code |
| `01-ui-ux.md` / `02-design-system.md` | Android UI |
| `03-android.md` | `app/**` |
| `04-backend.md` / `05-api.md` | `backend/**` |
| `checkout-ux-requirements.md` | Checkout HTML/JS |
| `deploy-follow-up.md` | After every coding task |

Also pre-read docs as listed in `00-project-context.md` (`docs/design/*`, `docs/android/*`, `docs/backend/*`, `docs/api/*`, `docs/deployment/*`, `docs/security/*`).

---

## What this project is

Monorepo for **PayChek** (`paycheckbd.com`) — payment verification:

- `app/` — native Android (Kotlin, package `online.paychek.app`), User + Admin flavors
- `backend/` — Node.js / Express, Prisma → MySQL, Redis, checkout engines, workers
- `docs/` — architecture + deployment handbook
- `payment_checker_blueprint.md` — living blueprint (Flutter sections may be outdated; **`app/` is source of truth**)

---

## Post-task deploy pipeline (by WHAT CHANGED)

### App changed (`app/**`)
1. Build **release** APK: `build-apk.bat` (repo root) **or** `gradlew assembleRelease` in `app/`.
2. Report: `app/app/build/outputs/apk/release/app-release.apk`.
3. `adb devices` — if `device`: install release APK + launch MainActivity.
4. **Always** upload release APK to VPS: `/var/www/payment-checker/shared/downloads/paycheck.apk`.

### Backend / website / checkout (`backend/**`)
1. **Always deploy to VPS** from local changes (SSH `paycheckbd` → sync → `pm2 reload payment-checker-api` → health check). Do not wait for a separate ask. Do **not** require GitHub push first.
2. Local Node (port 3000): restart when useful for local smoke tests — keep for now; does not replace VPS.

### Both
- Release APK (+ adb + VPS APK upload) + VPS backend deploy.

### GitHub
- **Never** auto stage/commit/push unless the user **explicitly** asks (e.g. end of day).

---

## VPS / production

> After every coding task that touches backend/website/app download: **always update VPS**. GitHub push is **not** required for that day-to-day deploy. Push only when the user asks. VPS hosts multiple projects — touch **only** this one.

- SSH: `ssh paycheckbd` (`root@37.60.224.231`)
- Domain: `paycheckbd.com`
- App root: `/var/www/payment-checker/` → `current/` symlink, `shared/` (.env, uploads, downloads)
- PM2 for this project: **`payment-checker-api`** only
- Production APK: `/var/www/payment-checker/shared/downloads/paycheck.apk`

**Never touch other PM2 apps:** `smartcalc-api`, `telecom-bot`, `telecom-dashboard` (confirm before touching `payment-otp`).

**Never on VPS:** overwrite `shared/.env` DB creds with local `.env`; force-push `main`.

Local APK build ≠ website download APK. Publishing production APK is a separate explicit step.

Deploy scripts: `backend/scripts/deploy.sh`, `rollback.sh`, `healthcheck.sh`, `backup.sh`. Details: `docs/deployment/*`.

---

## Git

- No commit/push unless asked; no force-push `main`; no interactive git (`-i`).
- Never commit secrets (`.env`, keystores, private keys, PII).
- Prefer `feature/<name>` → test → merge → deploy.

---

## Backend conventions

- `routes/` → `controllers/` → `services/` → `payment/`
- Prisma schema is source of truth; prefer additive `db/ensure-*.js` for staged prod schema
- Validate at edge; consistent JSON errors; never log secrets

## Android conventions

- Compose UI under `ui/screen/...`; ViewModel + StateFlow; Repository → Retrofit
- Config: `AppConfig.kt` (`BASE_URL`) — production default `https://paycheckbd.com/`
- Extend existing SMS/heartbeat/services; do not invent parallel stacks

## Blueprint

When screens/API/DB/config change, update matching sections of `payment_checker_blueprint.md` (current-state only).

---

## Multi-editor / multi-agent safety (why phone builds miss features)

If several editors/agents edit the same project:

1. **Save everything** before build — unsaved buffers are not in the APK.
2. **One workspace path** — always `D:\payment_checker_native_android`, not a second copy.
3. **Same branch** — check `git status` / branch; uncommitted work in another window is not in your build.
4. **Rebuild + reinstall** — after changes: clean assemble if needed, then `adb install -r` (old APK otherwise).
5. **Correct flavor** — User vs Admin; wrong flavor looks like “missing features”.
6. **Backend running** — app-only install will not show API-dependent features if server was not restarted/deployed.
7. Prefer **one agent finishing a feature** before another starts overlapping files, or merge carefully.

---

## Pointers

- Rules (Claude): `.claude/rules/`
- Rules (Cursor): `.cursor/rules/`, `.cursorrules`
- Deploy docs: `docs/deployment/`
- Backend docs: `docs/backend/`
- Design: `docs/design/`
