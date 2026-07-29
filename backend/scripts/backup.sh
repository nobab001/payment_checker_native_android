#!/usr/bin/env bash
#
# PayChek — pre-deploy/pre-migration MySQL backup. Keeps the latest 10 dumps.
# Credentials are read from shared/.env via get_env() WITHOUT being exported,
# so they do not leak into child-process environments. Each dump is verified
# with gzip -t before being kept.
#
set -euo pipefail

APP_ROOT="/var/www/payment-checker"
ENV_FILE="${APP_ROOT}/shared/.env"
[ -f "${ENV_FILE}" ] || { echo "FATAL: ${ENV_FILE} missing"; exit 1; }

get_env() { grep -E "^$1=" "${ENV_FILE}" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '"'; }
DB_HOST="$(get_env DB_HOST)"; DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="$(get_env DB_PORT)"; DB_PORT="${DB_PORT:-3306}"
DB_USER="$(get_env DB_USER)"
DB_PASS="$(get_env DB_PASS)"
DB_NAME="$(get_env DB_NAME)"
[ -n "${DB_USER}" ] && [ -n "${DB_NAME}" ] || { echo "FATAL: DB_USER/DB_NAME missing in ${ENV_FILE}"; exit 1; }

mkdir -p "${APP_ROOT}/backups" "${APP_ROOT}/logs"
TS="$(date +%Y-%m-%d_%H%M%S)"
OUT="${APP_ROOT}/backups/db_${TS}.sql.gz"
LOG_FILE="${APP_ROOT}/logs/backup_${TS}.log"

echo "[$(date -Is)] backing up DB '${DB_NAME}' -> ${OUT}" | tee -a "${LOG_FILE}"
mysqldump \
  -h"${DB_HOST}" -P"${DB_PORT}" \
  -u"${DB_USER}" -p"${DB_PASS}" \
  --single-transaction --routines --triggers \
  "${DB_NAME}" 2>>"${LOG_FILE}" | gzip > "${OUT}"

# verify the dump is non-empty and the gzip is intact
if [ ! -s "${OUT}" ] || ! gzip -t "${OUT}" 2>>"${LOG_FILE}"; then
  echo "[$(date -Is)] FATAL: backup empty or corrupt" | tee -a "${LOG_FILE}"
  rm -f "${OUT}"
  exit 1
fi
echo "[$(date -Is)] backup OK ($(du -h "${OUT}" | cut -f1))" | tee -a "${LOG_FILE}"

# retention: keep latest 10 DB backups
ls -1t "${APP_ROOT}"/backups/db_*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
echo "[$(date -Is)] backups kept: $(ls -1 "${APP_ROOT}"/backups/db_*.sql.gz 2>/dev/null | wc -l)" | tee -a "${LOG_FILE}"
