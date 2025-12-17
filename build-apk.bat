@echo off
REM SteamDeck Mobile - APK Build Script
REM このスクリプトはDebug APKをビルドします

echo ========================================
echo SteamDeck Mobile - APK Build Script
echo ========================================
echo.

REM Set JAVA_HOME explicitly
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

REM Java環境チェック
echo [1/5] Checking Java environment...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found in PATH
    echo Please install JDK 17 and set JAVA_HOME
    echo Download: https://adoptium.net/temurin/releases/?version=17
    pause
    exit /b 1
)

java -version
echo.

REM JAVA_HOMEチェック
if not defined JAVA_HOME (
    echo WARNING: JAVA_HOME is not set
    echo Please set JAVA_HOME to your JDK 17 installation path
    echo Example: set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot
    pause
)

REM Gradleキャッシュをクリーンアップ (オプション)
echo [2/5] Cleaning build cache...
call gradlew clean
if %errorlevel% neq 0 (
    echo ERROR: Gradle clean failed
    pause
    exit /b 1
)
echo.

REM 依存関係の解決
echo [3/5] Resolving dependencies...
echo This may take a few minutes on first run...
echo.

REM Debug APKをビルド
echo [4/5] Building Debug APK...
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Build failed!
    echo.
    echo Common solutions:
    echo - Ensure JDK 17 is installed
    echo - Check your internet connection (for dependency downloads)
    echo - Run: gradlew --refresh-dependencies
    echo.
    pause
    exit /b 1
)
echo.

REM 成功メッセージ
echo [5/5] Build successful!
echo.
echo ========================================
echo APK Location:
echo app\build\outputs\apk\debug\app-debug.apk
echo ========================================
echo.

REM APKファイルのサイズを表示
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    for %%I in ("app\build\outputs\apk\debug\app-debug.apk") do (
        set size=%%~zI
        set /a sizeMB=!size!/1024/1024
        echo APK Size: !sizeMB! MB
    )
    echo.
    echo To install on device:
    echo adb install app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Or open the APK file location in Explorer?
    choice /C YN /M "Open APK folder"
    if !errorlevel! equ 1 (
        explorer "app\build\outputs\apk\debug"
    )
) else (
    echo WARNING: APK file not found at expected location
)

echo.
pause
