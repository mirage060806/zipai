@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
cd /d "%~dp0"
set "PYTHON=python"
if exist "..\.venv\Scripts\python.exe" set "PYTHON=..\.venv\Scripts\python.exe"
set "TRIGGER=manual"
if /i "%~1"=="scheduled" set "TRIGGER=scheduled"
"%PYTHON%" lifestyle_pipeline.py --trigger "%TRIGGER%"
exit /b %ERRORLEVEL%
