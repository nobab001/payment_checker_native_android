@echo off
rem Start mysqld (XAMPP) in background
"C:\xampp\mysql\bin\mysqld.exe" --defaults-file="C:\xampp\mysql\bin\my.ini"
echo === mysqld exited ===