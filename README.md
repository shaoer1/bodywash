# 沐浴露瓶垂直领域模型训练与推理系统

## 项目概述

本项目是一个专业的模型训练与推理系统，专注于沐浴露瓶的文生图任务。系统集成了完整的模型训练、推理和管理功能，提供直观的Web可视化界面，实现了从数据准标注到模型部署的全流程解决方案。

### 核心功能

- **模型训练**：支持 SD1.5 和 SDXL 模型的 LoRA 轻量化微调
- **可视化界面**：提供直观的 Web 前端，无需命令行操作
- **模型推理**：集成文生图功能，支持加载和使用训练好的 LoRA 模型
- **数据管理**：内置数据集管理和标注工具
- **环境检测**：自动检测系统环境和依赖项

## 技术栈

| 类别 | 技术/框架 | 版本 | 用途 |
|------|-----------|------|------|
| 后端 | Python | 3.10.x | 核心运行环境 |
| 后端框架 | Flask | 3.0.3 | API 服务 |
| 前端 | Vue 3 | 3.4.0+ | 可视化界面 |
| 前端路由 | Vue Router | 4.2.5 | 页面导航 |
| 模型训练 | Kohya_ss | 最新版 | LoRA 训练工具 |
| 深度学习 | PyTorch | 2.1.2 | 模型训练与推理 |
| 扩散模型 | Diffusers | 0.27.2 | 模型推理 |
| 模型压缩 | PEFT | 0.9.0 | LoRA 实现 |
| 硬件加速 | CUDA | 11.8 | GPU 加速 |


## 环境依赖

### 前置安装

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| Python | **3.10.x**（严禁 3.11/3.12） | 避免依赖冲突 |
| Git | 最新稳定版 | 安装时勾选添加至系统 PATH |
| NVIDIA 驱动 | 最新适配版 | 官网下载对应型号 |
| CUDA | **11.8** | 适配训练与推理框架 |

### 项目依赖安装

```bash
# 在本项目根目录创建虚拟环境
python -m venv venv

# 激活虚拟环境（Windows）
venv\Scripts\activate

# 安装 PyTorch（CUDA 11.8 专用版本）
pip install torch==2.1.2 torchvision==0.16.2 --index-url https://download.pytorch.org/whl/cu118

# 安装其余依赖
pip install -r requirements.txt

# 安装前端依赖
cd vue-webui
npm install
```

## 核心工具安装

### Kohya_ss 训练工具

```bash
# 克隆训练仓库
git clone https://github.com/bmaltais/kohya_ss.git
cd kohya_ss


## 项目结构

```
bodywash/
├── app.py              # 后端主服务
├── requirements.txt    # Python 依赖
├── settings.json       # 系统配置
├── label_tool.py       # 数据标注工具
├── dataset/            # 训练数据集
│   └── bodywash/       # 沐浴露瓶数据集
├── model/              # 模型文件
│   ├── v1-5-pruned.ckpt        # SD1.5 模型
│   └── v1-inference.yaml       # 模型配置
├── output/             # 训练输出
│   ├── lora_models/    # 训练好的 LoRA 模型
│   └── logs/           # 训练日志
├── static/             # 静态资源
│   └── generated/      # 生成的图片
└── vue-webui/          # 前端项目
    ├── src/            # 前端源代码
    ├── package.json    # 前端依赖
    └── vite.config.js  # 前端构建配置
```

## 部署教程

### 1. 系统配置

编辑 `settings.json` 文件，配置相关路径：

```json
{
  "kohya_path": "D:\\kohya_ss",
  "models_dir": "D:\\train\\model",
  "output_dir": "D:\\train\\output",
  "dataset_base": "D:\\train\\dataset",
  "inference_model": ""
}
```

### 2. 启动服务

#### 后端服务

```bash
# 方式一：直接运行
python app.py

# 方式二：使用启动脚本
双击 "back.bat"
```

#### 前端服务

```bash
# 开发模式
cd vue-webui
npm run dev

# 生产模式构建
cd vue-webui
npm run build

# 方式二：使用启动脚本
双击 "front.bat"    
```

### 3. 访问系统

- 后端 API：`http://localhost:5000`
- 前端界面：`http://localhost:3000`或构建后的静态文件

## 使用教程

### 数据准备

1. **数据集结构**：按照以下结构组织数据

```
dataset/
└── bodywash/
    ├── 001.jpg
    ├── 001.txt
    ├── 002.jpg
    ├── 002.txt
    └── ...
```

2. **标注文件**：每个图片对应一个同名 `.txt` 文件，包含描述性文本

### 模型训练

1. 打开前端界面，进入「训练」标签页
2. 配置训练参数：
   - 选择模型类型（SD1.5 或 SDXL）
   - 填写底座模型路径
   - 配置数据集路径
   - 设置训练参数（学习率、批次大小、轮数等）
3. 点击「开始训练」按钮启动训练
4. 在训练状态页面查看实时进度和日志

### 模型推理

1. 打开前端界面，进入「推理」标签页
2. 选择底座模型和训练好的 LoRA 模型
3. 填写提示词和反向提示词
4. 配置生成参数（分辨率、步数、CFG 等）
5. 点击「生成」按钮获取图片

## 训练参数推荐

### SD1.5 模型

| 参数 | 值 |
|------|----|
| 图像分辨率 | 512×512 |
| Batch Size | 10GB显存→1，12GB+→2 |
| Learning Rate | 1e-4 |
| Epoch | 15~20 轮 |
| 保存频率 | 每 5 轮保存一次 |
| 数据增强 | 开启水平翻转 |
| 优化器 | AdamW8bit |
| 精度 | FP16 半精度 |

### SDXL 模型

| 参数 | 值 |
|------|----|
| 图像分辨率 | 1024×1024 |
| Batch Size | 10GB显存→1，12GB+→2 |
| Learning Rate | 2e-4 |
| Epoch | 12~15 轮 |
| 保存频率 | 每 5 轮保存一次 |
| 数据增强 | 开启水平翻转 |
| 优化器 | AdamW |
| 精度 | FP16 半精度 |

## 常见问题与解决方案

| 问题 | 解决方案 |
|------|----------|
| 仓库克隆失败 | 更换 Gitee 镜像，或开启网络代理 |
| 显存溢出 | Batch Size 降为 1，关闭后台程序，开启 FP16 |
| 生成结构畸变 | 清理不合格图片，LoRA 权重降至 0.7 |
| 过拟合模糊 | 减少 Epoch 轮数，剔除重复同质化图片 |
| 材质质感不佳 | 微调学习率，保证数据集光影材质统一 |
| 模型加载失败 | 检查模型文件路径和完整性 |


## 项目维护

### 依赖更新

```bash
# 更新 Python 依赖
pip install --upgrade -r requirements.txt

# 更新前端依赖
cd vue-webui
npm update
```

### 日志管理

训练日志存储在 `output/logs/` 目录，按时间戳命名。定期清理旧日志以节省存储空间。

