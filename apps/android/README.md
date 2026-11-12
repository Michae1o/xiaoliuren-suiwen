# 安卓端（Kotlin Multiplatform · Kuikly + Compose）

手机上的那一版。和桌面前端**不是**同一套代码：界面、动画、交互各写各的，判据逻辑另有一份 Kotlin 实现
（`kuikly/shared/src/commonMain/kotlin/com/xlr/k/judge/`），语义与桌面那份对齐。

iOS 与它共用同一份 `shared/`（`iosMain` 那一侧），只有壳是 Swift：见
[`kuikly/iosApp/README-IOS.md`](kuikly/iosApp/README-IOS.md)。

## 需要什么

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Android SDK | Platform 34 + Build-Tools（`ANDROID_HOME` 指好即可，不需要 `local.properties`） |
| Gradle | 用仓里的 `gradlew`，不用另装 |

## 编一个安装包

```bash
cd apps/android/kuikly

# Linux / macOS
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk \
  ./gradlew :androidApp:assembleRelease

# Windows
set JAVA_HOME=(upstream)
set ANDROID_HOME=(upstream)
gradlew.bat :androidApp:assembleRelease
```

调试包把 `assembleRelease` 换成 `assembleDebug` 即可。

产物：`kuikly/androidApp/build/outputs/apk/release/androidApp-release.apk`

**签名**：仓里不带任何钥匙。没有 `apps/android/keystore.properties` 时自动用调试签名，
编出来的包装得上、能跑。要用自己的钥匙：

```bash
cp keystore.properties.example keystore.properties   # 放在 apps/android/ 下
# 填 storeFile / storePassword / keyAlias / keyPassword
```

`keystore.properties` 已被 `.gitignore` 挡住，别提交。版本号在 `kuikly/androidApp/build.gradle.kts`
（`versionName` 给显示、`versionCode` 必须递增，装上去才是覆盖升级）。

## 目录

| 路径 | 内容 |
|---|---|
| `kuikly/shared/src/commonMain/kotlin/com/xlr/k/pages/` | 两端共用的状态、模型、协议层 |
| `kuikly/shared/src/androidMain/kotlin/com/xlr/k/pages/` | 安卓那一套页面与组件（`YiPage` / `JiePage` / `Settings*` / `Splash*` …） |
| `kuikly/shared/src/iosMain/kotlin/com/xlr/k/pages/` | iOS 那一套页面与组件（同名同结构，各编各的） |
| `kuikly/shared/src/commonMain/kotlin/com/xlr/k/judge/` | 判据的 Kotlin 版：`JudgeData` / `Blocks` / `Extras` / `PromptData` |
| `kuikly/androidApp/src/main/` | Android 宿主：Manifest、图标、`LlmHost`（存储与网络）、`UpdHost`（app 内更新） |
| `kuikly/iosApp/` | iOS 宿主：Swift 壳、Xcode 工程描述、CI 说明 |
| `scripts/gen-prompt.mjs` | 从 `apps/shared-judgment/src/prompt.js` 生成 `judge/PromptData.kt` |
| `scripts/gen-fog.py` · `gen-dunhuang-fold-fog.py` | 生成雾气贴图（素材已在 `assets/` 里，改素材时才用） |

八套皮肤在 `androidMain` / `iosMain` 的 `Skin.kt` 里（黑白、黑夜、梦幻、宣纸、青瓷、豆沙、敦煌、莫高窟）。

## 需要注意

- **判据是 Kotlin 版**：`judge/` 下那些 `*Data.kt` / `*Cases.kt` 是判据表的移植，改判据请改
  `apps/shared-judgment/`，再照那边的内容同步过来；系统提示词由 `gen-prompt.mjs` 生成，
  别手改 `PromptData.kt`。
- **更新清单留空**：`UpdHost.kt` 里 `MANIFEST_URL` 默认为空 = 关掉 app 内更新；
  要用就填你自己的清单地址（格式写在那个文件里）。
- 包名 `cn.guatai.xiaoliuren`，minSdk 24（Android 7.0），compileSdk / targetSdk 34。
