#!/usr/bin/env bash
# Bootstrap Payment Checker production layout on VPS (idempotent-ish).
# Safe: does not restart unrelated PM2 apps.
set -euo pipefail

DOMAIN_ROOT="paycheckbd.com"
DOMAIN_WWW="www.paycheckbd.com"
DOMAIN_ALIAS="paychek.online"
APP_ROOT="/var/www/payment-checker"
TMP_BUILD="/tmp/deploy/payment-checker"
REPO="https://github.com/nobab001/payment_checker_native_android.git"
RELEASE="$(date +%Y-%m-%d_%H%M%S)"
RELEASE_DIR="${APP_ROOT}/releases/${RELEASE}"
NODE_PORT="3000"
PM2_NAME="payment-checker-api"

echo "==> Ensuring directory layout"
mkdir -p \
  "${APP_ROOT}/releases" \
  "${APP_ROOT}/shared/uploads" \
  "${APP_ROOT}/shared/logs" \
  "${APP_ROOT}/shared/downloads" \
  "${APP_ROOT}/shared/cache" \
  "${APP_ROOT}/scripts" \
  "${APP_ROOT}/logs" \
  "${APP_ROOT}/backups" \
  /tmp/deploy

echo "==> Ensuring MySQL database + app user"
export MYSQL_PWD
MYSQL_PWD="$(clpctl db:show:master-credentials 2>/dev/null | awk -F'|' '/Password/{gsub(/ /,"",$3); print $3; exit}')"
DB_NAME="paychek_online_v2"
DB_USER="paychek_app"
DB_PASS="$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-28)"

mysql -h127.0.0.1 -P3306 -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
unset MYSQL_PWD
echo "DB ready: ${DB_NAME} / ${DB_USER}"

echo "==> Writing shared .env (if missing)"
ENV_FILE="${APP_ROOT}/shared/.env"
if [ ! -f "${ENV_FILE}" ]; then
  JWT="$(openssl rand -hex 32)"
  cat > "${ENV_FILE}" <<EOF
NODE_ENV=production
PORT=${NODE_PORT}
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=${DB_USER}
DB_PASS=${DB_PASS}
DB_NAME=${DB_NAME}
DATABASE_URL="mysql://${DB_USER}:${DB_PASS}@127.0.0.1:3306/${DB_NAME}"
JWT_SECRET=${JWT}
PUBLIC_BASE_URL=https://${DOMAIN_ROOT}
CORS_ORIGIN=https://${DOMAIN_ROOT},https://${DOMAIN_WWW},https://${DOMAIN_ALIAS}
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
TRIAL_DEFAULT_DAYS=7
EOF
  chmod 600 "${ENV_FILE}"
  echo "Created ${ENV_FILE}"
else
  echo "Keeping existing ${ENV_FILE}"
  # shellcheck disable=SC1090
  set -a; source "${ENV_FILE}"; set +a
fi

echo "==> Clone into temp build"
rm -rf "${TMP_BUILD}"
mkdir -p "${TMP_BUILD}"
git clone --depth 1 --branch main "${REPO}" "${TMP_BUILD}"

echo "==> Install backend deps + Prisma"
cd "${TMP_BUILD}/backend"
# Use shared env for prisma
ln -sfn "${ENV_FILE}" "${TMP_BUILD}/backend/.env"
npm ci
npx prisma generate
npx prisma db push

echo "==> Promote release"
mkdir -p "${RELEASE_DIR}"
rsync -a --delete \
  --exclude 'node_modules/.cache' \
  --exclude '.git' \
  "${TMP_BUILD}/backend/" "${RELEASE_DIR}/"
ln -sfn "${ENV_FILE}" "${RELEASE_DIR}/.env"
mkdir -p "${APP_ROOT}/shared/downloads"
rm -rf "${RELEASE_DIR}/public/downloads"
ln -sfn "${APP_ROOT}/shared/downloads" "${RELEASE_DIR}/public/downloads"
ln -sfn "${APP_ROOT}/shared/uploads" "${RELEASE_DIR}/uploads" 2>/dev/null || true

ln -sfn "${RELEASE_DIR}" "${APP_ROOT}/current"
echo "current -> ${RELEASE_DIR}"

echo "==> PM2: stop dead paychek-api if present; start/reload ${PM2_NAME} only"
pm2 delete paychek-api >/dev/null 2>&1 || true
cd "${APP_ROOT}/current"
if pm2 describe "${PM2_NAME}" >/dev/null 2>&1; then
  pm2 reload "${PM2_NAME}" --update-env
else
  pm2 start app.js --name "${PM2_NAME}" --cwd "${APP_ROOT}/current"
fi
pm2 save

echo "==> Nginx site ${DOMAIN_ROOT}"
cat > /etc/nginx/sites-available/paycheckbd.com.conf <<'NGINX'
server {
    listen 80;
    listen [::]:80;
    server_name paycheckbd.com www.paycheckbd.com paychek.online;

    client_max_body_size 50m;

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 120s;
    }
}
NGINX
ln -sfn /etc/nginx/sites-available/paycheckbd.com.conf /etc/nginx/sites-enabled/paycheckbd.com.conf
nginx -t
systemctl reload nginx

echo "==> TLS certificates"
certbot --nginx -d paycheckbd.com -d www.paycheckbd.com -d paychek.online \
  --non-interactive --agree-tos --register-unsafely-without-email --redirect || \
certbot --nginx -d paycheckbd.com -d www.paycheckbd.com \
  --non-interactive --agree-tos --register-unsafely-without-email --redirect

echo "==> Health checks"
sleep 2
curl -fsS "http://127.0.0.1:${NODE_PORT}/" -o /dev/null && echo "local root OK" || echo "local root FAIL"
curl -fsS "https://${DOMAIN_ROOT}/" -o /dev/null && echo "https root OK" || echo "https root FAIL"
curl -fsS "https://${DOMAIN_ROOT}/api/official/site" | head -c 200 || true
echo
pm2 describe "${PM2_NAME}" | head -25

echo "==> Cleanup temp build"
rm -rf "${TMP_BUILD}"
echo "DONE release=${RELEASE}"
