@echo off
chcp 65001 >nul
set PYTHONUTF8=1
set PYTHONIOENCODING=utf-8
title LoRA Studio - 后端服务
cd /d D:\train
echo.
echo ========================================
echo  LoRA Studio 后端服务
echo  访问地址: http://localhost:5000
echo  按 Ctrl+C 停止服务
echo ========================================
echo.
taskkill /F /IM python.exe /FI "WINDOWTITLE eq LoRA Studio - 后端服务" 2>nul
D:\kohya_ss\venv\Scripts\python.exe app.py
pause
