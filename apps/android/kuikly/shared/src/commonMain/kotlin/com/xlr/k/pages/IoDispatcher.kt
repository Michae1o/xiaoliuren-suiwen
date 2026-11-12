package com.xlr.k.pages

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 「该在后台线程跑」的调度器。
 *
 * ⚠ Kotlin/Native 上 `Dispatchers.IO` 是 internal 的（那是 JVM/Android 专有的 IO 池），
 *   共享层直接写 `Dispatchers.IO` 会让 iOS 编不过。所以抽成 expect：
 *   安卓照旧走真正的 IO 池（不改变线上行为），iOS 用 Default（Kotlin/Native 的多线程池）。
 */
internal expect val ioDispatcher: CoroutineDispatcher
