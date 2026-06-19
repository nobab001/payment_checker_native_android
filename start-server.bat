@echo off
title Paychek Backend Server
echo ===================================================
echo   Starting Paychek Backend Server...
echo ===================================================
cd /d "%~dp0backend"

:: Make sure dependencies (including nodemon) are installed
if not exist "node_modules\nodemon" (
  echo Installing backend dependencies...
  call npm install
)

:: Run the dev server (nodemon will auto-reload on file changes)
call npm run dev
pause