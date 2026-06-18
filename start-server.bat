@echo off
title Paychek Backend Server
echo ===================================================
echo   Starting Paychek Backend Server...
echo ===================================================
cd /d "%~dp0backend"
npm run dev
pause
www