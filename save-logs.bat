@echo off
REM SteamDeck Mobile - Log Saver
REM Saves Steam authentication logs to file

echo ========================================
echo SteamDeck Mobile - Log Saver
echo ========================================
echo.

REM ADB path
set ADB_PATH=C:\Android\sdk\platform-tools\adb.exe

REM Output filename (with timestamp)
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set LOG_FILE=steam_auth_log_%TIMESTAMP%.txt

REM Check if adb exists
if not exist "%ADB_PATH%" (
    echo ERROR: adb not found at %ADB_PATH%
    pause
    exit /b 1
)

echo [1/4] Checking connected devices...
"%ADB_PATH%" devices
echo.

REM Check if device is connected
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

REM Save logs to file (no filter, all logs)
"%ADB_PATH%" logcat > "%LOG_FILE%"

echo.
echo [4/4] Log saved to: %LOG_FILE%
echo.

REM Extract Steam-related logs
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
