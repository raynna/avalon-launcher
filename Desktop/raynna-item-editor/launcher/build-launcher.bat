@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build-exe.ps1"
if errorlevel 1 (
    echo.
    echo Launcher build failed.
    pause
    exit /b 1
)
echo.
echo Launcher build finished.
pause
