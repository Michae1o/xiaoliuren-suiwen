package com.xlr.k.pages

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo

/* iOS 恒 true：if (iosOnly()) 里的分支只在 iOS 上生效。 */
internal actual fun iosOnly(): Boolean = true

/* UIGlassEffect 是 iOS 26 起才有的；低版本 liquidGlass() 是空操作，
   不留退路控件会变透明 ⇒ 这里按系统主版本判断，低版本走自家 glassMaterial。 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun iosGlassAvailable(): Boolean =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion } >= 26L
