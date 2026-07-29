#!/usr/bin/env bash
#
# PayChek — rollback. NEVER uses git checkout. Only switches the `current`
# symlink to a previous release and reloads this project's PM2 app.
#
# Usage: rollback.sh [release-dir]     (default: the previous release)
#
set -euo pipefail

APP_ROOT="/var/www/payment-checker"
PM2_API="payment-checker-api"
mkdir -p "${APP_ROOT}/logs"
LOG_FILE="${APP_ROOT}/logs/rollback_$(date +%Y-%m-%d_%H%M%S).log"
exec > >(tee -a "${LOG_FILE}") 2>&1

log() { echo "[$(date -Is)] $*"; }

TARGET="${1:-}"
if [ -z "${TARGET}" ]; then
  # prefer the known-good pointer written by deploy.sh; else 2nd-newest release
  if [ -f "${APP_ROOT}/.last_release" ]; then
    TARGET="$(tr -d '[:space:]' < "${APP_ROOT}/.last_release" 2>/dev/null || true)"
  fi
  if [ -z "${TARGET}" ] || [ ! -d "${TARGET}" ]; then
    TARGET="$(ls -1dt "${APP_ROOT}"/releases/*/ 2>/dev/null | sed -n '2p')"
    TARGET="${TARGET%/}"
  fi
fi
[ -n "${TARGET}" ] && [ -d "${TARGET}" ] || { log "FATAL: no such release dir: '${TARGET}'"; exit 1; }

PREV="$(readlink -f "${APP_ROOT}/current" 2>/dev/null || true)"
log "==================== ROLLBACK START ===================="
log "from: ${PREV:-<none>}"
log "to:   ${TARGET}"

ln -sfn "${TARGET}" "${APP_ROOT}/current"
log "current -> $(readlink -f "${APP_ROOT}/current")"

pm2 reload "${PM2_API}" --update-env || pm2 reload "${PM2_API}" || { log "FATAL: pm2 reload failed"; exit 1; }
pm2 save >/dev/null 2>&1 || true

sleep 3
if [ -x "${APP_ROOT}/scripts/healthcheck.sh" ]; then
  "${APP_ROOT}/scripts/healthcheck.sh" && log "health OK after rollback" || log "WARN: health still failing after rollback — investigate"
fi
log "==================== ROLLBACK DONE -> ${TARGET} log=${LOG_FILE} ===================="
