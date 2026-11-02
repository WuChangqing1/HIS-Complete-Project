@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-his.ps1"
if errorlevel 1 (echo [ERROR] Check error above.) else (echo [OK] Stopped. Database files kept.)
pause
