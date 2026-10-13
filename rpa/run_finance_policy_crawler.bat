@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
cd /d "%~dp0"
set "TRIGGER=manual"
if /i "%~1"=="scheduled" set "TRIGGER=scheduled"
set "PYTHON=python"
if exist "..\.venv\Scripts\python.exe" set "PYTHON=..\.venv\Scripts\python.exe"
if not exist "logs" mkdir "logs"
"%PYTHON%" finance_policy_crawler.py --trigger "%TRIGGER%" >>"logs\finance_policy.log" 2>&1
set "EXITCODE=%ERRORLEVEL%"
echo 금융 수집 종료: exit=%EXITCODE% 로그=rpa/logs/finance_policy.log
exit /b %EXITCODE%
