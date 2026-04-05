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
    # 产品类型
    '旋盖式': 'screw cap',
    '旋盖': 'screw cap',
    '泵头式': 'pump dispenser',
    '泵头': 'pump dispenser',
    '压泵式': 'pump dispenser',
    '压泵': 'pump dispenser',
    '翻盖式': 'flip cap',
    '翻盖': 'flip cap',
    '喷雾式': 'spray nozzle',
    '喷雾': 'spray nozzle',
    # 产品品类
    '沐浴露': 'body wash',
    # 比例
    '修长': 'tall and slender',
    '矮胖': 'short and wide',
    '均匀': 'balanced proportion',
    # 颜色
    '白色': 'white',
    '黑色': 'black',
    '黄色': 'yellow',
    '红色': 'red',
    '蓝色': 'blue',
}

IMAGE_EXTS = {'.png', '.jpg', '.jpeg', '.webp', '.bmp'}


def parse_filename_to_tags(filename: str) -> list[str]:
    """从文件名解析出英文 tag 列表"""
    stem = Path(filename).stem
    # 去掉常见前缀如 jimeng-2026-03-30-1039-
    stem = re.sub(r'^jimeng-\d{4}-\d{2}-\d{2}-\d{4}-', '', stem)
    stem = re.sub(r'^[a-zA-Z0-9_-]+-\d{6,}-', '', stem)  # 其他前缀

    # 按分隔符拆分（中英文逗号、顿号、下划线、空格）
    parts = re.split(r'[，,、_\s]+', stem)

    tags = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        
        # 按关键词长度排序，优先匹配较长的关键词
        sorted_items = sorted(ZH2EN.items(), key=lambda x: len(x[0]), reverse=True)
        
        # 尝试匹配所有关键词
        remaining = part
        for zh, en in sorted_items:
            if zh in remaining:
                if en:  # 跳过空字符串映射
                    tags.append(en)
                    remaining = remaining.replace(zh, '').strip()
    
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


def build_caption_with_trigger(tags: list[str], trigger_word: str = '') -> str:
    """组合成 LoRA 训练用的 caption，触发词放在第一位"""
    if trigger_word:
        core = [trigger_word] + tags
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
        print('[预览模式] 未做任何修改，加 --run 参数执行实际操作')
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
