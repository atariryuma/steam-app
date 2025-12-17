@echo off
REM SteamDeck Mobile - Release APK Build Script
REM このスクリプトはRelease APK (最適化済み) をビルドします

echo ========================================
echo SteamDeck Mobile - Release Build Script
echo ========================================
echo.

REM Java環境チェック
echo [1/4] Checking Java environment...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found in PATH
    pause
    exit /b 1
)

java -version
echo.

REM Gradleキャッシュをクリーンアップ
echo [2/4] Cleaning build cache...
call gradlew clean
echo.

REM Release APKをビルド
echo [3/4] Building Release APK with ProGuard/R8 optimization...
echo This may take several minutes...
call gradlew assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Release build failed!
    pause
    exit /b 1
)
echo.

REM 成功メッセージ
echo [4/4] Release build successful!
echo.
echo ========================================
echo APK Location:
echo app\build\outputs\apk\release\app-release.apk
echo ========================================
echo.

REM APKファイルのサイズを表示
if exist "app\build\outputs\apk\release\app-release.apk" (
    for %%I in ("app\build\outputs\apk\release\app-release.apk") do (
        set size=%%~zI
        set /a sizeMB=!size!/1024/1024
        echo Optimized APK Size: !sizeMB! MB
        echo Target: ^<50 MB
    )
    echo.
    echo WARNING: This APK is NOT signed!
    echo For production release, you need to:
    echo 1. Create a keystore
    echo 2. Sign the APK with jarsigner
    echo 3. Optimize with zipalign
    echo.
    echo See SETUP.md for detailed instructions
    echo.

    choice /C YN /M "Open APK folder"
    if !errorlevel! equ 1 (
        explorer "app\build\outputs\apk\release"
    )
) else (
    echo WARNING: APK file not found
)

echo.
pause
