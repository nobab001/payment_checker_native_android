# VPS Deployment

## Host

- Domain: **paycheckbd.com**  
- App releases: `/var/www/payment-checker/` (release folders + `shared/`)  
- Shared env: `/var/www/payment-checker/shared/.env`  
- Shared APK: `/var/www/payment-checker/shared/downloads/paycheck.apk`  
- Process: PM2 process name typically `payment-checker-api`  
- Reverse proxy: Nginx → Node (TLS certificates on Nginx)

## Mental model

```
GitHub / local artifact
        ↓
  release directory (code)
        ↓
  symlink current → release
        ↓
  shared/.env + shared/uploads + shared/downloads
        ↓
  PM2 reload
        ↓
  Nginx serves HTTPS + static
```

## What local agent builds do **not** do

- Building `app-debug.apk` on a developer PC does **not** update the website Download button.
- Restarting local Node does **not** restart VPS PM2.

## SSH

Use the team’s configured key (historically documented for Contabo VPS access). Never paste private keys into the repo.

## CloudPanel / Contabo

Contabo panel ≠ always CloudPanel. Existing site may already be configured — do not “Add Site” blindly for paycheckbd.com if Nginx already routes it. CloudPanel UI often on port `8443` when installed.

## Ops checklist (manual)

1. Backup DB + `.env`  
2. Deploy code to new release  
3. `npm install --production` if needed  
4. Run Prisma/ensure scripts if schema changed  
5. Symlink switch  
6. `pm2 reload payment-checker-api`  
7. Health check `https://paycheckbd.com/` and a known API route  
8. Watch PM2 logs for errors  

## APK publish

```bash
# on VPS (example)
cp paycheck.apk /var/www/payment-checker/shared/downloads/paycheck.apk
chmod 644 /var/www/payment-checker/shared/downloads/paycheck.apk
```

Public URL: `https://paycheckbd.com/downloads/paycheck.apk`
