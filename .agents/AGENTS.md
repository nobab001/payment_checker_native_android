# Project-Scoped Rules — PayChek (Payment Checker)
> **Authority order:** This file is the single source of truth for Antigravity IDE.
> CLAUDE.md and `.cursor/rules/*.mdc` remain as cross-editor references but **this file governs**.

---

## 0. Session Start — Mandatory Context Load

At the **start of every new chat session**:
1. Read `payment_checker_blueprint.md` (master technical blueprint) to understand architecture, DB schema, APIs, and screen structures.
2. If prior context is needed, read `active_chat_history.md`.
3. Read `docs/project/project-context.md` before any non-trivial change.

---

## 1. Project Identity & Stack

**Product**: PayChek — SMS Payment Verification System for Bangladesh MFS (bKash, Nagad, Rocket, Upay).  
**Domain**: `paycheckbd.com`  
**Package**: `online.paychek.app`

| Layer | Tech |
|-------|------|
| Android | Kotlin, Jetpack Compose, Material 3, Navigation3, ViewModel, StateFlow, Retrofit, WorkManager, Foreground Services |
| Backend | Node.js, Express, Prisma → MySQL, Redis, JWT, HMAC |
| Checkout | Server-served JS under `backend/public/js/checkout/` |
| Hosting | VPS `paycheckbd.com`, PM2, Nginx, `/var/www/payment-checker/` |

### Repo Map
- `app/` — Android client (`online.paychek.app`) — **Kotlin, native** (blueprint may say Flutter; treat `app/` as source of truth)
- `backend/` — API, workers, checkout, official website
- `docs/` — Engineering handbook
- `.cursor/rules/` — Cursor editor rules (reference)
- `payment_checker_blueprint.md` — Living technical blueprint (always keep updated)

### Non-Negotiables
- Do **not** invent a second architecture or design language.
- Prefer **extending existing modules** over new parallel stacks.
- App API base URL lives in `AppConfig.kt` (`BASE_URL`). Production default: `https://paycheckbd.com/`. Never scatter base URLs.

---

## 2. Blueprint Maintenance (Living Document)

Whenever you make changes to the codebase (add/modify/delete screens, buttons, API routes, DB fields, config keys):

- **MUST** update the matching section in `payment_checker_blueprint.md` to reflect the exact current state.
- **Pattern**:
  - New element → append its details to the respective section.
  - Updated element → modify its description to match new behavior.
  - Removed element → delete its description entirely.
- **Do NOT** write changelog summaries at the end. Keep it a clean current-state blueprint.
- After schema changes also update `docs/backend/database.md`.

---

## 3. Post-Task Deploy Pipeline (decide by WHAT changed)

### A. App changed (`app/**`, Android UI/services/DTOs)
1. Build release APK: run `build-apk.bat` (repo root) **or** `gradlew assembleRelease` inside `app/`.
2. Report APK path: `app/app/build/outputs/apk/release/app-release.apk`.
3. **Device check (every build):** run `adb devices`.
   - If a device shows as `device`: `adb install -r app/app/build/outputs/apk/release/app-release.apk` → then launch `adb shell am start -n online.paychek.app/.MainActivity`.
   - If no device connected: only give the APK path. Do **not** ask for IP/PORT unless user wants wireless install.

### B. Backend / Website / Checkout page (`backend/**`, checkout JS, HTML, CSS)
1. Restart local Node server (port 3000) if testing locally.
2. **Website & Checkout Auto-Deploy Rule**: As requested by user, whenever website, checkout page (`backend/public/**`), or backend API changes are completed, **ALWAYS automatically deploy to VPS (`paycheckbd.com`)** via git push + `deploy.sh` so changes are immediately live for users without needing a separate ask.

### C. Both app + backend
- Restart local server, build the release APK (with adb device check), and deploy backend/website changes to VPS automatically.

---

## 4. VPS / Production Deploy — Rules & Pipeline

> **User Exception Rule**: Website and checkout page (`backend/public/**`) changes MUST be auto-deployed to VPS (`paycheckbd.com`) upon completion of the task. For other major structural changes, follow standard safety..
- This overrides any older rule that said "always push after implementation".
- Commit messages: short, why-focused (fix/add/update).
- Never force-push `main`; never amend pushed history; no interactive git (`-i`).
- Never commit secrets (`.env`, keystores, private keys, PII).
- Feature flow: `feature/<name>` branch → local test → commit → push → merge to `main` → `deploy.sh`.

---

## 4. VPS / Production Deploy — ONLY on Explicit Request

**Never deploy to VPS or production unless the user explicitly asks.**

### VPS Access
- SSH alias: `ssh paycheckbd` → `root@37.60.224.231` (Contabo VPS `vmi3182621`)
- Key: `~/.ssh/id_ed25519` | CloudPanel: `:8443`
- App root: `/var/www/payment-checker/` → `current/` (symlink) → `releases/<ts>/`
- Shared env: `/var/www/payment-checker/shared/.env`
- PM2 process: **`payment-checker-api`** (only this one — never touch others)

### Other PM2 processes — NEVER touch
`smartcalc-api`, `telecom-bot`, `telecom-dashboard`. (`payment-otp` — confirm with user before restarting.)

### Live Deploy Steps (when explicitly asked)
1. Deploy code via `deploy.sh [ref]` (in `backend/scripts/`).
2. `npm install --production` if deps changed.
3. Prisma/ensure scripts if schema changed.
4. `pm2 reload payment-checker-api`.
5. Health-check `https://paycheckbd.com/` + a known API route + watch PM2 logs.

### Production APK Upload (when explicitly asked)
- Copy to: `/var/www/payment-checker/shared/downloads/paycheck.apk`
- Run: `chmod 644` on the file.
- URL: `https://paycheckbd.com/downloads/paycheck.apk`
- Local APK build does **not** auto-update this download link.

### deploy.sh Pipeline Summary
`deploy.sh [ref]` → DB backup → clone GitHub `main` → `npm ci` → `prisma generate` → lint → unit tests → cut `releases/<ts>/` → symlink `shared/` → switch `current` → `pm2 reload` → post-health (auto-rollback on failure) → keep 10 releases / 10 backups.
- `DRY_RUN=1` — builds + tests without switching.
- `SKIP_TESTS=1` — skips test gate (not recommended).
- `rollback.sh [release-dir]` — switch `current` to previous + `pm2 reload` (recoverable within 2 min).

### VPS Never-Do List
- Restart/redeploy any PM2 process other than `payment-checker-api`.
- Overwrite VPS `shared/.env` DB credentials with local `.env`.
- Force-push `main`.
- Deploy untested payment-flow changes directly to production without confirmation.

---

## 5. Android Engineering Rules

Read before Android work: `docs/android/android-architecture.md`, `docs/android/services.md`, `docs/android/accessibility.md`

### Architecture
- **UI**: Jetpack Compose screens under `ui/screen/...`
- **State**: ViewModel + `StateFlow` / `collectAsState`
- **Data**: Repository → Retrofit API services → DTOs
- **Navigation**: Navigation3 keys in `NavigationKeys.kt` / `Navigation.kt`
- **Config**: `AppConfig.kt` for `BASE_URL` / prefs keys — never scatter base URLs

### Patterns
- Business logic in ViewModel / repository — screens stay UI + wiring only.
- Prefer existing utilities (`DeviceIdHelper`, `SecurePreferences`, adaptive size helpers).
- New screens: match existing package layout; no orphan God-activities.

### Services & Background
- SMS / heartbeat / keep-alive: extend existing workers/services (`services/sync`, `services/foreground`, `services/sms`).
- WorkManager for deferred/retryable work; Foreground Service only for continuous capture.
- Accessibility / overlay work: follow existing hardened patterns.

### Permissions & Lifecycle
- Request permissions at point of need with clear UX rationale.
- Cancel collectors/jobs in ViewModel `onCleared`; avoid leaking Context in companions.

### Performance
- No heavy work on main thread — use coroutines with proper dispatchers.
- Minimize recomposition (stable params, avoid huge lambdas capturing unstable state).
- No unnecessary animation libraries.

### DI / Storage
- Use the project's established DI/storage approach. Do not introduce a second DI framework or parallel DB stack without an architecture decision recorded in `docs/`.

---

## 6. Backend Engineering Rules

Read before backend work: `docs/backend/backend-architecture.md`, `docs/backend/database.md`, `docs/backend/folder-structure.md`, `docs/api/api-guideline.md`

### Structure
- `routes/` → thin HTTP wiring
- `controllers/` → request orchestration
- `services/` → domain logic
- `payment/` → checkout/payment engines
- `prisma/schema.prisma` → schema source of truth
- `db/ensure-*.js` → additive production-safe schema guards

### Rules
- Validate all inputs at the edge (controller or middleware).
- Return consistent JSON error shapes; use correct HTTP status codes.
- **Never log secrets** (JWT, HMAC keys, SMTP passwords, API secrets).
- Rate-limit sensitive endpoints (auth, OTP, public init).
- Keep payment flow changes isolated under `payment/` + checkout JS — do not break merchant callbacks.
- Redis/MySQL config from env — never hardcode production credentials in source.
- Schema change → update Prisma + document in `docs/backend/database.md`.

---

## 7. Security Rules

Read before security-sensitive work: `docs/security/`

- Never store raw passwords or PINs — always hash (bcrypt/HMAC).
- JWT tokens: short-lived, server-side invalidation on logout/rejection.
- Device IDs: hardware-bound, stored server-side; validate `X-Device-Id` header on protected endpoints.
- OTP: rate-limit sends; enforce expiry (< 10 min); mark `used_at` on consume.
- Never commit `.env`, keystore, private key, or PII to git.
- API keys in requests: always via HTTPS; never in query strings for secrets.
- CORS: explicit allow-list, never wildcard `*` on auth routes.

---

## 8. Code Quality Rules

- Run `node --check <file>` after any backend JS change to verify syntax before committing.
- Kotlin: no unused imports; `when` expressions exhaustive where possible.
- No TODOs left in submitted code unless marked `// TODO(issue-N):`.
- Don't break existing tests; add tests for non-trivial logic.
- Keep functions focused; extract helpers > 30-line inline blocks.
- Comment **why**, not **what**, for complex logic.

---

## 9. UI / UX Rules (Android)

Read before UI work: `docs/design/design-system.md`, `.cursor/rules/01-ui-ux.mdc`, `.cursor/rules/02-design-system.mdc`

### Color Palette (Canonical)
| Role | Value |
|------|-------|
| Primary | `#1A237E` (Dark Royal Indigo) |
| bKash | `#E2136E` (Hot Pink) |
| Nagad | `#EF4123` (Flame Orange-Red) |
| Rocket | `#6A2C91` (Violet Purple) |
| Upay | `#00B99B` (Vibrant Teal) |
| App Background | `#F5F7FA` |
| Card Background | `#FFFFFF` |
| Text Primary | `#212121` |
| Text Secondary | `#757575` |

### UI Rules
- Follow Material 3 guidelines; use existing theme tokens — no ad-hoc color literals.
- Match existing component patterns (cards, chips, dialogs) before building new ones.
- Use existing dimension/spacing helpers; no magic pixel numbers.
- Accessibility: content descriptions on icons; tap targets ≥ 48dp.
- Checkout UX: see `.cursor/rules/checkout-ux-requirements.mdc` for detailed checkout UI requirements.

---

## 10. AI Behaviour Rules

- **Never guess file paths** — verify with the actual directory/file structure first.
- **Ask before deleting** any production file, DB record, or irreversible action.
- If a task requires touching >3 files or >50 lines of logic, summarize the plan first and wait for confirmation.
- **Do not hallucinate APIs** — only use endpoints documented in the blueprint or existing `routes/` files.
- If an instruction in this file conflicts with a user message in the same session, **follow the user message** and flag the conflict.
- For ambiguous requirements: ask one focused clarifying question rather than making assumptions.
- **Never auto-run `pm2 restart` or `pm2 reload` on VPS without explicit user instruction.**
- **Language of Plans**: Always write and present the Implementation Plan (`implementation_plan.md`) in Bengali (বাংলা).

---

## 11. Quick Reference — Key File Locations

| What | Where |
|------|-------|
| Master Blueprint | `payment_checker_blueprint.md` |
| Android Entry | `app/app/src/main/java/online/paychek/app/` |
| Android Config | `AppConfig.kt` |
| Backend Entry | `backend/app.js` |
| DB Schema | `backend/prisma/schema.prisma` + `backend/schema.sql` |
| VPS Deploy Script | `backend/scripts/deploy.sh` |
| VPS Rollback | `backend/scripts/rollback.sh` |
| Health Check | `backend/scripts/healthcheck.sh` |
| Docs | `docs/` |
| Cursor Rules | `.cursor/rules/` |
| Build APK | `build-apk.bat` (repo root) |
| Checkout UI | `backend/public/js/checkout/` |
| Official Website | `backend/official-website/` |

---

## 12. Pointers to Detailed Documentation

- Deploy detail: `docs/deployment/{vps,staging,production,github-workflow}.md`
- Backend detail: `docs/backend/`
- Android detail: `docs/android/`
- API guidelines: `docs/api/api-guideline.md`
- Design system: `docs/design/design-system.md`
- VPS bootstrap (one-time): `backend/scripts/bootstrap-paycheckbd-vps.sh`
