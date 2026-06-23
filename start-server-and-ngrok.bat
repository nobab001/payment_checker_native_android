@echo off
title Paychek Services Starter
echo ===================================================
echo   Starting Redis, Backend Server, and Ngrok...
echo ===================================================

:: Start Redis Server in a new window
echo Launching Redis Server...
if exist "C:\Redis\redis-server.exe" (
    start "Paychek Redis Server" cmd /k "C:\Redis\redis-server.exe"
) else (
    echo [WARNING] Redis not found at C:\Redis\redis-server.exe
)

:: Start Backend Server in a new window
echo Launching Backend Server...
start "Paychek Backend Server" cmd /k "cd /d ""%~dp0backend"" && npm run dev"

:: Start Ngrok Tunnel in another window
echo Launching Ngrok Tunnel...
start "Paychek Ngrok Tunnel" cmd /k "ngrok http 3000"

echo ===================================================
echo   All services have been launched in separate windows.
echo   This starter window will close in 5 seconds.
echo ===================================================
timeout /t 5
