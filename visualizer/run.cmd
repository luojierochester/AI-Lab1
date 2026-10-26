@echo off
setlocal
if not exist "%~dp0bin\npuzzle-viewer.exe" (
  echo Please run PowerShell: powershell -ExecutionPolicy Bypass -File "%~dp0build.ps1"
  exit /b 1
)
"%~dp0bin\npuzzle-viewer.exe" %*
