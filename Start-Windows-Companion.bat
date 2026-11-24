@echo off
title AirLink Desktop Companion
echo ========================================================
echo               AirLink Desktop Companion
echo ========================================================
echo.

if not exist "%~dp0companion\windows\node_modules\electron\dist\electron.exe" (
    echo [INFO] Checking Electron engine...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0companion\windows\setup_electron.ps1"
    if not exist "%~dp0companion\windows\node_modules\electron\dist\electron.exe" (
        echo.
        echo [INFO] Electron engine abhi download ho raha hai.
        echo Barahe meherbani 2 se 3 minute intazar karein aur phir dobara chalayein.
        echo.
        pause
        exit /b 1
    )
)

echo Launching Windows Companion App...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0companion\windows'; node node_modules\electron\cli.js ."
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] App band ho gaya ya error aya.
    pause
)
