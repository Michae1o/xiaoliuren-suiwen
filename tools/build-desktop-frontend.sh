#!/usr/bin/env bash
# macOS/Linux 共用 Windows 同源前端构建链；不复制判据、不改变加密格式。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PYTHON="${PYTHON:-python3}"
command -v node >/dev/null
command -v npm >/dev/null
command -v "$PYTHON" >/dev/null
[ "$(node -p 'Number(process.versions.node.split(".")[0])')" -ge 22 ] || { echo '请安装 Node 22 或更新 LTS' >&2; exit 1; }
if [ ! -x node_modules/.bin/vite ]; then npm ci; fi
export XLR_WEB_SHELL="$ROOT/apps/desktop-shell/web"
echo '构建共享桌面前端与判据'
(cd apps/desktop-web && npm run build:shell)
node tools/assemble-desktop.mjs --append "$ROOT/apps/desktop-web/shell-dist/shell.js"
node tools/make-doc-entry.mjs
(cd apps/desktop-web && npm exec -- vite build --config vite.doc.config.mjs)
echo '生成加密 Tauri 前端'
(cd apps/desktop-web && "$PYTHON" build/protect.py && "$PYTHON" build/prep_tauri_web.py)
echo '共享前端已生成；尚未进行原生编译或界面验收。'
