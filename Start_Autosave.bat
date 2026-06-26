@echo off
title Chat Autosave Daemon
echo ===================================================
echo   Starting Chat Autosave Daemon...
echo   This window will auto-save your chat history.
echo   Keep this window open while chatting.
echo ===================================================
node "%~dp0autosave_chat.js"
pause
