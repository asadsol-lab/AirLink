@echo off
title AirLink Signaling Server
cd /d "%~dp0companion\server"
echo ========================================================
echo               AirLink Signaling Server
echo ========================================================
echo.
echo PC Local IP: 192.168.0.110 (Port: 8080)
echo Mobile App Signaling URL: ws://192.168.0.110:8080
echo PC Companion Signaling URL: ws://127.0.0.1:8080
echo.
echo Starting Signaling Server... (Keep this window open)
echo ========================================================
node server.js
pause
