@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" -Rebuild
if errorlevel 1 (echo [ERROR] Rebuild failed. See .runtime\logs.) else (echo [OK] Rebuild completed.)
pause
