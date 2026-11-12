package com.xlr.k

import com.xlr.k.pages.Platform
import com.xlr.k.pages.TopBarFeed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIKeyboardWillShowNotification
import platform.UIKit.UIPasteboard
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * 文件导出 / 触感 / 剪贴板 / 打开链接 / 收键盘（iOS 侧实现）。
 *
 * 与安卓最大的不同，写在最前面免得后来人以为写错了：
 * — iOS 是沙盒，**没有**「静默写进用户可见的下载目录」这回事。导出只能弹系统分享面板，
 *   让用户选「存到文件 / 发微信 / 存相册」。所以安卓那边 saveFile（静默）和 saveFileAs（弹面板）
 *   在 iOS 上**都会弹面板**。
 * — 「默认导出目录」这套（选目录 / 打开目录 / 清掉目录）iOS 没有对应概念，全部置空，
 *   共享层看到 null 就会退化成「每次导出都弹面板」。
 * — 文件导入（pickText）要先上 DocumentPicker 的 delegate，暂时留空。
 */

internal fun PlatformIos.installFiles() {
    Platform.saveFile = { name, text -> shareText(name, text) }
    Platform.saveFileAs = { name, text, cb ->
        val shown = shareText(name, text)
        cb(shown)
    }

    /* iOS 无「默认目录」语义 */
    Platform.exportDirLabel = null
    Platform.pickExportDir = null
    Platform.openExportDir = null
    Platform.resetExportDir = null

    /* 导入（选一个 txt/json 读进来）：DocumentPicker + delegate。
       ⚠ delegate 必须被强引用住：它在 present 之后由 UIKit 弱引用，
         我们这边一旦没持有就会被回收，回调永远不来。 */
    Platform.pickText = { cb ->
        dispatch_async(dispatch_get_main_queue()) {
            val host = rootViewController()
            if (host == null) {
                cb(null)
                return@dispatch_async
            }
            val d = TextPickerDelegate(cb)
            pickerDelegate = d
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("public.json", "public.plain-text", "public.text"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
            )
            picker.delegate = d
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** 手里攥着那一次导入用的 delegate（见 pickText 里的注释） */
private var pickerDelegate: TextPickerDelegate? = null

@OptIn(ExperimentalForeignApi::class)
private class TextPickerDelegate(
    private val cb: (String?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        var text: String? = null
        val path = url?.path
        if (path != null) {
            /* NSString 的 stringWithContentsOfURL 在 Kotlin/Native 里没有绑定，
               走文件管理器读 NSData 再自己转字符串（toKString 在 PlatformIos.kt 顶层）。 */
            text = NSFileManager.defaultManager.contentsAtPath(path)?.toKString()
        }
        pickerDelegate = null
        cb(text)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        pickerDelegate = null
        cb(null)
    }
}

internal fun PlatformIos.installMisc() {
    /* 触感三档，和安卓的毫秒/振幅一一对应：
       0 = 刻度（页间滑动、八卦名换格）→ light
       1 = 钝感（开记录抽屉）→ medium
       2 = 重（起卦、记录菜单）→ heavy
       ⚠ 必须回主线程，UIKit 的触感 API 不保证线程安全。 */
    Platform.haptic = { level ->
        val style = when (level) {
            0 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            1 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
            else -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        }
        dispatch_async(dispatch_get_main_queue()) {
            UIImpactFeedbackGenerator(style = style).impactOccurred()
        }
    }

    Platform.copy = { text ->
        UIPasteboard.generalPasteboard.string = text
    }

    Platform.openUrl = { url ->
        dispatch_async(dispatch_get_main_queue()) {
            kotlin.runCatching {
                val app = platform.UIKit.UIApplication.sharedApplication
                val nsUrl = platform.Foundation.NSURL.URLWithString(url)
                if (nsUrl != null) app.openURL(nsUrl)
            }
        }
    }

    /* 收键盘（点空白 / 点皮肤 / 点昼夜 / 点记录栏 / 起卦完成后都会走到这里）。
       ★ 以前这里是 null，注释说「iOS 上输入框失焦键盘就自己退」—— 实测**不会**：
         Kuikly 的原生输入框一直拿着 first responder，点旁边空白它不退。
         改成发通知给 Swift 侧（那里能直接调 UIKit 的 sendAction / endEditing），
         resign 之后系统会发 UIKeyboardWillHide，imeVisible 跟着归 false。 */
    Platform.hideIme = {
        NSNotificationCenter.defaultCenter.postNotificationName("xlrHideIme", null, null)
    }

    /* 这两个是安卓窗口层的补丁（键盘遮挡 / 底部露白），iOS 上键盘不缩窗口、
       容器底色由 ObjC 壳统一给，所以置空。 */
    Platform.setImePadPx = null
    Platform.setPageBg = null
}

/**
 * 弹系统分享面板。
 * ⚠ iOS 沙盒不让我们替用户决定存哪儿，所以这里只能把内容交给分享面板；
 *   用户选「存储到文件」时系统会问文件名。
 * @return 给用户看的文件名（内容为空返回 null）
 */
@OptIn(ExperimentalForeignApi::class)
private fun shareText(name: String, text: String): String? {
    if (text.isEmpty()) return null
    /* 先落到 App 自己的 Documents（Info.plist 开了 UIFileSharingEnabled，
       用户能在「文件」App → 我的 iPhone → 卦台 里看到、拷进 iCloud），
       再把这个文件本身交给分享面板 —— 比分享一串纯文本更容易存成 .json。 */
    val saved = saveToDocuments(name, text)
    dispatch_async(dispatch_get_main_queue()) {
        val host = rootViewController() ?: return@dispatch_async
        val item: Any = saved?.let { NSURL.fileURLWithPath(it) } ?: text
        val avc = UIActivityViewController(activityItems = listOf(item), applicationActivities = null)
        host.presentViewController(avc, animated = true, completion = null)
    }
    return name
}

/** 写进沙盒 Documents，返回完整路径（失败 null） */
@OptIn(ExperimentalForeignApi::class)
private fun saveToDocuments(name: String, text: String): String? {
    val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    val dir = dirs.firstOrNull() as? String ?: return null
    val path = "$dir/$name"
    val bytes = text.encodeToByteArray()
    /* 走 posix 写，不碰 NSData —— Kotlin/Native 里 NSData 的几种构造都对不上（见 PlatformIos.kt 的注）。
       ⚠ fopen/fclose 的 const char* 参数在 Kotlin/Native 里直接吃 String（cinterop 自动转换），
         别再手动 .cstr —— 那会变成 CValues 类型、编译不过。 */
    val f = platform.posix.fopen(path, "wb") ?: return null
    try {
        bytes.usePinned { p ->
            platform.posix.fwrite(p.addressOf(0), 1uL, bytes.size.toULong(), f)
        }
    } finally {
        platform.posix.fclose(f)
    }
    return path
}

/** 找当前最顶层的 VC 用来 present 分享面板 */
@OptIn(ExperimentalForeignApi::class)
private fun rootViewController(): UIViewController? {
    val app = platform.UIKit.UIApplication.sharedApplication
    val win = app.windows.firstOrNull { (it as? platform.UIKit.UIWindow)?.isKeyWindow() == true }
        as? platform.UIKit.UIWindow
        ?: (app.windows.firstOrNull() as? platform.UIKit.UIWindow)
    var vc = win?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc.presentedViewController
    }
    return vc
}

/**
 * 键盘避让（对应安卓 MainActivity 里的 applyImeInset）。
 *
 * 安卓的键盘会把窗口顶小，所以我们量「窗口可见区」就能算出要补多少内边距；
 * iOS 的键盘**不会缩窗口**，只能听通知拿键盘高度，写进共享层要的两个状态：
 *   Platform.imeVisible —— 输入卡据此收掉底部留白
 *   Platform.imePadPx   —— 页面底部要让出来的物理像素
 * 这两个不接，点输入框时键盘会把整张输入卡盖住（实机必现，模拟器截图看不出来）。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformIos.installKeyboard() {
    val center = NSNotificationCenter.defaultCenter

    /* 屏幕高（pt）。键盘 frame 报的是**屏幕坐标**，拿它减键盘顶边就是键盘真实可见高度。 */
    var screenPt = 0.0
    UIScreen.mainScreen.bounds.useContents { screenPt = size.height }

    center.addObserverForName(
        UIKeyboardWillShowNotification,
        null,
        NSOperationQueue.mainQueue,
    ) { note ->
        val v = note?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue
        if (v != null) {
            var hBySize = 0.0
            var kbTop = 0.0
            v.CGRectValue.useContents {
                hBySize = size.height
                kbTop = origin.y
            }
            /* 「屏幕底 − 键盘顶」才是键盘真实高度：只读 rect 的 height 时，实机上量出来
               只有实际的一半（键盘占 441px、这条给出来只够挪 190px，卡片下半截还在键盘底下）。
               ⚠ 有些系统版本这个 rect 的 origin 给 {0,0}，那时 hByGeo 会等于整屏高 ——
                 用 0.62 屏高当上限挡掉，退回 rect 自己的 height，不会把内容顶飞。 */
            val hByGeo = screenPt - kbTop
            val raw = if (hByGeo > 40.0 && hByGeo < screenPt * 0.62) maxOf(hBySize, hByGeo) else hBySize
            /* 再兜一层：iPhone 上带候选栏的键盘至少占屏高 1/3，报出来比 0.22 屏高还小
               一定是口径不对（实机量到过 1/6 的值）⇒ 按 0.375 屏高走，宁可多挪一点。 */
            val h = if (raw < screenPt * 0.22) screenPt * 0.375 else raw
            if (h > 20.0) {
                Platform.imePadPx = h.toInt()
                Platform.imeVisible = true
                /* ★ 位移在 Kotlin 侧算一份（pt）：Swift 那边 r52 实测 ap270 —— 值到了、transform 也设了，
                   但 Kuikly 的渲染层不跟那个 view 的 transform 走，像素纹丝不动。
                   ⇒ 改由页面自己往上移（见 RootPage 的 translationY）。 */
                com.xlr.k.pages.Session.imeShift = (h - 59.0).coerceAtLeast(0.0).toFloat()
                /* ★ 高度只在这里量一份，Swift 那边直接拿来用 —— 两端各算一遍必然对不上，
                   iOS 宿主读同一份 frame 在不同系统版本上还给过 {0,0} 和一半值。 */
                postIme(h, note?.userInfo)
            }
        }
    }
    center.addObserverForName(
        UIKeyboardWillHideNotification,
        null,
        NSOperationQueue.mainQueue,
    ) { note ->
        Platform.imePadPx = 0
        Platform.imeVisible = false
        com.xlr.k.pages.Session.imeShift = 0f
        postIme(0.0, note?.userInfo)
    }
}

/**
 * 把「键盘可见高度 + 键盘自己的动画时长/曲线」发给 Swift 宿主（ContentView 监听 `xlrIme`）。
 *
 * 为什么连时长/曲线一起给：让位动画必须跟键盘同一套参数，否则两段速度不一致，
 * 看起来就是「先跳一下再慢慢滑回来」。曲线在通知里是 UInt，Swift 侧左移 16 位还原。
 */
@OptIn(ExperimentalForeignApi::class)
private fun postIme(hPt: Double, info: Map<Any?, *>?) {
    var dur = 0.25
    var curve = 7 // 7 = UIViewAnimationCurve.easeOut
    (info?.get(UIKeyboardAnimationDurationUserInfoKey) as? NSNumber)?.let { dur = it.doubleValue }
    (info?.get(UIKeyboardAnimationCurveUserInfoKey) as? NSNumber)?.let { curve = it.intValue }
    NSNotificationCenter.defaultCenter.postNotificationName(
        "xlrIme",
        null,
        mapOf("h" to hPt, "dur" to dur, "curve" to curve),
    )
}

/**
 * 顶栏原生化的桥。
 *   Kotlin → Swift：`xlrTopBar`（step / label / dark / hidden）
 *   Swift → Kotlin：`xlrTopAction`（menu / skin / daynight）
 * 颜色不推 —— 原生顶栏用系统材质（ultraThin），明暗自己跟随皮肤。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformIos.installTopBar() {
    Platform.pushTopBar = { step, label, dark, hidden ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrTopBar",
            null,
            mapOf("step" to step, "label" to label, "dark" to dark, "hidden" to hidden),
        )
    }
    NSNotificationCenter.defaultCenter.addObserverForName(
        "xlrTopAction",
        null,
        NSOperationQueue.mainQueue,
    ) { note ->
        /* ObjC 那边给过来的可能还是 NSString，用 toString() 最稳 */
        val act = note?.userInfo?.get("act")?.toString()
        if (act != null) TopBarFeed.onAction?.invoke(act)
    }
}

/**
 * 记录行菜单的原生出口：把 items/danger 交给 Swift（ContentView 里用 confirmationDialog 弹），
 * 选完回传索引，再回到这里的回调执行 Kotlin 动作。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformIos.installActionSheet() {
    Platform.showActionSheet = { items, danger, onPick ->
        pendingSheetPick = onPick
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrActionSheet",
            null,
            mapOf("items" to items, "danger" to danger),
        )
    }
    NSNotificationCenter.defaultCenter.addObserverForName(
        "xlrActionSheetPick",
        null,
        NSOperationQueue.mainQueue,
    ) { note ->
        val idx = (note?.userInfo?.get("idx") as? NSNumber)?.intValue ?: -1
        val cb = pendingSheetPick
        pendingSheetPick = null
        if (idx >= 0 && cb != null) cb(idx)
    }
}

private var pendingSheetPick: ((Int) -> Unit)? = null

/**
 * 原生选择器的桥（iOS）。
 *   Kotlin → Swift：`xlrPickerShow`（kind / title / values / labels / selected）
 *     两个数组拼成 \u0001 分隔的字符串传：跨语言传 Kotlin List 要过 NSArray，
 *     拼串最省事，零类型坑。
 *   Swift → Kotlin：`xlrPickerPick`（value）点了「完成」；`xlrPickerCancel` 取消/下滑关掉。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformIos.installPicker() {
    Platform.showPicker = { kind, title, values, labels, selected, onPick ->
        pendingPickerPick = onPick
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrPickerShow",
            null,
            mapOf(
                "kind" to kind,
                "title" to title,
                "values" to values.joinToString("\u0001"),
                "labels" to labels.joinToString("\u0001"),
                "selected" to selected,
            ),
        )
    }
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName("xlrPickerPick", null, NSOperationQueue.mainQueue) { note ->
        /* ObjC 给回来的可能是 NSString，toString() 最稳 */
        val v = note?.userInfo?.get("value")?.toString()
        val cb = pendingPickerPick
        pendingPickerPick = null
        if (cb != null && v != null) cb(v)
    }
    center.addObserverForName("xlrPickerCancel", null, NSOperationQueue.mainQueue) { _ ->
        val cb = pendingPickerPick
        pendingPickerPick = null
        cb?.invoke(null)
    }
}

private var pendingPickerPick: ((String?) -> Unit)? = null

/** Kotlin 的 Map<String,String> 不能直接当 Map<Any?,Any?> 传（K 不变型）⇒ 转一层。 */
private fun toDict(m: Map<String, String>): Map<Any?, Any?> {
    val out = LinkedHashMap<Any?, Any?>(m.size)
    m.forEach { (k, v) -> out[k] = v }
    return out
}

/**
 * 原生起卦卡的桥（iOS）。
 *   Kotlin → Swift：`xlrAskState`（状态）/ `xlrAskSkin`（色值）
 *   Swift → Kotlin：`xlrAskEdit`/ `xlrAskHeight`（卡片高度）
 * 装好之后 Platform.nativeAsk = true，Kuikly 才不再画自己的卡（装不上就照旧，天然回退）。
 */
/** 原生记录抽屉（iOS）的桥：数据整份过去，动作回来自改库再推一次。 */
@OptIn(ExperimentalForeignApi::class)
internal fun PlatformIos.installDrawer() {
    Platform.drawerPush = { json ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrDrawerData", null, mapOf<Any?, Any?>("json" to json),
        )
    }
    Platform.drawerOpen = {
        NSNotificationCenter.defaultCenter.postNotificationName("xlrDrawerOpen", null, null)
    }
    Platform.drawerProg = { v ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrDrawerProg", null, mapOf<Any?, Any?>("v" to v),
        )
    }
    Platform.imeAllowed = { ok ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrImeAllow", null, mapOf<Any?, Any?>("v" to if (ok) "1" else "0"),
        )
    }
    Platform.drawerDelta = { t ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrDrawerDelta", null, mapOf<Any?, Any?>("t" to t),
        )
    }
    Platform.pageShift = { px ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrPageShift", null, mapOf<Any?, Any?>("x" to px),
        )
    }
    NSNotificationCenter.defaultCenter.addObserverForName(
        "xlrDrawerAct", null, NSOperationQueue.mainQueue,
    ) { note ->
        val k = note?.userInfo?.get("k")?.toString() ?: return@addObserverForName
        val v = note?.userInfo?.get("v")?.toString() ?: ""
        if (k == "sync") {
            Platform.drawerPush?.invoke(com.xlr.k.pages.drawerJson())
        } else {
            com.xlr.k.pages.applyDrawerAct(k, v)
            Platform.drawerPush?.invoke(com.xlr.k.pages.drawerJson())
        }
    }
    Platform.nativeDrawer = true
}

@OptIn(ExperimentalForeignApi::class)
internal fun PlatformIos.installAskCard() {
    Platform.pushAskState = { st ->
        NSNotificationCenter.defaultCenter.postNotificationName("xlrAskState", null, toDict(st))
    }
    Platform.pushAskSkin = { st ->
        NSNotificationCenter.defaultCenter.postNotificationName("xlrAskSkin", null, toDict(st))
    }
    Platform.askVisible = { on ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrAskVis", null, mapOf("v" to if (on) "1" else "0"),
        )
    }
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName("xlrAskEdit", null, NSOperationQueue.mainQueue) { note ->
        val k = note?.userInfo?.get("k")?.toString()
        val v = note?.userInfo?.get("v")?.toString() ?: ""
        if (k != null) com.xlr.k.pages.applyAskEdit(k, v)
    }
    center.addObserverForName("xlrAskHeight", null, NSOperationQueue.mainQueue) { note ->
        val h = (note?.userInfo?.get("h") as? NSNumber)?.doubleValue ?: 0.0
        if (h > 1.0) com.xlr.k.pages.Session.cardHdp = h.toFloat()
    }
    Platform.nativeAsk = true
}
