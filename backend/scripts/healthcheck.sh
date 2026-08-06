#!/usr/bin/env bash
#
# PayChek — health check (post-deploy or on-demand).
# Verifies: PM2 online + no crash-loop, companion services, HTTPS site, local
# API, an API route, Redis, MySQL, no recent fatal errors, no migration errors.
# Exit 0 = all healthy, 1 = at least one check failed.
#
# Secrets are read from shared/.env via get_env() WITHOUT being exported, so
# child processes do not inherit DB/Redis credentials in their environment.
#
set -uo pipefail   # deliberately NOT -e: run every check, then summarize

APP_ROOT="/var/www/payment-checker"
PM2_API="payment-checker-api"
PM2_WORKER="payment-checker-worker"
DOMAIN="paycheckbd.com"
ENV_FILE="${APP_ROOT}/shared/.env"
PM2_LOG_DIR="${PM2_HOME:-${HOME}/.pm2}/logs"
ERR_LOG="${PM2_LOG_DIR}/${PM2_API}-error.log"
OUT_LOG="${PM2_LOG_DIR}/${PM2_API}-out.log"

# read one key from the env file without exporting anything
get_env() { grep -E "^$1=" "${ENV_FILE}" 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '"'; }
DB_HOST="$(get_env DB_HOST)";   DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="$(get_env DB_PORT)";   DB_PORT="${DB_PORT:-3306}"
DB_USER="$(get_env DB_USER)"
DB_PASS="$(get_env DB_PASS)"
DB_NAME="$(get_env DB_NAME)"
REDIS_HOST="$(get_env REDIS_HOST)"; REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="$(get_env REDIS_PORT)"; REDIS_PORT="${REDIS_PORT:-6379}"
PORT="$(get_env PORT)";         PORT="${PORT:-3000}"

FAILS=0
report() { # name ok(0/1) detail
  if [ "$2" -eq 0 ]; then printf '  [PASS] %-20s %s\n' "$1" "$3";
  else printf '  [FAIL] %-20s %s\n' "$1" "$3"; FAILS=$((FAILS+1)); fi
}

echo "==================== HEALTH CHECK $(date -Is) ===================="
echo "current -> $(readlink -f "${APP_ROOT}/current" 2>/dev/null || echo '<none>')"

# --- PM2 status + crash-loop detection (via reliable JSON) ---------
pm2_field() { # name -> "status restarts"
  pm2 jlist 2>/dev/null | PM2_N="$1" node -e '
    let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
      let a=[];const i=d.indexOf("[");if(i>=0)d=d.slice(i);
      try{a=JSON.parse(d)}catch(e){a=[]}
      const p=a.find(x=>x.name===process.env.PM2_N);
      if(!p){console.log("ABSENT 0");return;}
      const st=(p.pm2_env&&p.pm2_env.status)||"unknown";
      const rs=(p.pm2_env&&p.pm2_env.restart_time)||0;
      console.log(st+" "+rs);
    });'
}
read -r P_STATUS R1 <<<"$(pm2_field "${PM2_API}")"
sleep 6
read -r _ R2 <<<"$(pm2_field "${PM2_API}")"
R1="${R1:-0}"; R2="${R2:-0}"; DELTA=$((R2 - R1))
[ "${P_STATUS}" = "online" ] && report "pm2:online" 0 "status=${P_STATUS}" || report "pm2:online" 1 "status=${P_STATUS:-unknown}"
[ "${DELTA}" -lt 3 ] && report "pm2:no-crashloop" 0 "restarts ${R1}->${R2} (delta ${DELTA})" || report "pm2:no-crashloop" 1 "restarts climbing ${R1}->${R2}"

# --- companion services (only if they exist) -----------------------
for svc in "${PM2_WORKER}"; do
  read -r S_STATUS _ <<<"$(pm2_field "${svc}")"
  if [ "${S_STATUS}" != "ABSENT" ]; then
    [ "${S_STATUS}" = "online" ] && report "pm2:${svc}" 0 "online" || report "pm2:${svc}" 1 "status=${S_STATUS}"
  fi
done

# --- HTTP / API ----------------------------------------------------
code_site="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://${DOMAIN}/" || echo 000)"
[ "${code_site}" = "200" ] && report "https:site" 0 "GET / = ${code_site}" || report "https:site" 1 "GET / = ${code_site}"

code_local="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:${PORT}/" || echo 000)"
[ "${code_local}" = "200" ] && report "http:local" 0 "GET :${PORT}/ = ${code_local}" || report "http:local" 1 "GET :${PORT}/ = ${code_local}"

code_api="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://${DOMAIN}/api/official/site" || echo 000)"
[ "${code_api}" = "200" ] && report "api:route" 0 "/api/official/site = ${code_api}" || report "api:route" 1 "/api/official/site = ${code_api}"

# --- Redis ---------------------------------------------------------
RP="$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ping 2>/dev/null || echo NO)"
[ "${RP}" = "PONG" ] && report "redis:ping" 0 "PONG" || report "redis:ping" 1 "${RP}"

# --- MySQL ---------------------------------------------------------
DBOK="$(mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" -N -e 'SELECT 1' 2>/dev/null || echo 0)"
[ "${DBOK}" = "1" ] && report "db:select1" 0 "connected" || report "db:select1" 1 "query failed"

# --- recent fatal errors in PM2 error log --------------------------
if [ -f "${ERR_LOG}" ]; then
  RECENT_FATALS="$(tail -40 "${ERR_LOG}" | grep -ciE 'Cannot find module|ECONNREFUSED|uncaughtException|UnhandledPromiseRejection|SyntaxError' || true)"
  [ "${RECENT_FATALS:-0}" -eq 0 ] && report "logs:no-fatal" 0 "clean (last 40 lines)" || report "logs:no-fatal" 1 "${RECENT_FATALS} fatal marker(s) recently"
else
  report "logs:no-fatal" 0 "no error log present"
fi

# --- migration / schema errors at boot -----------------------------
if [ -f "${OUT_LOG}" ]; then
  MIG_ERR="$(tail -120 "${OUT_LOG}" | grep -ciE 'PrismaClient[A-Za-z]*Error|ER_DUP_FIELDNAME|ER_DB_CREATE_ERROR|Migration (failed|halted)|[1-9][0-9]* failed' || true)"
  [ "${MIG_ERR:-0}" -eq 0 ] && report "logs:no-mig-err" 0 "no migration/schema errors" || report "logs:no-mig-err" 1 "${MIG_ERR} migration/schema error(s)"
else
  report "logs:no-mig-err" 0 "no out log present"
fi

echo "---------------------------------------------------"
if [ "${FAILS}" -eq 0 ]; then
  echo "HEALTH: ALL PASS"
  exit 0
else
  echo "HEALTH: ${FAILS} CHECK(S) FAILED"
  exit 1
fi
