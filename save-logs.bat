@echo off
REM SteamDeck Mobile - Log Saver
REM Steam認証のログをファイルに保存します

echo ========================================
echo SteamDeck Mobile - Log Saver
echo ========================================
echo.

REM adbのパス
set ADB_PATH=C:\Android\sdk\platform-tools\adb.exe

REM 保存先ファイル名（タイムスタンプ付き）
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set LOG_FILE=steam_auth_log_%TIMESTAMP%.txt

REM adbが存在するか確認
if not exist "%ADB_PATH%" (
    echo ERROR: adb not found at %ADB_PATH%
    pause
    exit /b 1
)

echo [1/4] Checking connected devices...
"%ADB_PATH%" devices
echo.

REM デバイスが接続されているか確認
"%ADB_PATH%" devices | findstr "device$" >nul
if %errorlevel% neq 0 (
    echo ERROR: No devices connected!
    pause
    exit /b 1
)

echo [2/4] Clearing previous logs...
"%ADB_PATH%" logcat -c
echo.

echo [3/4] Starting log capture...
echo Log file: %LOG_FILE%
echo Press Ctrl+C when you want to stop logging
echo.
echo TIP: Now open the app and try Steam authentication
echo.

REM ログをファイルに保存（フィルタなし、全ログ）
"%ADB_PATH%" logcat > "%LOG_FILE%"

echo.
echo [4/4] Log saved to: %LOG_FILE%
echo.

REM Steam関連のログを抽出
echo Extracting Steam-related logs...
findstr /C:"SteamAuth" /C:"SteamLogin" /C:"SteamDeckNavHost" "%LOG_FILE%" > steam_filtered_%TIMESTAMP%.txt

echo.
echo ========================================
echo Logs saved:
echo - Full log:     %LOG_FILE%
echo - Filtered log: steam_filtered_%TIMESTAMP%.txt
echo ========================================
echo.
pause
