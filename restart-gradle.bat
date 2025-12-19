@echo off
REM ========================================
REM Restart Gradle Daemon
REM ========================================
REM Use this when builds hang or become unresponsive

echo Stopping all Gradle daemons...
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Projects\steam-app

call gradlew.bat --stop

echo.
echo Waiting 3 seconds...
timeout /t 3 /nobreak >nul

echo.
echo Starting new daemon...
call gradlew.bat --version

echo.
echo ========================================
echo Gradle daemon restarted
echo You can now run your build
echo ========================================
pause
