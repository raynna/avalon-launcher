@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%release-oneclick.ps1" -ConfigPath "%SCRIPT_DIR%release-config.json"
if errorlevel 1 (
    echo.
    echo Release failed.
    pause
    exit /b 1
)
echo.
echo Release finished.
pause
