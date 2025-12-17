@echo off
REM ============================================================================
REM Steam Deck Mobile - Release APK Build Script
REM
REM Based on Android best practices 2025:
REM - https://developer.android.com/build/building-cmdline
REM - https://www.codegenes.net/blog/how-to-create-a-release-signed-apk-file-using-gradle/
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ========================================
echo Steam Deck Mobile - Release Build
echo ========================================
echo.

REM Step 1: Set JAVA_HOME
echo [1/5] Setting up Java environment...
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Verify Java version
java -version 2>&1 | findstr /C:"17.0" >nul
if errorlevel 1 (
    echo ERROR: Java 17 not found or JAVA_HOME not set correctly
    echo Current JAVA_HOME: %JAVA_HOME%
    pause
    exit /b 1
)
echo ✓ Java 17 detected
echo.

REM Step 2: Clean previous build
echo [2/5] Cleaning previous build artifacts...
call gradlew.bat clean --console=plain
if errorlevel 1 (
    echo ERROR: Clean failed
    pause
    exit /b 1
)
echo ✓ Clean completed
echo.

REM Step 3: Build Release APK
echo [3/5] Building release APK...
echo This may take 2-5 minutes (R8 optimization + ProGuard)...
echo.
call gradlew.bat assembleRelease --console=plain --stacktrace
if errorlevel 1 (
    echo.
    echo ERROR: Build failed
    echo Check the error messages above for details
    pause
    exit /b 1
)
echo ✓ Build completed successfully
echo.

REM Step 4: Verify APK output
echo [4/5] Verifying APK output...
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo ✓ APK found: app\build\outputs\apk\release\app-release.apk

    REM Get file size
    for %%A in ("app\build\outputs\apk\release\app-release.apk") do (
        set "size=%%~zA"
    )

    REM Convert to MB
    set /a sizeMB=!size! / 1048576
    echo ✓ APK size: !sizeMB! MB
) else (
    echo ERROR: APK not found at expected location
    pause
    exit /b 1
)
echo.

REM Step 5: Summary
echo [5/5] Build summary
echo ========================================
echo Status:        SUCCESS
echo APK Location:  app\build\outputs\apk\release\app-release.apk
echo APK Size:      !sizeMB! MB
echo Build Type:    Release (unsigned)
echo Version:       0.1.0-alpha
echo ========================================
echo.
echo NOTE: This APK is unsigned and suitable for testing only.
echo For Play Store distribution, you need to sign it with a release keystore.
echo.
echo Next steps:
echo 1. Install on device: adb install -r app\build\outputs\apk\release\app-release.apk
echo 2. Or run: build-and-install.bat
echo.

pause
