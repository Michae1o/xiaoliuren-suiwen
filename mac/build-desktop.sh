#!/usr/bin/env bash
# 与 Windows 共用 desktop-web + shared-judgment + desktop-shell，始终从源码重建。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MODE="${1:---debug}"
case "$MODE" in --debug|--release|--frontend-only) ;; *) echo '用法：bash mac/build-desktop.sh [--debug|--release|--frontend-only]' >&2; exit 2 ;; esac
PYTHON="${PYTHON:-python3}"
command -v node >/dev/null
command -v npm >/dev/null
command -v "$PYTHON" >/dev/null
[ "$(node -p 'Number(process.versions.node.split(".")[0])')" -ge 22 ] || { echo '请安装 Node 22 或更新 LTS'; exit 1; }
"$PYTHON" -c 'from PIL import Image' || { echo '请先安装 Pillow：python3 -m pip install Pillow'; exit 1; }
if [ "$MODE" != --frontend-only ]; then
  [ "$(uname -s)" = Darwin ] || { echo '原生 .app 必须在 macOS 构建；Linux 可使用 --frontend-only'; exit 1; }
  xcode-select -p >/dev/null
  if ! command -v cargo >/dev/null && [ -f "$HOME/.cargo/env" ]; then . "$HOME/.cargo/env"; fi
  command -v cargo >/dev/null
fi
bash "$ROOT/tools/build-desktop-frontend.sh"
echo '3/5 从当前 Windows 同源 PNG 生成 Mac 图标（不覆盖 ICO）'
"$PYTHON" tools/gen-icns.py
if [ "$MODE" = --frontend-only ]; then echo '前端与图标已生成；未编译或运行 Mac App'; exit 0; fi
echo '4/5 构建共享 Rust 外壳'
cd apps/desktop-shell
# 默认调试 App，先在 Mac 验证后再显式 --release；保持现有应用 identifier/记录目录。
if [ "$MODE" = --debug ]; then
  npx --yes @tauri-apps/cli@2 build --debug --config src-tauri/tauri.macos.conf.json
  PROFILE=debug
else
  npx --yes @tauri-apps/cli@2 build --config src-tauri/tauri.macos.conf.json
  PROFILE=release
fi
APP="$ROOT/apps/desktop-shell/src-tauri/target/$PROFILE/bundle/macos/遂问.app"
[ -d "$APP" ] || { echo "没有找到预期产物：$APP" >&2; exit 1; }
echo "5/5 已生成：$APP"
echo '这不代表已完成签名、公证或实机验收。请先打开检查，正式发布另走签名流程。'
