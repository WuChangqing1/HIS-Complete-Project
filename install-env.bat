@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" -InstallOnly
if errorlevel 1 (echo [ERROR] Environment setup failed.) else (echo [OK] Environment checked. Existing global variables preserved; missing user entries added.)
pause
