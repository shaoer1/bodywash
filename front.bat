@echo off
chcp 65001 >nul
title LoRA Studio - 前端服务
cd /d D:\train\vue-webui
echo.
echo ========================================
echo  LoRA Studio 前端服务
echo  访问地址: http://localhost:3000
echo  按 Ctrl+C 停止服务
echo ========================================
echo.
"D:\Program Files\nodejs\npm.cmd" run dev
pause
