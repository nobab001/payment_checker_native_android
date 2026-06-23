@echo off
"C:\xampp\mysql\bin\mysql.exe" --execute="DROP DATABASE IF EXISTS paychek_online_v2; DROP DATABASE IF EXISTS payment_checker; DROP DATABASE IF EXISTS payment_checker_db; CREATE DATABASE paychek_online_v2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
echo === exit code: %ERRORLEVEL% ===