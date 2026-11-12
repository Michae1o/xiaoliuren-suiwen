plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
    kotlin("plugin.compose")
}

val kuiklyVersion = "2.30.0-2.1.21"

kotlin {
    androidTarget()

    /* ★ iOS（Kuikly 一码五端）：声明 target 在 Windows 上也是安全的，
       只是编译 Apple 目标必须在 macOS 上做（GitHub Actions 的 macos runner 负责）。
       framework 名必须是 "shared" —— iOS 壳的 fetchContextCodeWithPageName 回调按这个名字找入口。 */
    iosArm64 { binaries.framework { baseName = "shared"; isStatic = true } }
    iosSimulatorArm64 { binaries.framework { baseName = "shared"; isStatic = true } }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:$kuiklyVersion")
                implementation("com.tencent.kuikly-open:core-annotations:$kuiklyVersion")
                implementation("com.tencent.kuikly-open:compose:$kuiklyVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:$kuiklyVersion")
            }
        }
        val iosMain by creating {
            /* 手动建的中间源集：默认层级模板在本工程没生效（加了 androidTarget 后
               iosArm64/iosSimulatorArm64 不会自动挂到 iosMain），所以这里显式串起来。
               ⚠ iosMain 不需要 core-render-ios：Kuikly 的 iOS 渲染器是 Objective-C 的，
                 由 CocoaPods 装（iosApp/Podfile 里的 OpenKuiklyIOSRender），Maven 上没这个 Kotlin 制品。
               ⚠ 网络走 ktor（官方 demo 的 iosMain 就是这么干的）：NSURLSession 在 Kotlin/Native
                 里没有带 completionHandler 的 dataTaskWithRequest，手搓 ObjC 绑定既难编也难调。 */
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-core:2.3.10")
                implementation("io.ktor:ktor-client-darwin:2.3.10")
            }
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

dependencies {
    compileOnly("com.tencent.kuikly-open:core-ksp:$kuiklyVersion") {
        add("kspAndroid", this)
        add("kspIosArm64", this)
        add("kspIosSimulatorArm64", this)
    }
}

android {
    namespace = "com.xlr.k.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
