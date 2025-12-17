@echo off
setlocal enabledelayedexpansion
REM SteamDeck Mobile - Environment Check Script
REM Android開発に必要な環境が整っているかチェックします

echo ========================================
echo SteamDeck Mobile - Environment Check
echo ========================================
echo.

set ERRORS=0

REM Java チェック
echo [1/5] Checking Java...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [X] Java NOT FOUND
    echo     Please install JDK 17
    echo     Download: https://adoptium.net/temurin/releases/?version=17
    set /a ERRORS+=1
) else (
    echo [OK] Java found:
    java -version 2>&1 | findstr "version"

    REM Java バージョンチェック
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr "version"') do (
        set JAVA_VERSION=%%v
        set JAVA_VERSION=!JAVA_VERSION:"=!
        echo     Version: !JAVA_VERSION!

        REM バージョンが17以上かチェック (簡易版)
        echo !JAVA_VERSION! | findstr /C:"17." >nul
        if !errorlevel! neq 0 (
            echo [!] WARNING: JDK 17 is recommended for this project
            set /a ERRORS+=1
        )
    )
)
echo.

REM JAVA_HOME チェック
echo [2/5] Checking JAVA_HOME...
if not defined JAVA_HOME (
    echo [!] JAVA_HOME is NOT SET
    echo     Please set JAVA_HOME environment variable
    echo     Example: set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot
    set /a ERRORS+=1
) else (
    echo [OK] JAVA_HOME: %JAVA_HOME%
    if not exist "%JAVA_HOME%\bin\java.exe" (
        echo [X] Invalid JAVA_HOME path
        set /a ERRORS+=1
    )
)
echo.

REM Android SDK チェック
echo [3/5] Checking Android SDK...
if not defined ANDROID_HOME (
    if not defined ANDROID_SDK_ROOT (
        echo [!] ANDROID_HOME is NOT SET
        echo     Please install Android SDK and set ANDROID_HOME
        echo     Typical location: C:\Users\%USERNAME%\AppData\Local\Android\Sdk
        set /a ERRORS+=1
    ) else (
        set ANDROID_HOME=%ANDROID_SDK_ROOT%
        echo [OK] Using ANDROID_SDK_ROOT: !ANDROID_HOME!
    )
) else (
    echo [OK] ANDROID_HOME: %ANDROID_HOME%
)

if defined ANDROID_HOME (
    if not exist "%ANDROID_HOME%\platform-tools" (
        echo [X] platform-tools not found in ANDROID_HOME
        set /a ERRORS+=1
    )
)
echo.

REM ADB チェック
echo [4/5] Checking ADB (Android Debug Bridge)...
where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] ADB NOT FOUND in PATH
    echo     Add to PATH: %ANDROID_HOME%\platform-tools
    set /a ERRORS+=1
) else (
    echo [OK] ADB found:
    adb version 2>&1 | findstr "Version"
)
echo.

REM Gradle チェック
echo [5/5] Checking Gradle Wrapper...
if exist "gradlew.bat" (
    echo [OK] Gradle wrapper found
    call gradlew --version 2>&1 | findstr "Gradle"
) else (
    echo [X] gradlew.bat NOT FOUND
    echo     This script must be run from project root
    set /a ERRORS+=1
)
echo.

REM 結果サマリー
echo ========================================
echo Environment Check Complete
echo ========================================
if %ERRORS% equ 0 (
    echo [OK] All checks passed!
    echo      You can now build the APK with: build-apk.bat
) else (
    echo [X] Found %ERRORS% issue(s)
    echo     Please fix the above issues before building
    echo.
    echo Quick Setup Guide:
    echo 1. Install JDK 17 from https://adoptium.net/
    echo 2. Install Android Studio from https://developer.android.com/studio
    echo 3. Set environment variables:
    echo    - JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x
    echo    - ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
    echo 4. Add to PATH:
    echo    - %%ANDROID_HOME%%\platform-tools
    echo.
    echo See SETUP.md for detailed instructions
)
echo.

pause
