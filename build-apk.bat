@echo off
title Paychek APK Builder
echo ===================================================
echo   Building Paychek Debug APK...
echo ===================================================

:: Set Android SDK location
set "ANDROID_HOME=C:\Users\DFIT\AppData\Local\Android\Sdk"
set "ANDROID_SDK_ROOT=C:\Users\DFIT\AppData\Local\Android\Sdk"

:: Use the JDK bundled with Android Studio (JBR)
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Navigate to the Gradle project root
cd /d "%~dp0app"

:: Wipe previous build outputs so this run produces a clean APK
echo   Cleaning previous build artifacts...
call gradlew.bat clean
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ===================================================
    echo   CLEAN FAILED
    echo ===================================================
    pause
    exit /b 1
)

:: Run the Gradle build
call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo   BUILD SUCCESSFUL
    echo   APK: %~dp0app\app\build\outputs\apk\debug\app-debug.apk
    echo ===================================================
) else (
    echo.
    echo ===================================================
    echo   BUILD FAILED
    echo ===================================================
)
pause
