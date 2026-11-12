# 在 Windows 上编 iOS（Kuikly + 云构建）

iPhone 那一版与安卓是**同一份 Kotlin**（`shared/`），只有壳是 Swift。
在 Windows 上写代码，编译交给 GitHub 的 macOS runner —— 本机不需要 Mac、不需要 Xcode。

---

## 一、这套东西怎么分工

| 在哪 | 干什么 |
|---|---|
| `shared/src/commonMain/` + `shared/src/iosMain/`（本机，Windows 也能改） | 界面与逻辑，和安卓共用 `commonMain` |
| `iosApp/iosApp/*.swift` / `*.m` | iOS 壳：渲染容器、图片适配器、路由适配器、日志适配器 |
| `iosApp/project.yml` | Xcode 工程描述。`.xcodeproj` 不手搓、不入库，现场用 XcodeGen 生成 |
| `iosApp/Podfile` | 只装 Kuikly 的 iOS 渲染器（`OpenKuiklyIOSRender`） |
| `.github/workflows/ios-sim.yml` | 云上编译：模拟器包 + 未签名 ipa |

**共享代码怎么进 App**：gradle 编出 `shared.framework` → 拷到 `iosApp/Frameworks/` →
XcodeGen 生成的工程把它静态链进主二进制。不走 CocoaPods 管业务代码，所以 Windows 上的安卓构建不受影响。

---

## 二、本地能验证什么

```powershell
cd apps\android\kuikly
.\gradlew.bat :shared:tasks --all | Select-String linkDebugFramework
```

能列出 `linkDebugFrameworkIosArm64` / `linkDebugFrameworkIosSimulatorArm64` 就说明 iOS target 配好了。
⚠ 真正编译必须在 macOS 上（Kotlin/Native 的 Apple 目标不支持交叉编译），Windows 只能做配置检查。

---

## 三、云端编译怎么跑

推代码到有 workflow 的仓库，或手动触发：

```powershell
gh workflow run ios-sim.yml
gh run list --workflow=ios-sim.yml --limit 3
gh run watch
```

跑完在 Actions 页面的 **Artifacts** 下载：

| 产物 | 是什么 |
|---|---|
| `iosApp-simulator.zip` | 能装进 iOS 模拟器的 `.app`（未签名） |
| `iosApp-unsigned-ipa` | 实机用未签名 `.ipa`（约 20 MB，要自签才能装，见第五节） |

**成本**：公开仓库 → macOS runner 免费不计费；私有仓库 → 每月 2000 分钟额度，macOS 按 ×10 计
⇒ 实跑约 200 分钟/月（一次完整构建含首次依赖下载约 20–40 分钟，有缓存后约 10 分钟）。
所以试错放在公开仓，别在私有仓上反复跑。

---

## 四、共享层能力对接状况

共享层通过 `Platform` 这个注入点跟宿主通信。iOS 侧由
`shared/src/iosMain/kotlin/com/xlr/k/PlatformIos*.kt` 全部写好，入口是 Swift 里的
`PlatformIos.shared.install()`。

| 能力 | iOS 实现 | 说明 |
|---|---|---|
| 读/写配置 `get`/`put` | `NSUserDefaults` | 记录、皮肤、LLM Key 都走这里 |
| 请求 `chat`/`list` | ktor + `NSURLSession` | 与安卓 OkHttp 同步调用等价 |
| 流式 `streamStart`/`streamRead` | 一次性拿完再返回（伪流式） | 真流式要 `NSURLSessionDataDelegate`，还没做 |
| 触感 `haptic` | `UIImpactFeedbackGenerator` | 三档 = light/medium/heavy |
| 剪贴板 `copy` | `UIPasteboard` | |
| 导出 `saveFile`/`saveFileAs` | 写临时目录 + `UIActivityViewController` | iOS 沙盒没有「静默存到下载目录」，都会弹分享面板 |
| 默认导出目录 | 置空 | 共享层自动退化成每次弹面板 |
| 导入 `pickText` | 未实现 | 要 `UIDocumentPickerViewController` + delegate |
| 打开链接 `openUrl` | `UIApplication.openURL` | |
| 收键盘 `hideIme` | `endEditing(true)` | |
| 版本/包名/屏幕 | `NSBundle` / `UIScreen` | 屏幕给物理像素，和安卓同口径 |
| 日志 | `KRLogModule` 适配器 | 没有它，Kotlin 侧异常在 iOS 上完全看不见 |
| 自更新 | 置空 | App Store 不允许 App 内自更新 |
| 返回 `onBack`/`exitApp` | 置空 | iPhone 没有系统返回键，走手势 + 页内按钮 |
| 实时雾 `realtimeFogAvailable` | `false` | 走静态雾降级（`FogShader.metal` 是另一条路） |

**已知未做**：真流式、文件导入 `pickText`、iPhone 安全区的实测微调。
隐私清单已加（`PrivacyInfo.xcprivacy`，声明 NSUserDefaults 用途）。

---

## 五、生成 ipa（不要 688 年费）

workflow 里的 `ipa` job 直接产出**免签名**的 `iosApp-unsigned.ipa`。
但免签名 ipa 装不进 iPhone —— iPhone 只接受有签名描述文件的 App。不花年费的装法是自签：

1. 在 Windows 上装 [Sideloadly](https://sideloadly.io/)（或 AltStore）
2. iPhone 用数据线连上，Sideloadly 里选这个 ipa
3. Apple ID 填自己的（**免费账号就行**，不用开发者计划）
4. 装完在 iPhone「设置 → 通用 → VPN与设备管理」里信任你的证书
5. 有效期 **7 天**，到期重签一次

其他路：¥688/年开发者计划（可 TestFlight 分发、可长期）、Mac 上用 Xcode 免费账号跑一次（同样 7 天）。

---

## 六、踩坑记录

- **`iosMain` 源集要手动建**：加了 `androidTarget()` 之后默认层级模板不生效，
  `val iosMain by getting` 会报 `KotlinSourceSet with name 'iosMain' not found`，
  必须 `by creating` 再 `dependsOn(commonMain)`。
- **framework 名必须叫 `shared`**：`XLRRenderViewController.m` 的 `fetchContextCodeWithPageName`
  回调就是按这个名字找入口，改名会导致运行时白屏。
- **`-ObjC` 链接参数不能省**：Kuikly 渲染器用类别扩展 UIKit 类，静态链接不带这个参数那些类别不会被加载。
- **`core-render-ios` 的版本号没有 Kotlin 后缀**：`core` / `compose` / `core-render-android` 是
  `2.30.0-2.1.21` 这种「Kuikly 版本-Kotlin 版本」，iOS 渲染器只写 `2.30.0`。
- **Kotlin/Native 没有 `Dispatchers.IO`**：网络层走 ktor，或者自己抽 `expect/actual`。
- **Kotlin/Native 绑定的 `NSURLSession` 只有单参数重载**：带 `completionHandler` 的那个没生成。
- **`~/.konan` 要缓存**：不缓存每轮白等几分钟重下几百 MB，workflow 里已经配了。
- **Xcode 27 起 iOS SDK 最低部署目标是 15.0**：Pods 里的老配置要在 `post_install` 里一起抬上来。
- **模拟器包要限定架构**：`generic/platform=iOS Simulator` 默认连 x86_64 一起要，
  而 framework 只有 arm64 ⇒ 加 `ARCHS=arm64 EXCLUDED_ARCHS=x86_64`。
- **资源要在 `Assets/` 子目录里找**：Kuikly 默认从 bundle 根找，适配器要指到子目录，
  否则界面画出来但没有图片。
- **iOS 的画面只能在 iPhone 上看**（Windows 跑不了模拟器）：界面适配类的事（安全区、键盘、手势）
  以实机为准。
