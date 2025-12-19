@echo off
chcp 65001 >nul
REM SteamDeck Mobile - Debug APKビルド＆インストールスクリプト
REM 使用方法: install-debug.bat

echo ========================================
echo SteamDeck Mobile - Debug Build Install
echo ========================================
echo.

REM 環境変数設定
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set ADB=C:\Android\sdk\platform-tools\adb.exe
set PACKAGE=com.steamdeck.mobile.debug
set MAIN_ACTIVITY=com.steamdeck.mobile.presentation.MainActivity

echo [1/4] デバイス接続確認...
%ADB% devices
echo.

echo [2/4] ビルド開始...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo ビルドに失敗しました。
    pause
    exit /b 1
)
echo.

echo [3/4] APKインストール中...
%ADB% install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo インストールに失敗しました。
    pause
    exit /b 1
)
echo.

echo [4/4] アプリ起動中...
%ADB% shell am start -n %PACKAGE%/%MAIN_ACTIVITY%
echo.

echo ========================================
echo インストール完了！
echo パッケージ名: %PACKAGE%
echo ========================================
echo.
echo リアルタイムログを表示する場合:
echo   %ADB% logcat -s AppLogger:*
echo.
pause
