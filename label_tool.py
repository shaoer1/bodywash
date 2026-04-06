#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LoRA 训练打标工具
- 根据图片文件名自动生成英文 caption
- 图片重命名为 001.png / 002.png ...
- caption 保存为 001.txt / 002.txt ...
"""

import os
import re
import sys
import shutil
from pathlib import Path

# ── 中文关键词 → 英文 tag 映射表 ──────────────────────────────────────────
ZH2EN = {
    "旋盖式": "screw cap",
    "压泵式": "pump dispenser",
    "翻盖式": "flip cap",
    "喷雾式": "spray bottle",
    "挤压式": "squeeze bottle",
    "沐浴露": "body wash",
    "洗发水": "shampoo",
    "护发素": "conditioner",
    "洗手液": "hand soap",
    "乳液": "lotion",
    "面霜": "face cream",
    "精华": "serum",
    "洁面": "facial cleanser",
    "防晒": "sunscreen",
    "香水": "perfume",
    "长方形": "rectangular",
    "圆形": "round",
    "椭圆形": "oval",
    "方形": "square",
    "圆柱形": "cylindrical",
    "修长": "tall and slender",
    "矮胖": "short and wide",
    "扁平": "flat and wide",
    "白色": "white",
    "黑色": "black",
    "透明": "transparent",
    "红色": "red",
    "蓝色": "blue",
    "绿色": "green",
    "金色": "gold",
    "银色": "silver",
    "粉色": "pink",
    "紫色": "purple",
    "橙色": "orange",
    "黄色": "yellow",
    "灰色": "gray",
    "米色": "beige",
    "棕色": "brown",
    "背景颜色": "",
    "纯色背景": "solid color background",
    "白色背景": "white background",
    "渐变背景": "gradient background",
    "黑色背景": "black background",
    "透明背景": "transparent background",
    "视觉造型": "",
    "产品类型": "",
    "背景": "background",
    "画面特征": "",
    "光泽": "glossy",
    "哑光": "matte",
    "磨砂": "frosted",
    "透明瓶身": "clear bottle body",
    "金属质感": "metallic texture",
    "简约": "minimalist",
    "高端": "premium",
    "清新": "fresh",
    "商业摄影": "product photography",
    "正面": "front view",
    "侧面": "side view",
    "三四视角": "three-quarter view",
    "特写": "close-up",
    "整体": "full product",
    "测试中文": "test chinese",
    "新测试": "new test",
    "测试1": "test1",
    "测试2": "test2",
    "测试3": "test3"
}

IMAGE_EXTS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}


def parse_filename_to_tags(filename: str) -> list[str]:
    """从文件名解析出英文 tag 列表"""
    stem = Path(filename).stem
    # 去掉常见前缀如 jimeng-2026-03-30-1039-
    stem = re.sub(r'^jimeng-\d{4}-\d{2}-\d{2}-\d{4}-', '', stem)
    stem = re.sub(r'^[a-zA-Z0-9_-]+-\d{6,}-', '', stem)  # 其他前缀

    # 预处理：把"背景颜色 XX色"合并为"XX色背景"
    COLOR_MAP = {
        '白色': 'white', '黑色': 'black', '透明': 'transparent',
        '红色': 'red', '蓝色': 'blue', '绿色': 'green', '金色': 'gold',
        '银色': 'silver', '粉色': 'pink', '紫色': 'purple', '灰色': 'gray',
        '米色': 'beige', '棕色': 'brown', '橙色': 'orange', '黄色': 'yellow',
    }
    for zh_color, en_color in COLOR_MAP.items():
        stem = re.sub(f'背景颜色\\s*{zh_color}', f'{en_color} background', stem)
        stem = re.sub(f'背景\\s*{zh_color}', f'{en_color} background', stem)

    # 按中文逗号、英文逗号分割
    parts = re.split(r'[，,]+', stem)

    tags = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        # 先尝试完整匹配
        matched = False
        for zh, en in ZH2EN.items():
            if zh in part:
                if en:  # 跳过空字符串映射
                    remaining = part.replace(zh, '').strip()
                    tags.append(en)
                    # 处理剩余部分
                    if remaining:
                        sub = parse_filename_to_tags(remaining)
                        tags.extend(sub)
                matched = True
                break
        if not matched:
            # 保留有意义的纯英文/数字标签
            if re.match(r'^[a-zA-Z0-9 _-]+$', part) and len(part) > 1:
                tags.append(part.lower())
    # 去重保序
    seen = set()
    result = []
    for t in tags:
        t = t.strip()
        if t and t not in seen:
            seen.add(t)
            result.append(t)
    return result


def build_caption(tags: list[str], subject: str = '') -> str:
    """组合成 LoRA 训练用的 caption"""
    if subject:
        core = [subject] + tags
    else:
        core = tags
    return ', '.join(core)


def process_folder(folder: str, subject: str = '', dry_run: bool = False):
    """处理一个文件夹"""
    folder = Path(folder)
    if not folder.exists():
        print(f'[ERROR] 目录不存在: {folder}')
        return

    images = sorted([
        f for f in folder.iterdir()
        if f.is_file() and f.suffix.lower() in IMAGE_EXTS
    ])

    if not images:
        print(f'[WARN] 未找到图片文件: {folder}')
        return

    print(f'\n找到 {len(images)} 张图片，开始处理...\n')

    for idx, img_path in enumerate(images, start=1):
        new_name = f'{idx:03d}{img_path.suffix.lower()}'
        txt_name = f'{idx:03d}.txt'
        new_img = folder / new_name
        new_txt = folder / txt_name

        # 生成 caption
        tags = parse_filename_to_tags(img_path.name)
        caption = build_caption(tags, subject)

        print(f'[{idx:03d}] {img_path.name}')
        print(f'      → 图片: {new_name}')
        print(f'      → 标注: {caption}')
        print()

        if not dry_run:
            # 重命名图片
            if img_path.name != new_name:
                shutil.move(str(img_path), str(new_img))
            # 写 caption
            with open(new_txt, 'w', encoding='utf-8') as f:
                f.write(caption)

    if dry_run:
        print()
    else:
        print(f'完成！已处理 {len(images)} 张图片')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='LoRA 训练打标工具')
    parser.add_argument('folder', help='图片所在文件夹路径')
    parser.add_argument('--subject', default='', help='主体词，如 product bottle (加在最前面)')
    parser.add_argument('--run', action='store_true', help='实际执行（默认为预览模式）')
    args = parser.parse_args()

    process_folder(args.folder, subject=args.subject, dry_run=not args.run)
