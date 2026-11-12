# 编译与改造

克隆下来照下面做就行。改代码完全自由：皮肤、布局、判据、提示词，想怎么改都行。

---

## 桌面端（Windows / macOS）

**需要**：Node 18+ · Rust（`cargo`）；Windows 还要 MSVC Build Tools（Win10/11 都自带 WebView2 运行时）。

```bash
npm install                    # 仓库根目录，一次就够

cd apps/desktop-shell
npx --yes @tauri-apps/cli@^2 build
```

| 平台 | 产物 |
|---|---|
| Windows | `src-tauri/target/release/xiaoliuren.exe` —— 单个文件，双击即用 |
| macOS | 同一份源码，在 Mac 上跑 `npx --yes @tauri-apps/cli@^2 build` → `*.app` |

前端**不用**先构建：`apps/desktop-shell/web/index.html` 是入库的加密壳，`tauri build` 直接拿它。

万一双击没反应，八成是系统缺 WebView2 运行时（Win11 自带；Win10 到微软官网装「WebView2 Runtime」，免费）。

---

## 安卓 APK

**需要**：JDK 17 · Android SDK（Platform 34；`ANDROID_HOME` 指好即可，不需要 `local.properties`）。

```bash
cd apps/android/kuikly
./gradlew :androidApp:assembleRelease        # Windows: .\gradlew.bat :androidApp:assembleRelease
```

产物：`androidApp/build/outputs/apk/release/androidApp-release.apk`。

**签名**：仓里不带任何钥匙。没有 `apps/android/keystore.properties` 时自动用调试签名，
编出来的包装得上、能跑。要用自己的钥匙：

```bash
cp apps/android/keystore.properties.example apps/android/keystore.properties
# 填 storeFile / storePassword / keyAlias / keyPassword
```

`keystore.properties` 已被 `.gitignore` 挡住，别提交。更多见 [`apps/android/README.md`](apps/android/README.md)。

---

## iOS

在 Windows 上写代码、GitHub 的 macOS runner 编译：

```bash
gh workflow run ios-sim.yml
gh run list --workflow=ios-sim.yml --limit 3
gh run watch
```

Artifacts 里会有模拟器包（`.app`）和未签名 ipa。公开仓库的 macOS runner 不计费。
怎么装到 iPhone（免费 Apple ID 自签、7 天有效期）见
[`apps/android/kuikly/iosApp/README-IOS.md`](apps/android/kuikly/iosApp/README-IOS.md)。

---

## 我要改东西

| 改什么 | 改哪儿 |
|---|---|
| 界面 / 交互 / 动画（桌面） | `apps/desktop-web/src/` |
| 皮肤 | `apps/desktop-web/src/skins/`（铜古那套在这里；其余八套的颜色表在 `src/data/skinsCore.js` 与 `src/data/skinExtra.json`） |
| 界面 / 交互（安卓、iOS） | `apps/android/kuikly/shared/src/androidMain|iosMain/kotlin/com/xlr/k/pages/`（两端各一套，不共用组件与样式） |
| 起卦 / 排盘 / 六宫 / 断辞 | `apps/shared-judgment/`（桌面 import 它；安卓那份是 Kotlin 移植，在 `.../judge/`） |
| MCP 工具表与出口 | `apps/shared-judgment/src/mcp-tools.js`；桌面设置页的「MCP 接入」是它的一个出口 |
| 模型提示词 | `apps/shared-judgment/src/prompt.js`（安卓是 `judge/PromptData.kt`，由脚本生成） |
| 判据语料（九份体系文档） | `apps/shared-judgment/corpus/`，改完跑 `node tools/gen-corpus.mjs` 重新打包成 `src/corpus.js` |
| 版本号 | `apps/desktop-shell/src-tauri/tauri.conf.json` + 同目录 `Cargo.toml`；安卓在 `apps/android/kuikly/androidApp/build.gradle.kts` |
| 图标 | `apps/desktop-shell/src-tauri/icons/`（Windows `.ico` / macOS `.icns`）、`apps/android/kuikly/androidApp/src/main/res/` |

### 改完前端怎么重新进产物

**桌面**（五步，按顺序）：

```bash
cd apps/desktop-web
npm run build                     # → dist/index.html（明文，别提交）
npm run build:shell               # → shell-dist/shell.js（外壳模块）

cd ../.. && node tools/assemble-desktop.mjs --append apps/desktop-web/shell-dist/shell.js
                                  # → apps/desktop-web/dist/新版-现代.html
node tools/make-doc-entry.mjs     # → apps/desktop-web/doc-entry.html（Vite 入口）

cd apps/desktop-web
npx vite build --config vite.doc.config.mjs   # → 成品明文单页（单文件，素材全内联）
python build/protect.py                       # → dist/新版-现代-加密.html
python build/prep_tauri_web.py                # → apps/desktop-shell/web/index.html（入库的那份壳）

cd ../desktop-shell && npx --yes @tauri-apps/cli@^2 build   # → exe / .app
```

顺序别换：`make-doc-entry` 必须在 `vite build` 之前（它生成入口），`protect` 必须在 `prep` 之前。

**安卓**：改完 `shared/` 或 `androidApp/` 里的代码，照上面那条 `gradlew` 重编就是新的。
系统提示词是**生成**的（`node apps/android/scripts/gen-prompt.mjs` ← `apps/shared-judgment/src/prompt.js`），别手改 `PromptData.kt`。

**iOS**：改完 `shared/` 或 `iosApp/iosApp/*.swift`，推上去跑一次 workflow 就是新的。

---

## 包里有什么

| 目录 | 内容 |
|---|---|
| `apps/desktop-web/` | 桌面前端（React 19 + Vite 6 + Tailwind 4） |
| `apps/shared-judgment/` | 起卦 / 排盘 / 六宫 / 断辞 / 提示词 / MCP 内核 / 九份语料 |
| `apps/android/` | 安卓与 iOS 整链（Kotlin Multiplatform + Kuikly；`kuikly/` 是 Gradle 工程） |
| `apps/desktop-shell/` | Tauri 2 壳；`web/` 是入库的加密壳，`pkg/` 是 Windows 启动器 |
| `apps/desktop-web/build/` | 加密脚本（`protect.py` / `prep_tauri_web.py`）；适配层 `shim.js` 在 `desktop-shell/web/` |
| `tools/` | 组装器、文档入口生成、语料打包 |
| `mac/` | macOS 构建脚本与说明 |
| `docs/images/` | README 里的截图 |

不含：网站、密钥、签名物料、构建产物、早期版本快照。

---

## 几个提醒（都不挡你，只是省踩坑）

1. **判据**桌面那份在 `apps/shared-judgment/`，安卓那份是它的 Kotlin 移植
   （`apps/android/kuikly/shared/src/commonMain/kotlin/com/xlr/k/judge/`），两边语义要齐，改一边回头对另一边。
2. **语料与判据是两层**：`corpus/*.md` 是九份体系文档（人读），`tools/gen-corpus.mjs` 把它们打包成
   `src/corpus.js`（代码读）。改了 md 一定重跑一次，否则界面里还是旧的。
3. **加密链**（`protect.py` / `prep_tauri_web.py` / `shim.js`）负责把前端打进壳里，改它们的输入输出格式要自己兜住
   （只对桌面端有意义，安卓和 iOS 是原生渲染）。
4. `dist/` · `target/` · `apps/android/kuikly/**/build/` · `local.properties` 都是生成物或本机配置，`.gitignore` 已经挡住。
5. **样式**：`apps/desktop-web/src/styles/legacy.css` 是整份历史样式，包在 `@layer components` 里；
   组装器找 `/*@xlrblk:N@*/` 分段标记按段插入，没有标记就整份放进 `<head>`。
