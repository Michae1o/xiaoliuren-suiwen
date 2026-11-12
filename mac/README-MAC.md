# 在 Mac 上编桌面版（Tauri 2）

桌面版是 **Tauri 2** 工程（Rust 外壳 + WebView 前端）。前端已经**加密好并入库**
（`apps/desktop-shell/web/index.html`），判据看不到明文——这是设计如此，不需要、也不要试图解密。

你只需要在这台 Mac 上把它编成 `.app`。

---

## 一、先装这三样

| 需要 | 怎么装 | 检查 |
|---|---|---|
| **Xcode 命令行工具** | `xcode-select --install`（弹窗点安装，约 5 分钟） | `xcode-select -p` 有输出 |
| **Rust** | `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \| sh`，装完 `source "$HOME/.cargo/env"` | `rustc --version` |
| **Node 18+** | 官网装包，或 `brew install node` | `node --version` |

⚠ 只要**命令行工具**，不需要装完整的 Xcode（那个 10 多 GB 用不上）。
⚠ Apple Silicon（M 系列）和 Intel 都行，脚本会自动按当前架构编。

---

## 二、编

```bash
cd <你 clone 下来的仓库目录>
bash mac/build-desktop.sh
```

或者双击 `mac/build-desktop.command`（第一次可能要右键 → 打开，因为 Gatekeeper 会拦未签名脚本）。

脚本干的事：

1. 检查上面三样齐不齐，缺哪个直接告诉你装什么
2. `npm install`（仓库根目录，只有第一次慢）
3. `cd apps/desktop-shell` → `npx @tauri-apps/cli build`
4. 报告产物位置

第一次编译 Rust 依赖约 **3–8 分钟**，之后改前端只重编外壳、几十秒。

---

## 三、产物

```
apps/desktop-shell/src-tauri/target/release/bundle/macos/遂问.app
```

双击就能开。想发给别人，压缩这个 `.app` 即可；对方第一次打开要右键 → 打开（未签名应用的正常提示，不是坏了）。

---

## 四、图标已经准备好了

`apps/desktop-shell/src-tauri/icons/icon.icns` 已入库（11 个尺寸，含 1024）。

**不要**再跑 `npx tauri icon` —— 那会重新生成一整套并覆盖现有的 `icon.ico`，把 Windows 那边的图标弄坏。

要改图标的话：换 `icons/1024x1024.png`，再重新生成 `.icns`（在 Mac 上用 `iconutil`，
或按 `tools/gen-icns.py` 的做法生成）。改完 `tauri.macos.conf.json` 里 `icon` 那一项不用动。

---

## 五、编译不过怎么办

| 症状 | 原因 | 处理 |
|---|---|---|
| `xcrun: error: invalid active developer path` | Xcode 命令行工具没装好 | `xcode-select --install`，装完再跑一次 |
| `error: linker 'cc' not found` | 同上 | 同上 |
| `npm install` 卡住或超时 | 网络 | 换国内镜像：`npm config set registry https://registry.npmmirror.com` |
| `cargo` 下载依赖极慢 | 没配镜像 | 在 `~/.cargo/config.toml` 里加 `[source.crates-io] replace-with = 'ustc'` 一节 |
| 编译过了但双击没反应 | 前端壳坏了 | 终端里直接跑 `.../遂问.app/Contents/MacOS/*`，看输出 |
| `"遂问.app" 已损坏，无法打开` | 未签名 + 隔离属性 | `xattr -cr "路径/遂问.app"` |
| 白屏 | 前端产物没进包 | 确认 `apps/desktop-shell/web/index.html` 存在（约 7 MB） |

---

## 六、手机端不在这条链里

| 端 | 怎么出包 |
|---|---|
| 安卓 | `apps/android/kuikly` 的 Gradle 工程，见 `apps/android/README.md` |
| iOS | 云构建（GitHub 的 macOS runner），见 `apps/android/kuikly/iosApp/README-IOS.md` |

---

## 七、想改界面

前端源码在 `apps/desktop-web/`（React 19 + Vite 6 + Tailwind 4）。改完要按 `BUILD.md` 那五步重新上壳，
再 `tauri build` —— 直接改源码不会进 `.app`，因为 App 里跑的是加密壳。
