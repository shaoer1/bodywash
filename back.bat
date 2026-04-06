@echo off
setlocal EnableExtensions
REM Use ASCII only in this file so cmd.exe parses correctly on Chinese Windows.

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "PY=%ROOT%\venv\Scripts\python.exe"
if not exist "%PY%" (
  echo [ERROR] venv not found: "%PY%"
  echo Run: python -m venv venv
  echo Then: venv\Scripts\pip install -r requirements.txt
  goto :END
)

pushd "%ROOT%\user-ui\backend" || goto :END
call mvn -q -DskipTests compile
if errorlevel 1 (
  echo [ERROR] mvn compile failed. Install JDK 17+ and Maven, add to PATH.
  popd
  goto :END
)
popd

echo.
echo ========================================
echo   Starting backends (2 windows):
echo   1) Flask  http://127.0.0.1:5000  (venv python)
echo   2) Spring Boot   http://127.0.0.1:8080  -^> Flask
echo ========================================
echo.

start "Flask-5000" /D "%ROOT%" cmd /k set PYTHONIOENCODING=utf-8 ^&^& "%PY%" app.py
start "Spring-Boot-8080" /D "%ROOT%\user-ui\backend" cmd /k "mvn spring-boot:run"

echo Started. Close each window to stop that service.

:END
echo.
pause
endlocal
