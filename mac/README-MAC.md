# 遂问 · macOS 桌面移植

Mac 现在从 **Windows 当前同一份源码** 构建，不再直接打包可能过时的入库 HTML：
- 界面、皮肤、星图、雾气：`apps/desktop-web`；
- 起卦、排盘、断辞、提示词：`apps/shared-judgment`（不再复制一份 Mac 判据）；
- 原生网络、导出、配置等：`apps/desktop-shell/src-tauri`；
- 图标：Windows 同源 `icons/1024x1024.png` → `icon.icns`，不改 `icon.ico`。

## 开发与验证

Mac 安装 Node 22+、Python 3 + Pillow、Rust、Xcode 命令行工具。Pillow 可安装在 Python 虚拟环境内；脚本支持 `PYTHON=/path/to/python`。

```bash
bash mac/build-desktop.sh --debug
```

也可双击 `mac/build-desktop.command`。默认生成调试 App，必须先在 Mac 打开验证。
脚本依次执行与 Windows 相同的 shell 构建、组装、Vite、`protect.py`、`prep_tauri_web.py`，
再生成 ICNS、编译同一套 Rust 代码；明确设置仓内输出路径，不会写旧 E 盘目录。
首次安装依赖需要网络；有依赖但锁文件变更时先在根目录重新运行 `npm ci`。

调试产物：`apps/desktop-shell/src-tauri/target/debug/bundle/macos/遂问.app`。
用户确认后才运行 `--release`，正式包前按主仓规则 bump 桌面版本；产物在 `target/release/bundle/macos`。
脚本按当前机器架构构建，不是通用 Universal 包。签名和公证没有自动配置，不承诺可直接分发。

Linux 只能检查共享前端及生成图标：
```bash
bash mac/build-desktop.sh --frontend-only
```
这 **不等于** Mac 原生编译或 WKWebView 实机验收。

## 兼容与数据

应用 identifier 沿用 `cn.luoshen.xiaoliuren`，不主动清除已有 Mac 卦例、配置或缓存。
基础配置保留原来的最低系统字段，但现代 CSS/WebGL 效果仍需目标 macOS/WKWebView 实测；
不能仅凭配置字段声称旧 macOS 已受支持。

Windows 的整包自动更新是 EXE/BAT 换装协议，**Mac 禁止调用**。Mac 设置中会提示使用 Mac 安装包，
不会下载 Windows 更新或执行换装。尚未配置专用 Mac 更新分发，不伪造更新源。

## 实机检查清单

打开调试 App，检查开屏/图标/皮肤/雾气、起卦与断辞、模型请求与接着问、记录保存与重启、
导出与 Finder 定位、外部链接、文本选择复制，以及更新入口不会下载 Windows 包。
重点覆盖 Apple Silicon / Intel 与所需系统版本；这些测试尚需 Mac 执行。

