@echo off
setlocal
set "TRIGGER=manual"
if /i "%~1"=="scheduled" set "TRIGGER=scheduled"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_happy_housing_crawler.ps1" -Trigger "%TRIGGER%"
exit /b %ERRORLEVEL%
