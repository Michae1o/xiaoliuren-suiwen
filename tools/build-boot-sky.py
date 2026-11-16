# -*- coding: utf-8 -*-
"""生成加载层要用的「开屏星图」数据包：9 套 × (轨道数据 + 49 张贴图 base64)。

  · 他要的是**还在动的那片星图**（各宿沿椭圆运行、星芒脉冲、北斗浮动），
    合成图只能整体静止 ⇒ 必须把 React 里那套逐帧逻辑搬到壳里。
  · 他也指了尺寸：开屏宽屏是 left:-8% / top:50% / min(66vw,94vh) / opacity .55，
    不是窄屏那套 120vw 铺满 ⇒ 加载层照宽屏那套摆。

产物：apps/desktop-web/build/boot-sky.json
  每套：cx cy a b tilt bx0 by0 bx1 by1 nx ny nw nh ids g tiles[49]
  tiles 顺序：s01..s24（0-23）、f01..f24（24-47）、north（48）
"""
import base64
import io
import json
import os
import re

from pathlib import Path
APP = Path(__file__).resolve().parents[1] / "apps/desktop-web"
SRC = APP / "src/assets/splash-orbit"
JS = APP / "src/data/splashOrbitData.js"
OUT = APP / "build/boot-sky.json"

s = io.open(JS, encoding="utf-8").read()
starts = [m.start() for m in re.finditer(r"\n  \w+: \{\n\s+key: '", s)] + [len(s)]


def num(block, k):
    return float(re.search(r"\b%s: (-?[\d.]+)" % k, block).group(1))


sets = {}
total_bytes = 0
for i in range(len(starts) - 1):
    block = s[starts[i]:starts[i + 1]]
    key = re.search(r"key: '(\w+)'", block).group(1)
    g = [float(x) for x in re.findall(r"-?\d+\.?\d*", re.search(r"g: \[(.*?)\]", block, re.S).group(1))]
    ids = [int(x) for x in re.findall(r"\d+", re.search(r"ids: \[(.*?)\]", block, re.S).group(1))]

    tiles = []
    d = os.path.join(SRC, key)
    for name in (["s%02d" % k for k in range(1, 25)] + ["f%02d" % k for k in range(1, 25)] + ["north"]):
        p = os.path.join(d, name + ".webp")
        b = io.open(p, "rb").read()
        total_bytes += len(b)
        tiles.append("data:image/webp;base64," + base64.b64encode(b).decode())

    sets[key] = {
        "key": key,
        "cx": num(block, "cx"), "cy": num(block, "cy"),
        "a": num(block, "a"), "b": num(block, "b"),
        "tilt": num(block, "tiltDeg"),
        "bx0": num(block, "bx0"), "by0": num(block, "by0"),
        "bx1": num(block, "bx1"), "by1": num(block, "by1"),
        "nx": num(block, "nx"), "ny": num(block, "ny"),
        "nw": num(block, "nw"), "nh": num(block, "nh"),
        "ids": ids,
        "g": g,
        "tiles": tiles,
    }

io.open(OUT, "w", encoding="utf-8").write(json.dumps(sets, separators=(",", ":")))
print("写出 %s" % OUT)
print("套数 %d · 贴图 %d 张 · 源图 %.2f MB · JSON %.2f MB"
      % (len(sets), len(sets) * 49, total_bytes / 1048576.0, os.path.getsize(OUT) / 1048576.0))
for k, v in sets.items():
    print("  %-6s 宿 %d · 贴图 %d 张" % (k, len(v["g"]) // 7, len(v["tiles"])))
