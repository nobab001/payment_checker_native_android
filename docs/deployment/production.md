# Production

## Definition of done for a production release

1. Code reviewed / approved by product owner  
2. Staging or thorough local verification complete  
3. Backups taken (MySQL + critical shared files)  
4. Deploy backend (if needed) → PM2 reload → health check  
5. Publish APK to shared downloads (if needed)  
6. Spot-check: login, checkout init, SMS ingest, merchant callback  
7. Monitor logs for 15–30 minutes  

## Health checks

- `GET https://paycheckbd.com/` returns official site  
- `GET https://paycheckbd.com/downloads/paycheck.apk` returns APK when published  
- Auth public config endpoint responds  
- PM2 status `online`  
- Nginx TLS valid  

## Rollback

1. Point `current` symlink to previous release  
2. `pm2 reload`  
3. Restore DB only if migration is incompatible (prefer forward fixes)  
4. Replace APK with previous artifact if client breakage  

## Monitoring

- PM2 logs (`~/.pm2/logs` or project log files)  
- Nginx access/error logs  
- Application audit logs table  
- Disk use on `uploads/` and MySQL  

## Backup

- Nightly MySQL dump to off-box storage when configured  
- Retain shared `.env` securely (password manager / sealed storage)  
- APK artifacts archived per release  

## Emergency

Payment mismatch incidents: pause affected website/gateway if controls exist, preserve SMS history rows, avoid deleting evidence, patch matchers carefully.
