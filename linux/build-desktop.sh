#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MODE="${1:---debug}"
case "$MODE" in --debug|--release|--frontend-only) ;; *) echo '用法：bash linux/build-desktop.sh [--debug|--release|--frontend-only]' >&2; exit 2 ;; esac
if [ "$MODE" != --frontend-only ]; then
  [ "$(uname -s)" = Linux ] || { echo 'Linux 原生程序必须在 Linux 构建。' >&2; exit 1; }
  if ! command -v cargo >/dev/null && [ -f "$HOME/.cargo/env" ]; then . "$HOME/.cargo/env"; fi
  command -v cargo >/dev/null || { echo '缺少 Rust/Cargo；请先安装稳定版 Rust 工具链。' >&2; exit 1; }
  command -v pkg-config >/dev/null || { echo '缺少 pkg-config；参见 linux/README-LINUX.md。' >&2; exit 1; }
  pkg-config --exists gtk+-3.0 webkit2gtk-4.1 || { echo '缺少 GTK 3 / WebKitGTK 4.1 开发依赖；参见 linux/README-LINUX.md。' >&2; exit 1; }
fi
bash tools/build-desktop-frontend.sh
if [ "$MODE" = --frontend-only ]; then exit 0; fi
cd apps/desktop-shell
if [ "$MODE" = --debug ]; then
  # 调试阶段不生成分发包；先实际打开验证，再显式请求 release。
  npx --yes @tauri-apps/cli@2 build --debug --no-bundle --config src-tauri/tauri.linux.conf.json
  BIN="$ROOT/apps/desktop-shell/src-tauri/target/debug/xiaoliuren"
  [ -x "$BIN" ] || { echo "没有找到调试程序：$BIN" >&2; exit 1; }
  echo "调试程序：$BIN"
  echo '请在 Linux 桌面会话中打开测试；这不是已验收的发布包。'
else
  npx --yes @tauri-apps/cli@2 build --config src-tauri/tauri.linux.conf.json
  echo "构建产物：$ROOT/apps/desktop-shell/src-tauri/target/release/bundle/"
  echo '正式交付前请按仓库规则更新版本；验收后自行归档安装包。'
fi
