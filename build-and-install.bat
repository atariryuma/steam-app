@echo off
REM ============================================================================
REM Steam Deck Mobile - Build and Install Script
REM
REM This script builds the release APK and installs it on a connected device
REM Based on Android best practices 2025
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ========================================
echo Steam Deck Mobile - Build and Install
echo ========================================
echo.

REM Step 1: Build Release APK
echo [1/3] Building release APK...
echo.
call build-release.bat
if errorlevel 1 (
    echo ERROR: Build failed
    pause
    exit /b 1
)

REM Step 2: Check for connected devices
echo.
echo [2/3] Checking for connected devices...
C:\Android\sdk\platform-tools\adb.exe devices | findstr /R "device$" >nul
if errorlevel 1 (
    echo ERROR: No Android device connected
    echo.
    echo Please connect your device via USB and enable USB debugging
    echo Then run this script again
    pause
    exit /b 1
)

REM List connected devices
echo ✓ Connected devices:
C:\Android\sdk\platform-tools\adb.exe devices
echo.

REM Step 3: Install APK
echo [3/3] Installing APK on device...
C:\Android\sdk\platform-tools\adb.exe install -r "app\build\outputs\apk\release\app-release.apk"
if errorlevel 1 (
    echo.
    echo ERROR: Installation failed
    echo.
    echo Common causes:
    echo - USB debugging not enabled
    echo - Device not authorized
    echo - Insufficient storage space
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS: APK installed on device
echo ========================================
echo.
echo You can now launch the app on your device:
echo - App name: Steam Deck Mobile
echo - Package: com.steamdeck.mobile
echo.
echo To launch via ADB:
echo adb shell am start -n com.steamdeck.mobile/.presentation.MainActivity
echo.

pause
