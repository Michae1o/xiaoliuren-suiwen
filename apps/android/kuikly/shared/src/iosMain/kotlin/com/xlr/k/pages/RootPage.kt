package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.animation.AnimatedContent
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.animation.fadeIn
import com.tencent.kuikly.compose.animation.fadeOut
import com.tencent.kuikly.compose.animation.scaleIn
import com.tencent.kuikly.compose.animation.scaleOut
import com.tencent.kuikly.compose.animation.togetherWith
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.ui.draw.drawBehind
import com.tencent.kuikly.compose.foundation.gestures.detectHorizontalDragGestures
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.annotations.Page
import com.xlr.k.judge.autoCat
import com.xlr.k.judge.buildContext
import com.xlr.k.judge.castByClock
import com.xlr.k.judge.castByDate
import com.xlr.k.judge.castByNums
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/* 三个步骤：壹（问卦）/ 贰（起卦）/ 叁（断卦）。
   旧实现把步骤挂在 html[data-step] 上、由一堆选择器分别管显隐；这里收成一个枚举 + 一次 when。 */
internal enum class XlrStep(val ch: String, val label: String) {
    YI("壹", "问卦"),
    GUA("贰", "起卦"),
    JIE("叁", "断卦");

    fun next(): XlrStep = when (this) {
        YI -> GUA
        GUA -> JIE
        JIE -> JIE
    }

    fun prev(): XlrStep = when (this) {
        YI -> YI
        GUA -> YI
        JIE -> GUA
    }
}

/* 跟手参数（照 phoneAnim.js）：FOLLOW=.86、SWIPE_MIN=110、EDGE_MAX=54、
   缩放 1-.06p、透明度 1-min(.42,|out|/320)、回弹 max(.2,.4-.18p) 秒 */
private const val FOLLOW = 0.86f
private const val SWIPE_MIN = 110f
private const val EDGE_MAX = 54f

/* 旧 drawBagua 的池子（phoneAnim BAGUA_ALL）：滑动手势期步进条按进度换这四个字 */
private val BAGUA_ALL = listOf("乾", "兑", "离", "震", "巽", "坎", "艮", "坤")

@Page("Yi")
class YiPage : ComposeContainer() {

    override fun willInit() {
        super.willInit()
        setContent {
            RootScreen()
        }
    }
}

@Composable
private fun RootScreen() {
    var step by remember { mutableStateOf(XlrStep.YI) }
    /* 皮肤深浅同步给宿主（iOS 靠它切状态栏文字颜色：深色皮肤下系统黑字看不见）。
       ⚠ 用 LaunchedEffect(Skin.cur) 而不是在 Skin.apply() 里回调 —— 换肤的入口不止一个
         （菜单、设置页、日夜切换），挂在状态上才不会漏。 */
    LaunchedEffect(Skin.cur) { Platform.onDark?.invoke(Skin.dark) }
    /* 一屏宽（px）。三页并排，轨道按 -index*w + 跟手位移 平铺 */
    var wPx by remember { mutableStateOf(0f) }
    /* 轨道当前平移量（px，负值＝往左走）。跟手时同步改它，落位时用 settle 动画到目标 */
    var posX by remember { mutableStateOf(0f) }
    val settle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf(false) }
    val dens = LocalDensity.current
    /* 旧 SWIPE_MIN=110（CSS px）⇒ 换算成这个屏的物理像素，给「途中闪八卦」的进度用 */
    val swipeMinPx = with(dens) { SWIPE_MIN.dp.toPx() }

    /* 真正起卦：按当前「起卦方式」取数 → 判据上下文 → 进贰页（旧 castCore.goCast）。
       ⚠ 旧实现的三路分支与报错句逐字照抄；报数没填全 ⇒ 当场提示、不播四拍。 */
    fun pad2(v: Int): String = if (v < 10) "0$v" else "$v"

    val askErr: () -> String = {
        val q = Session.q.trim()
        when {
            q.isEmpty() -> "先写清楚你要问的事。"
            q.length < 4 -> "问题太短了——「问什么，卦就答什么」，请写具体一点。"
            Session.mode == "num" && Session.nums().any { it.trim().isEmpty() } -> "三个数都要报。"
            Session.mode == "time" -> {
                val ds = "${Session.dtYear}-${pad2(Session.dtMonth)}-${pad2(Session.dtDay)}"
                val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                if (castByDate(ds, zi = null, dt = dt) == null)
                    "超出内置农历表范围（2020–2040）。改用报数，或手工把农历月日填进报数。"
                else ""
            }
            else -> ""
        }
    }

    val doCast: () -> Unit = doCast@{
        val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val q = Session.q.trim()
        val bg = Session.bg.trim()
        val c = when (Session.mode) {
            "num" -> castByNums(Session.nums(), zi = if (Session.ziTouchH2) Session.ziH2 else null, dt = dt)
            "time" -> castByDate(
                "${Session.dtYear}-${pad2(Session.dtMonth)}-${pad2(Session.dtDay)}",
                zi = if (Session.ziTouchH1) Session.ziH1 else null,
                dt = dt,
            )
            else -> castByClock(
                dt,
                manualNums = if (Session.numManual && Session.nums().none { it.trim().isEmpty() }) Session.nums() else null,
                ziOverride = if (Session.ziTouchH2) Session.ziH2 else null,
            )
        } ?: return@doCast
        Session.cast = c
        val key = if (Session.catSel == "auto") autoCat("$q $bg") else Session.catSel
        Session.ctx = buildContext(q = q, bg = bg, catKey = key, cast = c)
        /* 起一卦就记一条（早期版本 `addCase`：每起一卦自动记，同一卦不重记） */
        if (Session.recId.isEmpty()) {
            val cx = Session.ctx!!
            Session.recId = RecStore.add(
                q = cx.q,
                bg = cx.bg,
                gua = c.g[0] + " · " + c.g[1] + " · " + c.g[2] + "　" + c.ziName + "时",
                cat = cx.cat.name,
                catKey = key,
                src = cx.src,
                zi = c.zi,
            )
        }
        step = XlrStep.GUA
        /* 起过一卦 ⇒ 从此三页随便切（「只要进入 app 起完卦之后，解锁随便切页，
           哪怕点了新开一卦」）。这个标记只在本进程里攒，退掉 app 才回到「没起卦不能切」。 */
        Session.swipeUnlocked = true
        /* 起卦完成 ⇒ 把输入法收回去（「问卦完成之后，键盘不会自动收回」） */
        Platform.hideIme?.invoke()
    }

    /* 点「起卦」/ 回车：先过一遍校验，过了才播四拍 */
    val tryCast: () -> Unit = {
        val e = askErr()
        Session.err = e
        Session.errOn = e.isNotEmpty()
        if (e.isEmpty() && !CastFx.playing) {
            /* 按下起卦：重档震动（「点击起卦按钮」也要震） */
            Platform.haptic?.invoke(2)
            /* 新起一卦：接着问区归零（旧 resetAskBox）、这一卦要记新的一条（旧 addCase 每次 show 都记） */
            Follow.reset()
            Session.recId = ""
            Session.followReady = false
            /* ★ 上一卦的模型断辞必须一起清掉 —— 否则新卦进叁页会顶着上一卦的断辞
               。 */
            Session.askAnswer = ""
            Session.askStat = ""
            Session.askModelLabel = ""
            Session.askErr = ""
            Session.askBusy = false
            Session.castSeq += 1
            CastFx.toX = if (wPx > 0f) wPx / 2f else 540f
            CastFx.toY = 720f
            CastFx.playing = true
        }
    }

    /* 初次进入：时辰 / 日期按此刻补齐，三个数按当前方式补齐 */
    LaunchedEffect(Unit) {
        pageScope = scope
        /* 系统返回键交给这里判（宿主 onBackPressed → Platform.onBack） */
        Platform.onBack = { handleBack() }
        Skin.load()   /* 皮肤九套：把上次选的皮肤读回来（没有就黑白） */
        val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        Session.ziH1 = ziNow("time")
        Session.ziH2 = ziNow("clock")
        Session.dtYear = d.year
        Session.dtMonth = d.monthNumber
        Session.dtDay = d.dayOfMonth
        refreshClockNums()
    }

    /* 开屏落定之后再弹一次配置引导（旧 maybeWelcome：开屏在场时不弹；看过就不再弹） */
    LaunchedEffect(Session.splashOn) {
        if (Session.splashOn) return@LaunchedEffect
        kotlinx.coroutines.delay(240)
        maybeGuide()
    }

    /* 每 2 秒跟钟（旧 AskPanel 的 setInterval）；只挂在「时间取数」上，换方式即停 */
    LaunchedEffect(Session.mode) {
        if (Session.mode != "clock") return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(2000)
            refreshClockNums()
        }
    }

    /* 按下问卦：先播四拍（念起→气动→成象→落卦），2400ms 才真正起卦，3150ms 收层 */
    LaunchedEffect(CastFx.playing) {
        if (!CastFx.playing) return@LaunchedEffect
        CastFx.clockMs = 0f
        var ms = 0f
        var fired = false
        while (ms < 3150f) {
            CastFx.clockMs = ms
            if (!fired && ms >= 2400f) {
                fired = true
                doCast()
            }
            kotlinx.coroutines.delay(16)
            ms += 16f
        }
        CastFx.clockMs = 3150f
        CastFx.playing = false
    }

    /* 页码变了（手势松手或按钮触发）⇒ 把轨道滑到这一页 */
    LaunchedEffect(step, wPx) {
        if (wPx <= 0f) return@LaunchedEffect
        val target = -step.ordinal * wPx
        if (abs(target - posX) < 1f) return@LaunchedEffect
        settle.snapTo(posX)
        settling = true
        settle.animateTo(target, tween(300, easing = KY_EASE_OUT))
        posX = target
        settling = false
    }

    /* iOS：原生卡挂在页面之外，翻页时它不会跟着走 ⇒ 把三页轨道的位移逐帧推过去，
       Swift 用同一个位移把卡片一起挪（不推的话切页时卡会钉在原地）。
       ⚠⚠ 必须推**眼睛看到的那一帧**：松手之后那 300ms 是 settle（Animatable）在动，
           posX 要到动画结束才赋值 ⇒ 上一版只盯 posX，结果是「拖到哪儿卡就停在哪儿」，
           贰→壹半边卡贴着左边不动，等落位完才归位）。 */
    LaunchedEffect(Unit) {
        if (!iosOnly()) return@LaunchedEffect
        var lastShift = Float.NaN   /* NaN ⇒ 第一个值一定推（比较恒 false） */
        androidx.compose.runtime.snapshotFlow { if (settling) settle.value else posX }
            .collect { v ->
                /* 位移不到 0.5px 不推：Swift 侧本来就丢掉（`abs(px-last) >= 0.5` 才写 state），
                   白白跨一次 Kotlin→Swift 通知 + 字典分配。 */
                if (kotlin.math.abs(v - lastShift) < 0.5f) return@collect
                lastShift = v
                Platform.pageShift?.invoke(v)
            }
    }

    var dragAcc by remember { mutableStateOf(0f) }
    /* 宿主窗口底色跟着皮肤/昼夜变：详情展开时底部内边距让出来的那块是页面色，不是白（） */
    val pageBg = KY_BG
    SideEffect { Platform.setPageBg?.invoke(pageBg.red, pageBg.green, pageBg.blue) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KY_BG)
            /* 点空白处收输入法（「点空白部位，输入法不会收回」）：
               按钮/输入框那些子控件自己会把点击吃掉，所以「点空白」才会走到这一层。
               ⛔ 别用「键盘高度 > 0」当闸门——有些机型/输入法的窗口高度量出来是 0，
                 那样这道闸永远不开，用户看到的就是「跟以前一样没反应」。 */
            .pointerInput(Unit) {
                detectTapGestures {
                    /* 设置页 / 皮肤菜单有自己的「点空白收键盘」；这里别抢 ——
                       它们的点击会穿到这一层，抢了就把刚点起来的输入框 focus 收掉
                       。 */
                    if (!Session.settingsOpen && !Session.skinMenuOpen) Platform.hideIme?.invoke()
                }
            }
            .pointerInput(Unit) {
                /* ⚠ 这些标记必须活在 pointerInput 的协程里、跨手势记住：
                   原来只在 onDragStart 里 `return@detectHorizontalDragGestures`，只是**退出了那个回调**，
                   拖动照样继续走 onHorizontalDrag —— 于是在设置页/抽屉打开时横向一拖，
                   底下这层照样把记录抽屉拉出来（「设置页面左滑也能唤出记录抽屉，这不合理」）。
                   现在用 blocked 一次性把整次手势掐掉。 */
                var blocked = false
                /* 抽屉跟手模式：壹页往右拖时进入，拖多少给多少，松手再吸附 */
                var drawerMode = false
                var drawerAcc = 0f
                /* 壹页这一拖是往右（开抽屉）还是往左（翻页）——攒够 6px 才定 */
                var dirDecided = false
                var pendingDx = 0f
                /* 起手点（物理像素）—— 用来判断这一拖是不是落在「取数方式」那一排里 */
                var startX = 0f
                var startY = 0f
                var firstMove = true
                /* 刻度震动：上一次抽过的八卦格编号（-1 = 本轮还没抽过） */
                var lastTick = -1
                /* 甩动速度（px/s）：Kuikly 不给 velocity，自己按帧差分攒。
                   「快而短」的一划要能翻页，光看位移是不够的。 */
                var lastMs = 0L
                var velPxS = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        blocked = Session.recordsOpen || Session.settingsOpen ||
                            Session.guideOn || Session.skinMenuOpen || Session.splashOn
                        drawerMode = false
                        drawerAcc = 0f
                        dirDecided = false
                        pendingDx = 0f
                        if (blocked) return@detectHorizontalDragGestures
                        dragAcc = 0f
                        lastTick = -1
                        firstMove = true
                        lastMs = 0L
                        velPxS = 0f
                        posX = if (settling) settle.value else posX
                        settling = false
                        /* 手势期：收起步进条标题、抽 4 个卦名备着（旧 stepCollapseAll + drawBagua） */
                        Session.stepLabelClosed = true
                        Session.stepGlyphOverride = null
                        Session.baguaGrp = BAGUA_ALL.shuffled().take(4)
                    },
                    onDragCancel = {
                        if (blocked) return@detectHorizontalDragGestures
                        if (drawerMode) {
                            drawerMode = false
                            Session.recDragging = false
                            if (Platform.nativeDrawer) {
                                /* 手势被打断：原生那层也带回原位 */
                                Session.recProg = 0f
                                Session.recordsOpen = false
                                Platform.drawerProg?.invoke(0f)
                                return@detectHorizontalDragGestures
                            }
                            RecDrawer.close(scope)
                            return@detectHorizontalDragGestures
                        }
                        Session.stepGlyphOverride = null
                        settling = true
                        scope.launch {
                            settle.snapTo(posX)
                            settle.animateTo(-step.ordinal * wPx, tween(260, easing = KY_EASE_OUT))
                            posX = -step.ordinal * wPx
                            settling = false
                        }
                    },
                    onDragEnd = {
                        if (blocked) return@detectHorizontalDragGestures
                        if (drawerMode) {
                            drawerMode = false
                            Session.recDragging = false
                            /* iOS 原生抽屉：进度不归 Kuikly 管（这里立刻归零，否则卡片会被藏），
                               右滑过半就请原生把它滑出来；没到就推 0 让它带弹簧回去。 */
                            if (Platform.nativeDrawer) {
                                val want = Session.recProg > 0.32f
                                /* ★ 开了就记成 1（不是 0）：barHidden / 卡片让位都看这个值，
                                   一归零它们会在抽屉还往里滑的那 300ms 里闪回来一圈。
                                   关抽屉时由 DrawerBridge 的 "close" 再归零。 */
                                Session.recProg = if (want) 1f else 0f
                                Session.recordsOpen = want
                                if (want) Platform.drawerOpen?.invoke() else Platform.drawerProg?.invoke(0f)
                                return@detectHorizontalDragGestures
                            }
                            if (Session.recProg > 0.32f) RecDrawer.open(scope) else RecDrawer.close(scope)
                            return@detectHorizontalDragGestures
                        }
                        Session.stepGlyphOverride = null
                        if (wPx > 0f) {
                            val base = -step.ordinal * wPx
                            val d = posX - base          /* 相对当前页的位移 */
                            val cur = step
                            /* ★ 甩动优先：够快就按方向翻，不看位移 ——
                               系统的翻页都是速度与位移二选一，不是只看位移。 */
                            val flick = abs(velPxS) > wPx * 1.15f
                            val want = when {
                                flick && velPxS < 0f -> cur.next()
                                flick && velPxS > 0f -> cur.prev()
                                d < -wPx * 0.22f -> cur.next()
                                d > wPx * 0.22f -> cur.prev()
                                else -> cur
                            }
                            val allowed = want == XlrStep.YI || Session.swipeUnlocked
                            step = if (allowed) want else cur
                        }
                        settling = true
                        scope.launch {
                            settle.snapTo(posX)
                            val target = -step.ordinal * wPx
                            settle.animateTo(target, tween(300, easing = KY_EASE_OUT))
                            posX = target
                            settling = false
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        if (blocked) return@detectHorizontalDragGestures
                        /* 起手点：这个重载的 onDragStart 不带坐标，就用第一帧触点位置当起手（差一两像素无妨） */
                        if (firstMove) {
                            firstMove = false
                            startX = change.position.x
                            startY = change.position.y
                            /* ：横滑（翻页 / 拉记录栏）也该先把输入法收回去，
                               不能只在「点空白」那一种情况下收。 */
                            dismissIme()
                        }
                        if (wPx <= 0f) return@detectHorizontalDragGestures
                        val cur = step
                        val base = -cur.ordinal * wPx
                        /* 壹页横拖的两种意思：**往右**= 记录抽屉，**往左**= 翻到贰页。
                           方向要攒够 6px 才敢定（头一两帧的抖动不算方向）。
                           ⚠ 原来不分方向，壹页上一拖就进抽屉模式、左拖被整条吃掉 ——
                              报的「解卦之后回到第一页切不动页」就是它。 */
                        var amt = amount
                        if (!drawerMode && !dirDecided && cur == XlrStep.YI && !Session.recordsOpen) {
                            pendingDx += amount
                            if (abs(pendingDx) < 6f) return@detectHorizontalDragGestures
                            dirDecided = true
                            amt = pendingDx
                            pendingDx = 0f
                            /* 起手落在「取数方式」那一排 ⇒ 让位给药丸横拖，不抢去开抽屉。
                               两个值都是**物理像素**（实测 startY=1878 / segY0=1887 / dens=2.75），直接比。 */
                            val inSeg = Session.segY0 >= 0f && startY >= Session.segY0 - 6f &&
                                startY <= Session.segY1 + 6f
                            if (amt > 0f && !inSeg) {
                                /* 往右 ⇒ 记录抽屉跟手（不是过了阈值才整块弹出来）：
                                   手指拖多少面板就露多少，松手后由 onDragEnd 决定吸到开还是关。 */
                                drawerMode = true
                                drawerAcc = 0f
                                /* 手指接手：掐掉缓动（两边抢同一个进度值会抖） */
                                RecDrawer.stop()
                                Session.recDragging = true
                                Session.recProg = 0f
                                Session.recordsOpen = true
                                /* 钝感震动：抽屉一被拖起来就闷一下 */
                                com.xlr.k.pages.Platform.haptic?.invoke(1)
                                return@detectHorizontalDragGestures
                            }
                        }
                        if (drawerMode) {
                            /* 往右 = 正；往左 = 往回缩，两边都跟手。
                                 手指拖动的距离先归一化，再走 easeOutQuad ——
                                 手挪一小段面板就出来一大截（1-(1-t)²），松手前的最后一段变「钝」。
                                 ⚠ 上一版把它当成「不跟手」删成了线性，其实不跟手是另一回事
                                   （进度根本没推给原生那层）。
                               ★ iOS：进度逐帧推给原生，否则拖动期间面板纹丝不动。 */
                            val panelPx = (wPx * 0.72f).coerceAtLeast(1f)
                            drawerAcc = (drawerAcc + amt).coerceIn(0f, panelPx)
                            val t = (drawerAcc / panelPx).coerceIn(0f, 1f)
                            Session.recProg = 1f - (1f - t) * (1f - t)
                            if (Platform.nativeDrawer) Platform.drawerProg?.invoke(Session.recProg)
                            return@detectHorizontalDragGestures
                        }
                        dragAcc += amt
                        /* 攒速度（指数平滑，避免最后一帧抖动影响判定） */
                        val nowMs = change.uptimeMillis
                        if (lastMs > 0L) {
                            val dtMs = (nowMs - lastMs).coerceIn(1L, 100L)
                            velPxS = velPxS * 0.55f + (amt / dtMs * 1000f) * 0.45f
                        }
                        lastMs = nowMs
                        val raw = posX + amt
                        val minX = -(XlrStep.JIE.ordinal) * wPx
                        val noNext = raw < minX - 0.5f || (raw < base && cur == XlrStep.JIE) ||
                            (raw < base && cur == XlrStep.YI && !Session.swipeUnlocked)
                        val noPrev = raw > 0.5f && cur == XlrStep.YI
                        posX = if (noNext || noPrev) {
                            /* 到头：越过边界的部分按阻尼折算 */
                            val over = raw - base
                            base + damp(over, EDGE_MAX * density)
                        } else raw
                        /* ★ 顶栏步进条「途中闪八卦」（旧 phoneAnim swipeMove 里那段）：
                           有目标页时，按拖动进度 0/25/50/75% 依次换成起手抽到的 4 个卦名；
                           到头（阻尼那一路）不换。 */
                        val d = raw - base
                        val hasTarget = if (d < 0f) (cur != XlrStep.JIE && Session.swipeUnlocked) else (cur != XlrStep.YI)
                        val grp = Session.baguaGrp
                        if (hasTarget && grp.isNotEmpty()) {
                            val prog = (abs(d) / swipeMinPx).coerceIn(0f, 1f)
                            val idx = (prog * 4f).toInt().coerceIn(0, grp.size - 1)
                            Session.stepGlyphOverride = grp[idx]
                            /* 刻度震动：八卦名每换一格抽一下（「让震动跟随八卦的凸显，
                               变成刻度感震动」） */
                            if (idx != lastTick) {
                                lastTick = idx
                                com.xlr.k.pages.Platform.haptic?.invoke(0)
                            }
                        } else {
                            Session.stepGlyphOverride = null
                        }
                    },
                )
            }
    ) {
        AuraLayer()
        /* 皮肤天光（敦煌/莫高窟 的沙壁暖光；其它皮肤只是竖向色阶）—— 必须画成 Canvas 子层 */
        SkinBackdropLayer()
        Box(
            modifier = Modifier.fillMaxSize().background(
                com.tencent.kuikly.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0f, 0f, 0f, 0.02f), Color(0f, 0f, 0f, 0.015f))
                )
            )
        )

        /* 三页各自整屏叠放，靠 translationX 排成一条横轨：
           当前页在 0，下一页在 +w，上一页在 −w；整轨跟着手指走。 */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { if (it.width > 0) wPx = it.width.toFloat() }
        ) {
            val cur = if (settling) settle.value else posX
            /* 记录抽屉右滑出来时，主页整块被推到右边（DS 就是这么做的：抽屉 72% 宽，
               主页左边缘正好贴着抽屉右边缘 1:1 跟手，两者之间不留缝、也没有「盖在上面」的假象）。 */
            /* 口径 2026-11-09：抽屉就**覆盖**在壹页上，不再把主页推出去
               （推出去的版本总在圆角外侧透出一条直角残留，覆盖反而干净）。 */
            val push = 0f
            /* 键盘让位：位移在 Kotlin 侧做（Swift 的 view transform 在 Kuikly 上不生效，r52 实测）。
               imeShift 是 pt，Kuikly 里 1dp = 1pt，转成 px 再写 translationY。 */
            val imeA by animateFloatAsState(
                targetValue = Session.imeShift,
                animationSpec = tween(250, easing = KY_EASE_OUT),
            )
            val imePx = with(LocalDensity.current) { imeA.dp.toPx() }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = cur + push; translationY = -imePx }) {
                YiContent(onCast = tryCast)
            }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = cur + wPx + push; translationY = -imePx }) {
                GuaContent(onNext = { step = XlrStep.JIE }, active = step == XlrStep.GUA, onBack = { step = XlrStep.YI })
            }
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = cur + wPx * 2f + push; translationY = -imePx }) {
                JieContent(active = step == XlrStep.JIE, onNewCast = {
                    Session.clear()
                    Session.evidenceOpen = false
                    EvOpen.clear()
                    Session.recId = ""
                    step = XlrStep.YI
                })
            }
        }

        /* ★ iOS 的「原生卡该不该露」放在这里算，别放 TopBar 里：
           那边是 `remember { derivedStateOf { … trackX … } }`，而 trackX 是**参数（值）**，
           「解完卦滑回壹页，有概率壹页元素全没、只剩背景」）。这里读的是 State，才跟得住。
           ⚠ 卡片该露的判据 = 壹页还占着屏幕（轨道位移没到 -w）+ 没有整屏遮罩。 */
        if (iosOnly()) {
            val cardNearYi by remember {
                androidx.compose.runtime.derivedStateOf {
                    wPx <= 0f || (if (settling) settle.value else posX) > -(wPx - 2f)
                }
            }
            val drawerAway = Session.recProg > 0.35f
            /* 允许键盘的场合：设置页（填 Key）/ 引导 / 皮肤菜单。这几个地方 Swift 的
               「多余键盘当场收掉」那道闸必须让路，否则点输入框没反应。
               ⚠ 以前是拿 barHidden 顶替的，它要绕一圈（TopBarFeed → 通知）才到 Swift，
                 中间那几帧正好把第一次点击的键盘收掉。 */
            val imeAllow = Session.settingsOpen || Session.guideOn || Session.skinMenuOpen
            LaunchedEffect(imeAllow) { Platform.imeAllowed?.invoke(imeAllow) }
            LaunchedEffect(cardNearYi, drawerAway, Session.cardShellOn, CastFx.playing,
                           Session.splashOn, Session.guideOn, Session.settingsOpen,
                           Session.skinMenuOpen) {
                /* ⛔ evidenceOpen 不算遮罩：断卦依据是**页内展开的一张卡**（JiePage 的 EvidenceCard），
                   不是整屏浮层。以前把它算进来 ⇒ 点开依据之后顶栏和起卦卡一直不回来
                   。 */
                val overlays = Session.guideOn || Session.settingsOpen || Session.skinMenuOpen ||
                    drawerAway
                Platform.askVisible?.invoke(
                    cardNearYi && !overlays && !CastFx.playing &&
                        (!Session.splashOn || Session.cardShellOn)
                )
            }
        }

        TopBarLive(step = step, wPx = wPx) { if (settling) settle.value else posX }
        SkinMenuOverlay()
        CastFxLayer()
        AskPicker()
        SettingsOverlay()
        RecordsOverlay()
        SplashOverlay()
        GuideOverlay()
        AppToast()
    }
}

private fun backMs(p: Float): Int = (max(0.2f, 0.4f - 0.18f * p) * 1000f).toInt()

/* ---------------- 系统返回键 ----------------
   ：「现在任意页面按手机原生返回会直接退回桌面，这可不对」。
   次序（从最上层往下收）：
     ① 放大图 / 选择面板 / 皮肤菜单 / 断卦依据浮层
     ② 设置页 ⇒ 收设置，回到「记录抽屉打开」那一态（设置是从抽屉点进来的）
     ③ 记录抽屉打开 ⇒ 收回抽屉
     ④ 壹贰叁页 ⇒ 弹一句「再按一下退出」，2.2 秒内再按才真退（Platform.exitApp）
   ⛔ 别在 Activity 里直接 finish。 */
private var backArmedAt = 0L

/** 根组合作用域（抽屉的收/开动画要用它）；RootScreen 里赋值 */
internal var pageScope: kotlinx.coroutines.CoroutineScope? = null

internal fun handleBack(): Boolean {
    if (Session.guideZoom != null) {
        Session.guideZoom = null
        return true
    }
    if (Session.moreOpen) {
        Session.moreOpen = false
        return true
    }
    if (Session.picker != null) {
        Session.picker = null
        return true
    }
    if (Session.skinMenuOpen) {
        Session.skinMenuOpen = false
        return true
    }
    if (Session.evidenceOpen) {
        Session.evidenceOpen = false
        return true
    }
    if (Session.settingsOpen) {
        Session.settingsOpen = false
        Session.settingsPage = ""
        /* 回到「记录抽屉打开」：抽屉直接给满进度（设置在它上面滑走，看不出这一跳） */
        Session.recDragging = false
        Session.recProg = 1f
        Session.recordsOpen = true
        return true
    }
    if (Session.recordsOpen) {
        val sc = pageScope
        if (sc != null) {
            RecDrawer.close(sc)
        } else {
            Session.recordsOpen = false
            Session.recProg = 0f
        }
        return true
    }
    val now = Clock.System.now().toEpochMilliseconds()
    if (backArmedAt > 0L && now - backArmedAt < 2200L) {
        backArmedAt = 0L
        Platform.exitApp?.invoke()
        return true
    }
    backArmedAt = now
    Session.say("再按一下退出")
    Platform.haptic?.invoke(0)
    return true
}

/* 到头阻尼：最多让出 EDGE_MAX，越推越沉 */
private fun damp(dx: Float, edgeMaxPx: Float): Float {
    val s = if (dx < 0f) -1f else 1f
    return s * edgeMaxPx * (1f - 1f / (1f + abs(dx) / (150f * edgeMaxPx / 54f)))
}

/* 收输入法（「不止点空白，点皮肤、点昼夜、点记录栏、左右滑动，都该收」）。
   统一走这里：键盘没挂就不做，避免多余 clearFocus。 */
internal fun dismissIme() {
    if (Platform.imeVisible) Platform.hideIme?.invoke()
}

@Composable
/* 薄壳：翻页位移逐帧变，只让这里重组，把值转交给 TopBar。 */
private fun TopBarLive(step: XlrStep, wPx: Float, track: () -> Float) {
    TopBar(step = step, trackX = track(), wPx = wPx)
}

@Composable
private fun TopBar(step: XlrStep, trackX: Float, wPx: Float) {
    val scope = rememberCoroutineScope()

    /* ★ iOS：顶栏交给原生画（唯一能「钉住不动」的做法）。
       Kuikly 整页会被键盘整体上移，顶栏在同一图层里跑不出去；
       原生顶栏挂在页面之外，页面怎么动它都不动。
       这里只留出同样的高度（46 + 40），并把状态推过去。 */
    /* ★ 原生顶栏浮在页面之上，凡是「整屏盖住页面」的层都要让它先躲开，
       否则开屏/引导/选择器/抽屉上会挂着一排按钮。 */
    /* ⚠ 用 derivedStateOf：recProg 每帧都在变，直接读它会让这一层（连三页一起）逐帧重组。
       derivedStateOf 只在**布尔结果翻面**时才通知读它的人。 */
    val barHidden by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            Session.splashOn || Session.guideOn ||
                /* iOS 的选择器是系统 sheet（半屏、上方页面还看得见），顶栏不必躲；
                   安卓自绘弹层是全屏遮罩，顶栏要躲开。 */
                (!iosOnly() && Session.picker != null) ||
                Session.settingsOpen || Session.skinMenuOpen ||
                Session.recProg > 0.35f
        }
    }
    if (iosOnly()) {
        /* 「原生卡该不该露」已经搬到 RootScreen 里算（那边读得到 State）；
           这里只管把顶栏状态推给原生、并让出同样的高度。 */
        LaunchedEffect(Session.stepGlyphOverride, step.ch, step.label, Skin.dark, barHidden) {
            TopBarFeed.onAction = { act ->
                when (act) {
                    "menu" -> {
                        dismissIme()
                        if (Platform.nativeDrawer) {
                            /* 从菜单按钮打开的也一样：先记成「开着」，
                               否则卡片/顶栏的让位判断看不到它（抽屉会盖在卡片上） */
                            Session.recordsOpen = true
                            Session.recProg = 1f
                            Platform.drawerOpen?.invoke()
                        } else RecDrawer.open(scope)
                    }
                    "skin" -> { dismissIme(); Session.skinMenuOpen = !Session.skinMenuOpen }
                    "daynight" -> { dismissIme(); Skin.toggleDayNight() }
                }
            }
            TopBarFeed.push(
                step = Session.stepGlyphOverride ?: step.ch,
                label = step.label,
                dark = Skin.dark,
                hidden = barHidden,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(46.dp))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp))
        }
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(46.dp))
        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            GlassSurface(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(40.dp)
                    .tapNoIndication { dismissIme(); RecDrawer.open(scope) },
                shape = RoundedCornerShape(999.dp),
            ) { MenuGlyph() }
            Box(modifier = Modifier.align(Alignment.Center)) {
                StepBadge(step = step.ch, label = step.label)
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
                /* 左：皮肤 —— 单独唤起皮肤菜单浮层（早期版本 .skinmenu；⛔ 不是进设置的外观页） */
                GlassSurface(
                    modifier = Modifier.width(43.dp).height(40.dp).tapNoIndication {
                        dismissIme()
                        Session.skinMenuOpen = !Session.skinMenuOpen
                    },
                    shape = RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp),
                ) { SkinGlyph() }
                /* 右：日夜 —— 照早期版本 #btnDN：深色套亮太阳（点了回浅色），浅色套亮月亮（点了进黑夜） */
                GlassSurface(
                    modifier = Modifier.width(43.dp).height(40.dp).tapNoIndication {
                        dismissIme()
                        Skin.toggleDayNight()
                    },
                    shape = RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp),
                ) { if (Skin.dark) SunGlyph() else MoonGlyph() }
            }
        }
    }
}
