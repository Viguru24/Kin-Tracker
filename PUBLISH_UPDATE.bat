@echo off
setlocal
cd /d "%~dp0"
title Kin-Tracker OTA Publisher
color 0B

echo ========================================================
echo   Kin-Tracker Over-The-Air (OTA) Publisher
echo ========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-update.ps1"

echo.
echo ========================================================
pause
