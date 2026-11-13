package com.xlr.k

import com.xlr.k.pages.Platform
import com.xlr.k.pages.StreamSnap
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIScreen
import platform.posix.memcpy
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS 端的宿主能力注入（对应安卓的 androidApp/LlmHost.kt + MainActivity 里那一段赋值）。
 *
 * 共享层的 `Platform` 每个回调都是可空、默认 null，宿主不接就静默降级。
 * 入口：Swift 侧 `iOSApp.init` 里调一句 `PlatformIos.shared.install()`。
 *
 * ⚠ 网络为什么用 ktor 而不是 NSURLSession：
 *   Kotlin/Native 绑定的 NSURLSession **只有** `dataTaskWithRequest(request)` 这一个重载，
 *   带 completionHandler 的那个在绑定里不存在（实测报 "Too many arguments"）。
 *   自己实现 NSURLSessionDataDelegate 要写 ObjC 协议子类，坑多且难调；
 *   而 ktor 是纯 Kotlin、官方 demo 的 iosMain 就用它，跨端最稳。
 */
object PlatformIos {

    private const val SP_NAME = "xlr-k-llm"

    /** NSUserDefaults 里加个前缀，避免和别的键撞 */
    private fun key(k: String) = "$SP_NAME.$k"

    /** ktor 客户端复用一个实例（每次新建会漏连接池） */
    private val http by lazy { HttpClient(Darwin) }

    fun install() {
        installCore()
        installNet()
        installFiles()
        installMisc()
        installKeyboard()
        installTopBar()
        installActionSheet()
        installPicker()
        installAskCard()
        installDrawer()
        installTheme()
    }

    /* 皮肤深浅 → SwiftUI 层（它据此切 preferredColorScheme，状态栏文字颜色跟着变）。
       ⚠ 不走 Kotlin 回调桥：((Boolean) -> Unit) 到 Swift 侧是 KotlinBoolean，映射别扭；
         NSNotification + userInfo 更省事。dark 用 0/1 传，Swift 侧读 NSNumber 不会踩类型。 */
    private fun installTheme() {
        Platform.onDark = { dark ->
            /* ⚠ 必须用位置参数：`object = null` 里的 object 是 Kotlin 关键字，
               写成具名参数会报 "Syntax error: Expecting a class body"（踩过两次）。
               签名顺序是 (name, object, userInfo)。 */
            platform.Foundation.NSNotificationCenter.defaultCenter.postNotificationName(
                "xlrThemeChanged",
                null,
                mapOf<Any?, Any?>("dark" to if (dark) 1 else 0),
            )
        }
    }

    // ---------------------------------------------------------------- 存储 / 屏幕 / 版本

    @OptIn(ExperimentalForeignApi::class)
    private fun installCore() {
        val d = NSUserDefaults.standardUserDefaults

        Platform.get = { k -> d.stringForKey(key(k)) }
        Platform.put = { k, v -> d.setObject(v, forKey = key(k)) }

        val info = NSBundle.mainBundle.infoDictionary
        Platform.version = (info?.get("CFBundleShortVersionString") as? String) ?: "1.0"
        Platform.versionCode = (info?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 1
        Platform.pkgName = (info?.get("CFBundleIdentifier") as? String) ?: "cn.luoshen.xiaoliuren.ios"

        /* 共享层拿这两个值把「设计坐标」换算成物理像素（手势/布局要用），
           和安卓那边给的口径一致：物理像素，不是点。
           ⚠ CGRect 在 Kotlin/Native 里是 CValue，必须 useContents 才能读到字段。 */
        var w = 0.0
        var h = 0.0
        UIScreen.mainScreen.bounds.useContents {
            w = size.width
            h = size.height
        }
        val scale = UIScreen.mainScreen.scale
        Platform.screenW = (w * scale).toInt()
        Platform.screenH = (h * scale).toInt()

        /* iOS 不允许 App 内自更新（App Store 规则），整组置空：
           共享层看到 null 就不会显示「检查更新」那一条。 */
        Platform.updCheck = null
        Platform.updDownload = null
        Platform.updInstall = null

        /* iPhone 没有系统返回键，「连按两次退出 / 浮层→设置→抽屉」这套是安卓的返回栈语义。
           iOS 走右滑返回 + 页内按钮，这里置空，共享层不会拦系统手势。 */
        Platform.onBack = null
        Platform.exitApp = null

    }

    // ---------------------------------------------------------------- 网络

    private fun installNet() {
        Platform.chat = { url, headers, body ->
            httpText(url, headers, body, method = "POST")
        }
        Platform.list = { url, headers ->
            parseModelIds(httpText(url, headers, null, method = "GET"))
        }

        /* 真流式：ktor 的 ByteReadChannel 逐行读 SSE，把 delta.content 追进正文。
           共享层 Llm.kt 是把 StreamSnap.text 当「已解析的正文」用的
           （它直接 onText(s.text)），所以这里必须解析掉 "data: {...}" 这层壳，
           不能把原始 SSE 文本丢给上层。 */
        Platform.streamStart = { url, headers, body ->
            val id = nextStreamId++
            streams[id] = StreamState("", done = false, err = "")
            streamScope.launch {
                try {
                    http.preparePost(url) {
                        headers.forEach { (k, v) -> header(k, v) }
                        contentType(ContentType.Application.Json)
                        if (body != null) setBody(body)
                    }.execute { resp ->
                        if (!resp.status.isSuccess()) {
                            throw IllegalStateException("HTTP ${resp.status.value}")
                        }
                        val ch = resp.bodyAsChannel()
                        while (true) {
                            val line = ch.readUTF8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty()) continue
                            if (payload == "[DONE]") break
                            val delta = sseDelta(payload) ?: continue
                            /* ⚠ 每次换一个新对象而不是原地 append：
                               读的人可能在别的线程，StringBuilder 并发读会读到半截。 */
                            val cur = streams[id] ?: continue
                            streams[id] = StreamState(cur.text + delta, done = false, err = "")
                        }
                    }
                    val cur = streams[id] ?: return@launch
                    streams[id] = StreamState(cur.text, done = true, err = "")
                } catch (e: Throwable) {
                    val cur = streams[id]
                    streams[id] = StreamState(cur?.text ?: "", done = true, err = e.message ?: "请求失败")
                }
            }
            id
        }
        Platform.streamRead = { id ->
            val s = streams[id]
            if (s == null) StreamSnap("", true, "没有这个流") else StreamSnap(s.text, s.done, s.err)
        }
    }

    /* 不可变快照：读的人可能在渲染线程，不能让它读到半截 StringBuilder */
    private class StreamState(val text: String, val done: Boolean, val err: String)

    /** 流式请求跑在后台（不能阻塞调用方，否则 UI 卡住） */
    private val streamScope = CoroutineScope(Dispatchers.Default)

    /** 从一条 SSE 的 data 里抠出 delta.content（各家都遵循 OpenAI 的格式） */
    private fun sseDelta(payload: String): String? {
        val key = "\"content\":\""
        val at = payload.indexOf(key)
        if (at < 0) return null
        val sb = StringBuilder()
        var i = at + key.length
        while (i < payload.length) {
            val c = payload[i]
            if (c == '\\' && i + 1 < payload.length) {
                sb.append(c).append(payload[i + 1])
                i += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        if (sb.isEmpty()) return null
        return com.xlr.k.judge.junesc(sb.toString())
    }

    private var nextStreamId = 1
    private val streams = mutableMapOf<Int, StreamState>()

    /**
     * 同步 HTTP —— 共享层的 chat/list 都是同步签名（安卓那边用 HttpURLConnection 阻塞）。
     * 这里用 runBlocking 把 ktor 的挂起调用包成阻塞，行为与安卓一致。
     */
    private fun httpText(
        url: String,
        headers: Map<String, String>,
        body: String?,
        method: String,
    ): String = runBlocking {
        val resp = if (method == "POST") {
            http.post(url) {
                headers.forEach { (k, v) -> header(k, v) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } else {
            http.get(url) {
                headers.forEach { (k, v) -> header(k, v) }
            }
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            throw IllegalStateException("HTTP ${resp.status.value}: ${text.take(300)}")
        }
        text
    }

    /**
     * 解析模型清单（与安卓 LlmHost.get 同口径：取 id / name / model 三个字段之一）。
     * ⚠ 用正则扫而不是完整 JSON 解析：清单结构在各家服务商之间差异很大，
     *   安卓那边是 org.json 逐层取字段，这里只要拿到「模型名的字符串列表」，
     *   完整解析器要引 kotlinx.serialization，为一个清单不值当。
     */
    private fun parseModelIds(text: String): List<String> {
        fun grab(field: String): List<String> =
            Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(text)
                .map { it.groupValues[1] }
                .toList()

        val ids = grab("id").ifEmpty { grab("name") }.ifEmpty { grab("model") }
        if (ids.isEmpty()) throw IllegalStateException("清单是空的")
        return ids.distinct().sorted()
    }
}

// ---------------------------------------------------------------- NSData -> String
// 放顶层：PlatformIosFiles.kt 里读导入文件也要用

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toKString(): String? {
    val len = length.toInt()
    if (len == 0) return ""
    val bytes = ByteArray(len)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, length)
    }
    return bytes.decodeToString(throwOnInvalidSequence = false)
}

/* ⚠ 写文件不走 NSData：
   dataWithBytes / create / dataWithCapacity 在 Kotlin/Native 的绑定里一个都找不到（实测三轮），
   直接用 posix 的 fopen/fwrite —— 那是 C 库，一定有，路径按 UTF-8 传即可。 */
