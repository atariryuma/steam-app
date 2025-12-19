@echo off
REM ========================================
REM Quick Build Script (No Cache Clear)
REM ========================================
REM Use this for normal development builds
REM Use clean-build.bat if you see stale code issues

echo Building debug APK...
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Projects\steam-app

call gradlew.bat assembleDebug --no-configuration-cache

echo.
if %ERRORLEVEL% EQU 0 (
    echo ========================================
    echo Build successful!
    echo APK: app\build\outputs\apk\debug\app-debug.apk
    echo ========================================
) else (
    echo ========================================
    echo Build FAILED!
    echo Try running clean-build.bat instead
    echo ========================================
)
pause
