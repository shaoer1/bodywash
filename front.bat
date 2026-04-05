@echo off
setlocal enableextensions enabledelayedexpansion
chcp 65001 >nul

title LoRA Studio - 前端服务
set "ROOT=%~dp0"
set "WEBUI=%ROOT%train-ui"
cd /d "%WEBUI%"

echo.
echo ========================================
echo   LoRA Studio 前端服务
echo   工作目录: %CD%
echo   访问地址: http://localhost:3000
echo   代理后端: http://127.0.0.1:5000
echo   按 Ctrl+C 停止服务
echo ========================================
echo.

if not exist "%WEBUI%\package.json" (
  echo [错误] 未找到 package.json，请确认 train-ui 目录存在。
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [错误] 未检测到 npm，请先安装 Node.js 并配置 PATH。
  pause
  exit /b 1
)

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":3000" ^| findstr LISTENING') do (
  echo [信息] 结束占用 3000 端口的进程 PID=%%p
  taskkill /F /PID %%p >nul 2>nul
)

echo [信息] 正在启动前端...
call npm run dev -- --host 0.0.0.0 --port 3000

echo.
echo [信息] 前端已退出。
pause
