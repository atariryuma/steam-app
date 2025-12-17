@echo off
REM ============================================================================
REM Steam Deck Mobile - Debug Build and Install Script
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ========================================
echo Steam Deck Mobile - Debug Build
echo ========================================
echo.

REM Step 1: Set JAVA_HOME
echo [1/4] Setting up Java environment...
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d C:\Projects\steam-app

REM Verify Java version
java -version 2>&1 | findstr /C:"17.0" >nul
if errorlevel 1 (
    echo ERROR: Java 17 not found
    pause
    exit /b 1
)
echo ✓ Java 17 detected
echo.

REM Step 2: Build Debug APK
echo [2/4] Building debug APK...
echo This is faster than release (no R8/ProGuard optimization)
echo.
call gradlew.bat assembleDebug --console=plain
if errorlevel 1 (
    echo ERROR: Build failed
    pause
    exit /b 1
)
echo ✓ Build completed
echo.

REM Step 3: Verify APK
echo [3/4] Verifying APK output...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do (
        set /a sizeMB=%%~zA / 1048576
    )
    echo ✓ APK found: app\build\outputs\apk\debug\app-debug.apk
    echo ✓ APK size: !sizeMB! MB
) else (
    echo ERROR: APK not found
    pause
    exit /b 1
)
echo.

REM Step 4: Install on device
echo [4/4] Installing on device...
C:\Android\sdk\platform-tools\adb.exe devices | findstr /R "device$" >nul
if errorlevel 1 (
    echo WARNING: No device connected
    echo Please connect your device and run:
    echo adb install -r app\build\outputs\apk\debug\app-debug.apk
    pause
    exit /b 0
)

echo ✓ Device connected
C:\Android\sdk\platform-tools\adb.exe install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
    echo ERROR: Installation failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS: Debug APK installed
echo ========================================
echo.
echo Package: com.steamdeck.mobile.debug
echo Version: 0.1.0-alpha-debug
echo.
echo To launch the app:
echo adb shell am start -n com.steamdeck.mobile.debug/.presentation.MainActivity
echo.
pause
