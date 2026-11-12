package com.xlr.k.pages

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/* iOS 没有 Dispatchers.IO（Kotlin/Native 里它是 internal）。用 Default：
   新内存模型下它是真正的多线程池，跑网络和文件都够用。 */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
