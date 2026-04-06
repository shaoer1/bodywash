@echo off
setlocal EnableExtensions
REM ASCII-only: avoids UTF-8 batch parse errors on Chinese Windows.

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "NPM=npm"
if exist "D:\Program Files\nodejs\npm.cmd" set "NPM=D:\Program Files\nodejs\npm.cmd"

if not exist "%ROOT%\train-ui\node_modules\vite" (
  echo [train-ui] npm install...
  pushd "%ROOT%\train-ui"
  call "%NPM%" install
  if errorlevel 1 goto :END
  popd
)

if not exist "%ROOT%\user-ui\frontend\node_modules\vite" (
  echo [user-ui] npm install...
  pushd "%ROOT%\user-ui\frontend"
  call "%NPM%" install
  if errorlevel 1 goto :END
  popd
)

echo.
echo ========================================
echo   Starting frontends (2 windows):
echo   train-ui   http://localhost:3000  -^> Flask :5000  (training / inference UI)
echo   user-ui    http://localhost:3001  -^> Java :8081
echo   Run back.bat first for backends.
echo ========================================
echo.

start "train-ui-3000" /D "%ROOT%\train-ui" cmd /k call "%NPM%" run dev
start "user-ui-3001" /D "%ROOT%\user-ui\frontend" cmd /k call "%NPM%" run dev

echo Started.

:END
echo.
pause
endlocal
