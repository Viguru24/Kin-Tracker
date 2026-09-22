@echo off
title Kin-Tracker - Build & Install to All Devices
color 0A

set APK=app\build\outputs\apk\debug\app-debug.apk
set PACKAGE=com.aistudio.familytracker.lnvwe
set ACTIVITY=com.example.MainActivity

echo ============================================
echo   Kin-Tracker Auto-Builder & Installer
echo ============================================
echo.

echo [BUILD] Building latest APK with your newest changes...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed! Check compiler output above.
    pause
    exit /b 1
)

if not exist "%APK%" set APK=app\build\outputs\apk\debug\PulseTracker-v2.5-debug.apk

if not exist "%APK%" (
    echo.
    echo [ERROR] APK not found after build!
    pause
    exit /b 1
)

echo.
echo [INFO] Target APK: %APK%
echo [INFO] Scanning for connected devices...
echo.

adb devices -l
echo.

for /f "skip=1 tokens=1" %%D in ('adb devices') do (
    if not "%%D"=="" if not "%%D"=="*" (
        echo [^>^>] Installing on: %%D
        adb -s %%D install -r "%APK%"
        if errorlevel 1 (
            echo [FAIL] Install failed on %%D
        ) else (
            echo [OK]   Installed. Launching app on %%D...
            adb -s %%D shell am start -n "%PACKAGE%/%ACTIVITY%"
        )
        echo.
    )
)

echo ============================================
echo   Done! All connected devices updated.
echo ============================================
pause
