@echo off
REM Backup current paychek_online_v2 to a timestamped SQL dump
for /f "tokens=2 delims==" %%a in ('"wmic os get localdatetime /value"') do set datetime=%%a
set timestamp=%datetime:~0,8%_%datetime:~8,6%
set out=D:\payment_checker_native_android\backend\backups\paychek_online_v2_%timestamp%.sql
"C:\xampp\mysql\bin\mysqldump.exe" -u root --single-transaction --routines --triggers paychek_online_v2 > "%out%"
echo Backup saved to: %out%
pause
