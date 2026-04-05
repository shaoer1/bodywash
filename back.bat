@echo off
setlocal enableextensions enabledelayedexpansion
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"

title LoRA Studio - 后端服务
set "ROOT=%~dp0"
cd /d "%ROOT%"

echo.
echo ========================================
echo   LoRA Studio 后端服务
echo   工作目录: %CD%
echo   访问地址: http://localhost:5000
echo   按 Ctrl+C 停止服务
echo ========================================
echo.

if not exist "%ROOT%app.py" (
  echo [错误] 未找到 app.py，请确认脚本位于项目根目录。
  pause
  exit /b 1
)

if not exist "%ROOT%venv\Scripts\python.exe" (
  echo [错误] 未找到 Python 虚拟环境: %ROOT%venv\Scripts\python.exe
  echo 请先创建并安装依赖后再启动。
  pause
  exit /b 1
)

for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":5000" ^| findstr LISTENING') do (
  echo [信息] 结束占用 5000 端口的进程 PID=%%p
  taskkill /F /PID %%p >nul 2>nul
)

echo [信息] 正在启动后端...
"%ROOT%venv\Scripts\python.exe" "%ROOT%app.py"

echo.
echo [信息] 后端已退出。
pause
