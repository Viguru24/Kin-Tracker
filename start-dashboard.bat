@echo off
title Kin-Tracker Sovereign Radar - Port 4666 Dashboard
color 0B

echo ================================================================
echo    ___  _____ _   _       _____ ____      _    ____ _  _______ ____  
echo   ^| ^|/ /_ _^| \ ^| ^|     ^|_   _^|  _ \    / \  / ___^| ^|/ / ____^|  _ \ 
echo   ^| ' / ^| ^|^|  \^| ^| _____ ^| ^| ^| ^|_) ^|  / _ \^| ^|   ^| ' /^|  _^| ^| ^|_) ^|
echo   ^| . \ ^| ^|^| ^|\  ^| _____^| ^| ^| ^|  _ ^<  / ___ \ ^|___^| . \^| ^|___^|  _ ^< 
echo   ^|_^|\_\___^|_^| \_^|       ^|_^| ^|_^| \_\/_/   \_\____^|_^|\_\_____^|_^| \_\
echo.
echo           SOVEREIGN VPS RADAR ^& COMMAND CENTER (PORT 4666)
echo ================================================================
echo.

:: Check Node.js
where node >nul 2>nul
if errorlevel 1 (
    color 0C
    echo [ERROR] Node.js is not installed or not found in PATH!
    echo Please install Node.js from https://nodejs.org/ and try again.
    pause
    exit /b 1
)

:: Navigate to server directory
cd /d "%~dp0server"

:: Install dependencies if node_modules is missing
if not exist "node_modules" (
    echo [SETUP] First-time setup: Installing server dependencies...
    call npm install
    if errorlevel 1 (
        color 0C
        echo [ERROR] Failed to install npm dependencies.
        pause
        exit /b 1
    )
)

echo [INIT] Starting Sovereign GPS Server on port 4666...
echo [INFO] Web Dashboard: http://localhost:4666/dashboard
echo [INFO] Sovereign Ring: Over-the-Air High-Decibel Siren Enabled
echo.

:: Launch browser in background after 2 seconds
start "" powershell -NoProfile -Command "Start-Sleep -Seconds 2; Start-Process 'http://localhost:4666/dashboard'"

:: Start node server (keeps command prompt window active with live logs)
node src/index.js

if errorlevel 1 (
    echo.
    echo [INFO] Server stopped.
    pause
)
