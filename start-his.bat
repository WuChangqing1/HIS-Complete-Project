@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1"
if errorlevel 1 (
  echo.
  echo [ERROR] HIS deployment failed. Check the message above and .runtime\logs.
  echo [ERROR] Your MySQL data was NOT automatically deleted.
  pause
  exit /b 1
)
echo.
echo [OK] HIS services are running in background. Close this window safely.
pause
