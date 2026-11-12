package com.xlr.k.pages

/**
 * 「只改 iOS」的开关。
 *
 * 代价太大、而且以后修一个共享逻辑得改两处；这里改用平台开关——
 * 安卓编译出来 iosOnly() 恒为 false，走的是原分支，行为与拆分前**逐字一致**。
 *
 * 用法：
 *   if (iosOnly()) { ...iOS 专属... } else { ...安卓原样... }
 *
 * ⚠ 别把它当成"给 iOS 开后门"：只在确实两端要求不同时用。
 *   两端本该一致的东西（颜色表、文案、判据）继续走共享代码。
 */
internal expect fun iosOnly(): Boolean

/** 这台机器有没有系统液态玻璃（UIGlassEffect，iOS 26 起）。安卓恒 false。 */
internal expect fun iosGlassAvailable(): Boolean

/** 有实时雾（安卓 true、iOS false，走静态雾降级）。 */
internal expect fun realtimeFogAvailable(): Boolean
