package com.xlr.k.pages

/* 安卓恒 false：所有 if (iosOnly()) 分支在安卓上都不会走，
   保证"只改 iOS"的调整不会改动安卓的既有行为。 */
internal actual fun iosOnly(): Boolean = false

/* 安卓没有系统液态玻璃（UIGlassEffect 是 iOS 26 的东西）⇒ 恒 false，走自家 glassMaterial。 */
internal actual fun iosGlassAvailable(): Boolean = false
