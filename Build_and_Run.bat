@echo off
echo =========================================
echo       Paychek Android Build & Run
echo =========================================
echo.

cd /d "%~dp0\app"

echo [1/3] Building Debug APK...
call gradlew assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ Build Failed! Please check the errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Installing APK to connected device...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ Installation Failed! Make sure a device/emulator is connected and USB debugging is enabled.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Launching the App...
adb shell monkey -p online.paychek.app -c android.intent.category.LAUNCHER 1 >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ⚠ Failed to launch the app automatically. You might need to open it manually.
) else (
    echo.
    echo ✅ App launched successfully!
)

echo.
pause
