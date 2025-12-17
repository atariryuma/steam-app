@echo off
REM SteamDeck Mobile - Debug Info Collector
REM デバッグに必要な情報を一括取得します

echo ========================================
echo SteamDeck Mobile - Debug Info Collector
echo ========================================
echo.

REM adbのパス
set ADB_PATH=C:\Android\sdk\platform-tools\adb.exe

REM 保存先ファイル
set OUTPUT_FILE=debug_info.txt

if exist "%OUTPUT_FILE%" del "%OUTPUT_FILE%"

echo Collecting debug information...
echo.

REM adbが存在するか確認
if not exist "%ADB_PATH%" (
    echo ERROR: adb not found at %ADB_PATH%
    pause
    exit /b 1
)

echo [1/7] Device information... >> "%OUTPUT_FILE%"
echo ==================== DEVICE INFO ==================== >> "%OUTPUT_FILE%"
"%ADB_PATH%" devices -l >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo [2/7] Android version...
echo ==================== ANDROID VERSION ==================== >> "%OUTPUT_FILE%"
"%ADB_PATH%" shell getprop ro.build.version.release >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo [3/7] Device model...
echo ==================== DEVICE MODEL ==================== >> "%OUTPUT_FILE%"
"%ADB_PATH%" shell getprop ro.product.model >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo [4/7] App installation status...
echo ==================== APP INFO ==================== >> "%OUTPUT_FILE%"
"%ADB_PATH%" shell pm list packages | findstr "steamdeck" >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo [5/7] App version...
"%ADB_PATH%" shell dumpsys package com.steamdeck.mobile | findstr "versionName" >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo [6/7] Network connectivity...
echo ==================== NETWORK ==================== >> "%OUTPUT_FILE%"
"%ADB_PATH%" shell dumpsys connectivity | findstr "NetworkAgentInfo" >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo [7/7] Recent Steam logs (last 500 lines)...
echo ==================== RECENT STEAM LOGS ==================== >> "%OUTPUT_FILE%"
"%ADB_PATH%" logcat -d -t 500 | findstr "SteamAuth SteamLogin SteamDeckNavHost" >> "%OUTPUT_FILE%" 2>&1
echo. >> "%OUTPUT_FILE%"

echo.
echo ========================================
echo Debug info saved to: %OUTPUT_FILE%
echo ========================================
echo.
echo You can share this file when reporting issues.
echo.

REM ファイルを開く
type "%OUTPUT_FILE%"
echo.
pause
