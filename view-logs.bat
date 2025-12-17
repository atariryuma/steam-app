@echo off
REM SteamDeck Mobile - Log Viewer
REM Steam認証のログをリアルタイムで表示します

echo ========================================
echo SteamDeck Mobile - Log Viewer
echo ========================================
echo.

REM adbのパス
set ADB_PATH=C:\Android\sdk\platform-tools\adb.exe

REM adbが存在するか確認
if not exist "%ADB_PATH%" (
    echo ERROR: adb not found at %ADB_PATH%
    echo Please install Android SDK Platform-Tools
    pause
    exit /b 1
)

echo [1/3] Checking connected devices...
"%ADB_PATH%" devices
echo.

REM デバイスが接続されているか確認
"%ADB_PATH%" devices | findstr "device$" >nul
if %errorlevel% neq 0 (
    echo.
    echo WARNING: No devices connected!
    echo.
    echo Please connect your Android device via USB and enable USB debugging:
    echo 1. Settings ^> About phone ^> Tap "Build number" 7 times
    echo 2. Settings ^> System ^> Developer options ^> Enable "USB debugging"
    echo 3. Connect device to PC via USB
    echo 4. Allow USB debugging on device popup
    echo.
    pause
    exit /b 1
)

echo [2/3] Clearing previous logs...
"%ADB_PATH%" logcat -c
echo Previous logs cleared.
echo.

echo [3/3] Starting real-time log monitoring...
echo.
echo ========================================
echo Watching Steam Authentication Logs
echo Press Ctrl+C to stop
echo ========================================
echo.

REM Steam認証関連のログをフィルタして表示
"%ADB_PATH%" logcat | findstr "SteamAuth SteamLogin SteamDeckNavHost"
