@echo off
REM ========================================
REM Clean Build Script for Steam Deck Mobile
REM ========================================
REM This script ensures a completely fresh build by:
REM 1. Stopping all Gradle daemons
REM 2. Cleaning all build caches
REM 3. Running a clean debug build

echo [1/4] Stopping Gradle daemons...
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Projects\steam-app
call gradlew.bat --stop

echo.
echo [2/4] Cleaning build caches...
if exist .gradle\caches rmdir /s /q .gradle\caches
if exist .gradle\kotlin-profile rmdir /s /q .gradle\kotlin-profile
if exist .kotlin rmdir /s /q .kotlin
if exist build rmdir /s /q build
if exist app\build rmdir /s /q app\build

echo.
echo [3/4] Running Gradle clean...
call gradlew.bat clean --no-configuration-cache

echo.
echo [4/4] Building debug APK...
call gradlew.bat assembleDebug --no-configuration-cache --rerun-tasks

echo.
echo ========================================
echo Build complete!
echo APK location: app\build\outputs\apk\debug\app-debug.apk
echo ========================================
pause
