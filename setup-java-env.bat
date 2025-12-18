@echo off
REM SteamDeck Mobile - Java Environment Setup
REM This script sets JAVA_HOME and PATH permanently

echo Setting up Java environment variables...

REM Set JAVA_HOME (System-wide)
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot" /M

REM Add JAVA_HOME\bin to PATH (System-wide)
REM Note: This appends to PATH, doesn't replace it
for /f "skip=2 tokens=3*" %%a in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v Path') do set CURRENT_PATH=%%a %%b
setx PATH "%CURRENT_PATH%;%JAVA_HOME%\bin" /M

echo.
echo ========================================
echo Java environment variables set!
echo ========================================
echo JAVA_HOME: C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
echo PATH: Updated to include %JAVA_HOME%\bin
echo.
echo IMPORTANT: Please restart your terminal/IDE for changes to take effect.
echo.

pause
