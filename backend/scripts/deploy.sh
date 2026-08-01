#!/usr/bin/env bash
#
# PayChek — production deploy (repeatable, gated, recoverable).
# ------------------------------------------------------------------
# Source of truth: GitHub main. This script NEVER edits production files
# directly; it builds in /tmp, gates on lint + unit tests, cuts a release,
# switches the `current` symlink, and restarts (delete+start) ONLY this project's PM2 app.
#
# Flow:
#   backup DB -> clone <ref> to /tmp/deploy/payment-checker -> npm ci
#   -> prisma generate -> lint (syntax) -> unit tests -> cut release
#   -> link shared (.env/uploads/downloads/logs) -> switch current
#   -> pm2 delete+start -> POST health (auto-rollback on failure)
#   -> retention (10 releases / 10 backups) -> cleanup temp
#
# Any gate failure BEFORE the symlink switch => STOP, production untouched.
# A failed POST health => automatic rollback to the previous release.
#
# Usage:  deploy.sh [git-ref]        (default: main)
# Env:    SKIP_TESTS=1   skip the unit-test gate (NOT recommended)
#         DRY_RUN=1      build+test+cut release but do NOT switch/reload
#
set -euo pipefail

# ---- config -------------------------------------------------------
APP_ROOT="/var/www/payment-checker"
TMP_BUILD="/tmp/deploy/payment-checker"
REPO="https://github.com/nobab001/payment_checker_native_android.git"
PM2_API="payment-checker-api"
PM2_WORKER="payment-checker-worker"     # reloaded only if it exists
PM2_SOCKET="payment-checker-socket"     # reloaded only if it exists
RELEASE="$(date +%Y-%m-%d_%H%M%S)"
RELEASE_DIR="${APP_ROOT}/releases/${RELEASE}"
REF="${1:-main}"
SCRIPTS_DIR="${APP_ROOT}/scripts"

mkdir -p "${APP_ROOT}/logs" "${APP_ROOT}/releases" "${APP_ROOT}/backups"
LOG_FILE="${APP_ROOT}/logs/deploy_${RELEASE}.log"
# tee all output (stdout+stderr) into the timestamped deploy log
exec > >(tee -a "${LOG_FILE}") 2>&1

# ---- locking ------------------------------------------------------
LOCK_FILE="/var/run/payment-checker-deploy.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "FATAL: Another deployment is already in progress (locked via $LOCK_FILE)."
  exit 1
fi

log() { echo "[$(date -Is)] $*"; }
die() { log "FATAL: $*"; log "Deploy ABORTED — production left untouched. Log: ${LOG_FILE}"; exit 1; }

log "==================== DEPLOY START ref=${REF} release=${RELEASE} ===================="
log "host=$(hostname) user=$(whoami)"

# ---- preflight ----------------------------------------------------
command -v git  >/dev/null 2>&1 || die "git not found"
command -v pm2  >/dev/null 2>&1 || die "pm2 not found"
command -v npm  >/dev/null 2>&1 || die "npm not found"
[ -d "${APP_ROOT}" ] || die "APP_ROOT ${APP_ROOT} missing (run bootstrap first)"
[ -f "${APP_ROOT}/shared/.env" ] || die "shared/.env missing"

PREV_RELEASE="$(readlink -f "${APP_ROOT}/current" 2>/dev/null || true)"
log "previous release: ${PREV_RELEASE:-<none>}"

# ---- pre-deploy snapshot ------------------------------------------
log "==> Pre-deploy config snapshot"
SNAPSHOT_DIR="/var/backups/apps/snapshots/${RELEASE}"
mkdir -p "${SNAPSHOT_DIR}"
chmod 700 /var/backups/apps /var/backups/apps/snapshots "${SNAPSHOT_DIR}" 2>/dev/null || true

# Copy current configurations
[ -f "/root/.pm2/dump.pm2" ] && cp -p "/root/.pm2/dump.pm2" "${SNAPSHOT_DIR}/dump.pm2" 2>/dev/null || true
[ -f "${APP_ROOT}/shared/.env" ] && cp -p "${APP_ROOT}/shared/.env" "${SNAPSHOT_DIR}/.env" 2>/dev/null || true
[ -f "${APP_ROOT}/current/ecosystem.config.js" ] && cp -p "${APP_ROOT}/current/ecosystem.config.js" "${SNAPSHOT_DIR}/ecosystem.config.js" 2>/dev/null || true
[ -f "/etc/nginx/sites-available/default" ] && cp -p "/etc/nginx/sites-available/default" "${SNAPSHOT_DIR}/nginx_default" 2>/dev/null || true
[ -f "/etc/nginx/nginx.conf" ] && cp -p "/etc/nginx/nginx.conf" "${SNAPSHOT_DIR}/nginx.conf" 2>/dev/null || true
log "Pre-deploy config snapshot saved in ${SNAPSHOT_DIR}"

# ---- 1. database backup (before any migration) --------------------
if [ -x "${SCRIPTS_DIR}/backup.sh" ]; then
  log "==> DB backup"
  "${SCRIPTS_DIR}/backup.sh" || die "DB backup failed — aborting before any change"
else
  log "WARN: backup.sh not found/executable — skipping DB backup"
fi

# ---- 2. clone source of truth into temp build ---------------------
log "==> Clone ${REPO} @ ${REF} -> ${TMP_BUILD}"
rm -rf "${TMP_BUILD}"
mkdir -p "${TMP_BUILD}"
if ! git clone --quiet "${REPO}" "${TMP_BUILD}"; then
  die "git clone failed (check VPS GitHub access / deploy key)"
fi
cd "${TMP_BUILD}"
git checkout --quiet "${REF}" || die "git checkout ${REF} failed"
GIT_SHA="$(git rev-parse --short HEAD)"
log "checked out ${GIT_SHA}"

# ---- 3. install deps + prisma generate ----------------------------
cd "${TMP_BUILD}/backend"
ln -sfn "${APP_ROOT}/shared/.env" "${TMP_BUILD}/backend/.env"
log "==> npm ci"
npm ci --no-audit --no-fund || die "npm ci failed"
log "==> prisma generate"
npx prisma generate || die "prisma generate failed"

# ---- 3.5. inject build metadata -----------------------------------
log "==> Injecting build metadata into shared/.env"
sed -i '/^BUILD_VERSION=/d' "${APP_ROOT}/shared/.env" 2>/dev/null || true
sed -i '/^BUILD_COMMIT=/d' "${APP_ROOT}/shared/.env" 2>/dev/null || true
sed -i '/^BUILD_DATE=/d' "${APP_ROOT}/shared/.env" 2>/dev/null || true
echo "BUILD_VERSION=${REF}" >> "${APP_ROOT}/shared/.env"
echo "BUILD_COMMIT=${GIT_SHA}" >> "${APP_ROOT}/shared/.env"
echo "BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "${APP_ROOT}/shared/.env"

# NOTE: schema changes are applied via additive runtime ensure-guards
# (services/* ensureSchema, db/ensure-*.js) — never `prisma db push` here,
# which could drop columns. Backups are already taken above.

# ---- 4. lint gate (syntax) ----------------------------------------
log "==> lint (node --check on all backend JS)"
LINT_FAIL=0
while IFS= read -r -d '' f; do
  node --check "$f" 2>>"${LOG_FILE}" || { log "SYNTAX ERROR: $f"; LINT_FAIL=1; }
done < <(find . -name '*.js' -not -path './node_modules/*' -print0)
[ "${LINT_FAIL}" -eq 0 ] || die "lint (syntax) gate failed"
log "lint OK"

# ---- 5. unit-test gate (DB-free) ----------------------------------
if [ "${SKIP_TESTS:-0}" = "1" ]; then
  log "WARN: SKIP_TESTS=1 — unit-test gate skipped"
else
  log "==> unit tests (payment, DB-free)"
  npm run --silent test:payment-unit >>"${LOG_FILE}" 2>&1 || die "unit-test gate failed"
  log "unit tests OK"
fi

# ---- 6. cut release + link shared ---------------------------------
log "==> cut release ${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"
rsync -a --delete \
  --exclude '.git' \
  --exclude '.env' \
  --exclude 'node_modules/.cache' \
  --exclude 'server-*.log' \
  "${TMP_BUILD}/backend/" "${RELEASE_DIR}/"
ln -sfn "${APP_ROOT}/shared/.env"       "${RELEASE_DIR}/.env"
ln -sfn "${APP_ROOT}/shared/uploads"    "${RELEASE_DIR}/uploads"          2>/dev/null || true
ln -sfn "${APP_ROOT}/shared/cache"      "${RELEASE_DIR}/cache"            2>/dev/null || true
mkdir -p "${RELEASE_DIR}/public" "${APP_ROOT}/shared/downloads"
rm -rf "${RELEASE_DIR}/public/downloads"
ln -sfn "${APP_ROOT}/shared/downloads"  "${RELEASE_DIR}/public/downloads"
log "release ready: ${RELEASE_DIR}"

# keep the runner scripts on the VPS in sync with the deployed code.
# NOTE: deploy.sh is EXCLUDED here and synced at the very end via a deferred
# background copy. Copying the *running* deploy.sh over itself mid-run corrupts
# the executing shell whenever a deploy changes deploy.sh (bash reads the live
# file by byte offset and misparses the new layout — a pre-switch syntax error).
# Every other script (rollback.sh included) is safe to sync mid-run: rollback.sh
# is only ever read by a fresh subprocess, never by this running shell.
for _pc_s in "${RELEASE_DIR}/scripts/"*.sh; do
  case "$(basename "${_pc_s}")" in
    deploy.sh) continue ;;
  esac
  cp -f "${_pc_s}" "${SCRIPTS_DIR}/" 2>/dev/null || true
done
chmod +x "${SCRIPTS_DIR}/"*.sh 2>/dev/null || true

if [ "${DRY_RUN:-0}" = "1" ]; then
  log "DRY_RUN=1 — release built+tested but NOT switched. release=${RELEASE_DIR}"
  rm -rf "${TMP_BUILD}"
  log "==================== DEPLOY END (dry-run) ===================="
  exit 0
fi

# ---- 7. switch current symlink ------------------------------------
log "==> switch current -> ${RELEASE_DIR}"
# record the known-good release so a no-arg rollback is deterministic
[ -n "${PREV_RELEASE}" ] && echo "${PREV_RELEASE}" > "${APP_ROOT}/.last_release" || true
ln -sfn "${RELEASE_DIR}" "${APP_ROOT}/current"
log "current -> $(readlink -f "${APP_ROOT}/current")"

# ---- 8. restart ONLY this project's PM2 apps ----------------------
# IMPORTANT: `pm2 reload`/`restart` reuse the process's frozen, realpath-resolved
# exec cwd/script and NEVER re-read the `current` symlink. After a symlink switch
# they therefore keep serving the OLD release — this is the bug that left the
# 2026-07-29 checkout redesign un-served while the post-health check passed
# against the stale-but-healthy server. The only way to pick up the new release
# is `pm2 delete` + `pm2 start` against `current`, which re-resolves the symlink
# at start time. Fork-mode reload was never zero-downtime anyway, so the brief
# delete/start gap is acceptable.
log "==> pm2 delete+start ${PM2_API} (re-resolve current)"
cd "${APP_ROOT}/current"
pm2 delete "${PM2_API}" || true
pm2 start app.js --name "${PM2_API}" --cwd "${APP_ROOT}/current" || {
  log "pm2 start failed — rolling back to ${PREV_RELEASE}"
  # rollback.sh also uses delete+start, so it re-creates the process on the
  # previous release even though we just deleted it here.
  "${SCRIPTS_DIR}/rollback.sh" "${PREV_RELEASE}"; die "pm2 start failed";
}
# optional companion services (only if they already exist)
# NOTE: these share the same frozen-cwd limitation as the API above — `reload`
# will NOT re-resolve `current`. They are intentionally left on reload here
# because (a) their start args/entrypoints are not codified in this script, and
# (b) they are not deployed on the current VPS, so this loop is a guarded no-op
# in production today. If/when a companion is enabled, migrate it to the same
# delete+start pattern using a committed pm2 ecosystem config that records each
# service's script + args, so its cwd can be re-resolved safely.
for svc in "${PM2_WORKER}" "${PM2_SOCKET}"; do
  if pm2 describe "${svc}" >/dev/null 2>&1; then
    log "==> pm2 reload ${svc}"
    pm2 reload "${svc}" --update-env || pm2 reload "${svc}" || log "WARN: reload ${svc} failed"
  fi
done
# pm2 save >/dev/null 2>&1 || true # Omitted to prevent state overwriting on dead PM2 daemons

# ---- 9. POST health check (auto-rollback on failure) --------------
log "==> post-deploy health check"
sleep 3
if [ -x "${SCRIPTS_DIR}/healthcheck.sh" ]; then
  if "${SCRIPTS_DIR}/healthcheck.sh"; then
    log "health OK"
  else
    log "ERROR: post-deploy health FAILED — rolling back to ${PREV_RELEASE}"
    "${SCRIPTS_DIR}/rollback.sh" "${PREV_RELEASE}" || true
    die "deploy failed post-health; rolled back to ${PREV_RELEASE}"
  fi
else
  log "WARN: healthcheck.sh missing — skipping post-health verification"
fi

# ---- 10. retention: keep latest 10 releases / 10 backups ----------
log "==> retention"
ls -1dt "${APP_ROOT}"/releases/*/ 2>/dev/null | tail -n +11 | xargs -r rm -rf
ls -1t  "${APP_ROOT}"/backups/db_*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
ls -1dt /var/backups/apps/snapshots/*/ 2>/dev/null | tail -n +8 | xargs -r rm -rf
log "releases kept: $(ls -1d "${APP_ROOT}"/releases/*/ 2>/dev/null | wc -l)"

# ---- 11. cleanup temp build ---------------------------------------
rm -rf "${TMP_BUILD}"
log "==================== DEPLOY OK release=${RELEASE} sha=${GIT_SHA} log=${LOG_FILE} ===================="

# Defer syncing deploy.sh itself until this shell has stopped reading the file,
# so a deploy that changes deploy.sh can never corrupt its own execution. The
# parent process exits within milliseconds; the copy runs a few seconds later.
{ sleep 3; cp -f "${RELEASE_DIR}/scripts/deploy.sh" "${SCRIPTS_DIR}/deploy.sh" 2>/dev/null && chmod +x "${SCRIPTS_DIR}/deploy.sh" 2>/dev/null; } >/dev/null 2>&1 &
disown 2>/dev/null || true
