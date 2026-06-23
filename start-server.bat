@echo off
title Paychek Backend Server
echo ===================================================
echo   Starting Paychek Backend Server and Redis...
echo ===================================================

:: Start Redis Server in a new window
echo Launching Redis Server...
if exist "C:\Redis\redis-server.exe" (
    start "Paychek Redis Server" cmd /k "C:\Redis\redis-server.exe"
) else (
    echo [WARNING] Redis not found at C:\Redis\redis-server.exe
)

cd /d "%~dp0backend"

:: Make sure dependencies (including nodemon) are installed
if not exist "node_modules\nodemon" (
  echo Installing backend dependencies...
  call npm install
)

:: Run the dev server (nodemon will auto-reload on file changes)
call npm run dev
pause