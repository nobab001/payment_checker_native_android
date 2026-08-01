#!/usr/bin/env bash
#
# Admin-controlled PM2 Save with process and release validation.
# Checks that all required apps are online and run from expected directories before saving PM2 state.
#
set -euo pipefail

EXPECTED_APPS=("payment-checker-api" "telecom-bot" "telecom-dashboard" "smartcalc-api")
BACKUP_DIR="/var/backups/apps"
PM2_HOME="${PM2_HOME:-$HOME/.pm2}"

echo "==================== PM2 SAFE SAVE ===================="
echo "Timestamp: $(date -Is)"

# Load PM2 list JSON
PM2_JSON=$(pm2 jlist 2>/dev/null || echo "[]")

if [ "$PM2_JSON" = "[]" ] || [ -z "$PM2_JSON" ]; then
  echo "[FATAL] PM2 is not running or failed to list processes."
  exit 1
fi

ALL_VALID=true

for app in "${EXPECTED_APPS[@]}"; do
  # Parse status and execution path using node
  STATUS_INFO=$(echo "$PM2_JSON" | APP_NAME="$app" node -e '
    let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{
      try {
        const list = JSON.parse(d);
        const p = list.find(x => x.name === process.env.APP_NAME);
        if (!p) { console.log("ABSENT none"); return; }
        const status = p.pm2_env?.status || "offline";
        const path = p.pm2_env?.pm_exec_path || "unknown";
        console.log(status + " " + path);
      } catch (e) { console.log("ERROR none"); }
    });')

  read -r STATUS EXEC_PATH <<< "$STATUS_INFO"

  if [ "$STATUS" != "online" ]; then
    echo "[FAIL] App '$app' status is '${STATUS}' (expected 'online')."
    ALL_VALID=false
    continue
  fi

  # Release Path Validation
  # Ensure the process is running from the active release or expected directories
  case "$app" in
    payment-checker-api)
      if [[ "$EXEC_PATH" != *"/payment-checker/current/"* ]]; then
        echo "[WARN] App '$app' is running from non-standard path: $EXEC_PATH"
      fi
      ;;
    telecom-*)
      if [[ "$EXEC_PATH" != *"/telecom-bot/bot/"* ]]; then
        echo "[WARN] App '$app' is running from non-standard path: $EXEC_PATH"
      fi
      ;;
    smartcalc-api)
      if [[ "$EXEC_PATH" != *"/smartcalculator/backend/"* ]]; then
        echo "[WARN] App '$app' is running from non-standard path: $EXEC_PATH"
      fi
      ;;
  esac

  echo "[PASS] App '$app' is online (Exec: $EXEC_PATH)"
done

if [ "$ALL_VALID" = "false" ]; then
  echo "[FATAL] Validation failed. Not saving PM2 configuration."
  exit 1
fi

# Secure Backup of dump.pm2
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

if [ -f "$PM2_HOME/dump.pm2" ]; then
  BACKUP_FILE="${BACKUP_DIR}/dump_$(date +%Y-%m-%d_%H%M%S).pm2"
  cp "$PM2_HOME/dump.pm2" "$BACKUP_FILE"
  chmod 600 "$BACKUP_FILE"
  echo "[INFO] Successfully backed up current dump.pm2 to $BACKUP_FILE"
fi

# Run PM2 Save
echo "[INFO] Saving PM2 state..."
pm2 save

echo "==================== SAVE COMPLETED ===================="
