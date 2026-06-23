@echo off
echo =========================================
echo       Paychek Android Clean Build ^& Run
echo =========================================
echo.

cd /d "%~dp0\app"

echo [1/4] Stopping Gradle Daemons to clear memory cache...
call gradlew --stop

echo.
echo [2/4] Cleaning old build files...
call gradlew clean

echo.
echo [3/4] Building Fresh Debug APK...
call gradlew assembleDebug --no-build-cache --rerun-tasks
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ Build Failed! Please check the errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [4/4] Installing APK to connected device...
adb install -r -t -d app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ Installation Failed! Trying full uninstall and reinstall...
    adb uninstall online.paychek.app
    adb install -r -t -d app\build\outputs\apk\debug\app-debug.apk
    if %ERRORLEVEL% NEQ 0 (
        echo ❌ Final Installation Failed! Make sure a device/emulator is connected and USB debugging is enabled.
        pause
        exit /b %ERRORLEVEL%
    )
)

echo.
echo [✓] Launching the App...
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
