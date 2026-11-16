# 遂问 Linux 桌面移植

此入口与 Windows / Mac 共用 `apps/desktop-web` 界面、`apps/shared-judgment` 判据和 `apps/desktop-shell` Rust 外壳。没有复制另一份起卦内核，也不再直接把旧的入库 HTML 当作最新界面。

## 当前状态

- 已提供从源代码重建前端、调试可执行程序和 `.deb` / `.AppImage` 的构建入口。
- 默认生成调试程序，不默认发布。界面、模型请求、系统集成及数据保留仍需 Linux 桌面实机验收。
- 没有 Linux 更新清单或安装器更新通道；共用更新层阻止误装 Windows 更新包。发布渠道就绪前使用人工安装，不把 Windows ZIP 当 Linux 包。
- 应用 identifier 保持 `cn.luoshen.xiaoliuren`。请勿为了移植修改它或清理用户资料目录。
- 默认使用当前主机架构，未宣称跨架构包或全发行版兼容。应在计划支持的最旧发行版构建并在目标环境验证。

## 环境

Node 22 或更新 LTS、npm、Python 3、稳定版 Rust/Cargo、C/C++ 构建工具、pkg-config、GTK 3 和 WebKitGTK 4.1 开发依赖；桌面运行需图形会话和 `xdg-utils`。建议安装中文字体以免缺字。

Debian/Ubuntu 示例（由维护者按系统版本确认后安装）：

```bash
sudo apt install build-essential pkg-config libgtk-3-dev libwebkit2gtk-4.1-dev \
  libssl-dev librsvg2-dev libayatana-appindicator3-dev patchelf file xdg-utils fonts-noto-cjk
```

Rust 使用官方稳定工具链；构建脚本不会擅自改系统包、npm 源或全局环境。系统依赖参考 [Tauri 2 官方前置要求](https://v2.tauri.app/start/prerequisites/)。

## 构建

在项目根目录运行：

```bash
# 先重建与 Windows 同源的前端，不要求原生 Linux 开发库
bash linux/build-desktop.sh --frontend-only

# 默认：调试程序，不生成分发包
bash linux/build-desktop.sh --debug
./apps/desktop-shell/src-tauri/target/debug/xiaoliuren

```

先在桌面打开调试程序确认效果。用户确认后，按仓库规则更新桌面版本，再执行：

```bash
bash linux/build-desktop.sh --release
```

原生包在 `apps/desktop-shell/src-tauri/target/release/bundle/`；打包本身不代表实机通过；AppImage 的运行依赖也需目标系统验证。

## 验收清单

1. 开屏、星图、主题、透明/模糊、WebGL 动画、中文字体及窗口缩放。
2. 起卦、排盘、断辞与同版本 Windows 的输入输出一致；不修改判据。
3. 模型设置、请求/流式输出、取消、错误提示；不导出用户密钥。
4. 卦例保存、搜索、导入导出、重启数据保留；系统默认浏览器和文件管理器能打开。
5. Linux 更新提示不得指向 Windows 包，不退出去运行 Windows 安装脚本。
6. 分别记录桌面环境、显示协议、发行版与架构；未测环境不写通过。

当前导出命令优先使用 `$HOME/Downloads`，不存在时使用 `$HOME`。这是共享旧行为，不宣称已支持所有 XDG 本地化下载目录。
