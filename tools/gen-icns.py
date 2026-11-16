# -*- coding: utf-8 -*-
"""从 1024x1024.png 生成 macOS 的 icon.icns（PNG-in-ICNS 容器，10.7+ 原生支持）。"""
import io
from pathlib import Path
import struct

from PIL import Image

ICONS = Path(__file__).resolve().parents[1] / 'apps/desktop-shell/src-tauri/icons'
SRC = ICONS / '1024x1024.png'
DST = ICONS / 'icon.icns'

# macOS 认的 PNG 型 entry：(类型码, 该尺寸边长)
ENTRIES = [
    (b'icp4', 16),    # 16pt @1x
    (b'icp5', 32),    # 32pt @1x
    (b'icp6', 64),    # 64pt
    (b'ic07', 128),   # 128pt
    (b'ic08', 256),   # 256pt
    (b'ic09', 512),   # 512pt
    (b'ic10', 1024),  # 512pt @2x
    (b'ic11', 32),    # 16pt @2x
    (b'ic12', 64),    # 32pt @2x
    (b'ic13', 256),   # 128pt @2x
    (b'ic14', 512),   # 256pt @2x
]

src = Image.open(SRC).convert('RGBA')
parts = []
for code, size in ENTRIES:
    im = src if size == 1024 else src.resize((size, size), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, format='PNG', optimize=True)
    data = buf.getvalue()
    parts.append(code + struct.pack('>I', len(data) + 8) + data)

body = b''.join(parts)
icns = b'icns' + struct.pack('>I', len(body) + 8) + body
open(DST, 'wb').write(icns)

print('写出 %s' % DST)
print('  %d 个尺寸 / %d 字节' % (len(parts), len(icns)))

# 自校验：能读回来吗
back = Image.open(DST)
print('  回读：%s %s  OK' % (back.format, back.size))
