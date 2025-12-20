@echo off
chcp 65001 >nul
REM ========================================
REM Clean Build - Full Stack Rebuild
REM ========================================
REM Use when: Build errors, stale cache, major changes
REM Process: Stop daemon → Clear cache → Build → Install → Launch
REM Speed: Slowest (5-10 min) but most reliable

echo ========================================
echo Clean Build - Full Stack
echo ========================================
echo.

REM Environment setup
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set ADB=C:\Android\sdk\platform-tools\adb.exe
set PACKAGE=com.steamdeck.mobile.debug
set MAIN_ACTIVITY=com.steamdeck.mobile.presentation.MainActivity
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk

cd /d C:\Projects\steam-app

echo [1/6] Stopping Gradle daemons...
call gradlew.bat --stop
echo.

echo [2/6] Cleaning build caches...
if exist .gradle\caches rmdir /s /q .gradle\caches
if exist .gradle\kotlin-profile rmdir /s /q .gradle\kotlin-profile
if exist .kotlin rmdir /s /q .kotlin
if exist build rmdir /s /q build
if exist app\build rmdir /s /q app\build
echo.

echo [3/6] Running Gradle clean...
call gradlew.bat clean --no-configuration-cache
echo.

echo [4/6] Building debug APK...
call gradlew.bat assembleDebug --no-configuration-cache --rerun-tasks
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    pause
    exit /b 1
)
echo.

echo [5/6] Installing APK...
"%ADB%" devices
"%ADB%" install -r "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo INSTALL FAILED!
    echo ========================================
    pause
    exit /b 1
)
echo.

echo [6/6] Launching app...
"%ADB%" shell am start -n %PACKAGE%/%MAIN_ACTIVITY%
echo.

echo ========================================
echo Clean Build Complete!
echo ========================================
echo APK: %APK_PATH%
echo Package: %PACKAGE%
echo.
echo View logs: view-logs.bat
echo ========================================
pause
