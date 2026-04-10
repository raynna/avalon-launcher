@echo off
setlocal

set "APP_DIR=%~dp0"
set "PACKAGED_DIST_BIN=%APP_DIR%app\bin\raynna-item-editor.bat"
set "DEV_DIST_BIN=%APP_DIR%build\install\raynna-item-editor\bin\raynna-item-editor.bat"
set "BOOTSTRAP_PS1=%APP_DIR%ensure-java.ps1"
set "JAVA_EXE="

if exist "%PACKAGED_DIST_BIN%" (
    set "DIST_BIN=%PACKAGED_DIST_BIN%"
) else (
    set "DIST_BIN=%DEV_DIST_BIN%"
)

if not exist "%DIST_BIN%" (
    call "%APP_DIR%gradlew.bat" installDist
    if errorlevel 1 exit /b %errorlevel%
)

if not exist "%BOOTSTRAP_PS1%" (
    echo Missing launcher dependency: ensure-java.ps1
    pause
    exit /b 1
)

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_EXE (
    for /f "usebackq delims=" %%I in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%BOOTSTRAP_PS1%" -AppRoot "%APP_DIR%" -RequiredMajor 21`) do set "JAVA_HOME=%%I"
)

if not defined JAVA_HOME (
    echo Failed to resolve Java 21+ for Item Editor.
    pause
    exit /b 1
)

if not defined JAVA_EXE set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo Java bootstrap completed, but java.exe was not found at "%JAVA_EXE%".
    pause
    exit /b 1
)

call "%DIST_BIN%"
exit /b %errorlevel%
