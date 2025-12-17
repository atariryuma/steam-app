@echo off
setlocal enabledelayedexpansion

echo ========================================
echo SteamDeck Mobile - Building with Auth
echo ========================================
echo.

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "ANDROID_HOME=C:\Android\sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_HOME: %ANDROID_HOME%
echo.

echo Building Debug APK...
call gradlew.bat assembleDebug --stacktrace

if %errorlevel% neq 0 (
    echo.
    echo Build failed with error code: %errorlevel%
    pause
    exit /b %errorlevel%
)

echo.
echo ========================================
echo Build successful!
echo APK: app\build\outputs\apk\debug\app-debug.apk
echo ========================================
pause
