@echo off
chcp 65001 >nul
REM ========================================
REM Quick Build - Fast Development Cycle
REM ========================================
REM Use for: Normal development (most common)
REM Process: Build → Install → Launch
REM Speed: Fast (1-3 min) - keeps Gradle cache

echo ========================================
echo Quick Build - Development Mode
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

echo [1/3] Building debug APK...
call gradlew.bat assembleDebug --no-configuration-cache
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    echo TIP: Try clean-build.bat if errors persist
    pause
    exit /b 1
)
echo.

echo [2/3] Installing APK...
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

echo [3/3] Launching app...
"%ADB%" shell am start -n %PACKAGE%/%MAIN_ACTIVITY%
echo.

echo ========================================
echo Quick Build Complete!
echo ========================================
echo APK: %APK_PATH%
echo Package: %PACKAGE%
echo.
echo View logs: view-logs.bat
echo ========================================
pause
