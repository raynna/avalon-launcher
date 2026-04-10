@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "TOKEN_FILE=%SCRIPT_DIR%.github-token"

if not defined GITHUB_TOKEN if exist "%TOKEN_FILE%" (
    for /f "usebackq delims=" %%I in ("%TOKEN_FILE%") do (
        if not defined GITHUB_TOKEN set "GITHUB_TOKEN=%%I"
    )
)

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
