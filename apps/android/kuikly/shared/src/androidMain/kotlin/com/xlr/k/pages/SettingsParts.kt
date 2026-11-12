package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.gestures.detectHorizontalDragGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.layout.positionInParent
import com.tencent.kuikly.compose.ui.layout.positionInRoot
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlinx.coroutines.launch

/* 设置（旧 apps/mobile-web/src/components/SettingsScreen.jsx）
   早期版本是一页式：左栏点哪一栏、右栏 scrollIntoView 滚到那一段。
     要丝滑过渡，**不要总览页**。所以这里是「顶栏 tab + 内容切换」，六个栏各自一份内容。 */

internal data class SetPage(val k: String, val n: String)

internal val SET_PAGES = listOf(
    SetPage("llm", "大模型"),
    SetPage("skin", "外观"),
    SetPage("data", "数据"),
    SetPage("about", "关于"),
    SetPage("faq", "常见问题"),
    SetPage("note", "说明"),
)

@Composable
internal fun SettingsOverlay() {
    var mounted by remember { mutableStateOf(false) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Session.settingsOpen) {
        if (Session.settingsOpen) {
            mounted = true
            p.animateTo(1f, tween(280, easing = KY_EASE_OUT))
        } else if (mounted) {
            p.animateTo(0f, tween(200, easing = KY_EASE_IN))
            mounted = false
        } else {
            /* 兜底：设置早就关着（组件被回收后重建），别让雾层一直不挂 */
            Session.settingsSwiping = false
        }
    }
    if (!mounted) return

    val sheetPx = with(LocalDensity.current) { 2412.dp.toPx() }
    val tabDp = with(LocalDensity.current) { 1.dp.toPx() }
    println("XLRDBG SettingsOverlay compose tab=" + Session.settingsTab)

    /* 选中的栏（挂在 Session 上：局部 remember 在这层壳里写完不触发重组） */
    val tab = Session.settingsTab
    var tabX by remember { mutableStateOf(0f) }
    var tabW by remember { mutableStateOf(0f) }
    val barX = remember { Animatable(0f) }
    val barW = remember { Animatable(0f) }

    /* 手指按着 / 正在收尾：这段时间内容 alpha 恒为 1（跟手滑动不该看着像在淡出） */
    var swiping by remember { mutableStateOf(false) }
    /* 同步给雾层：拖动期间它整层不挂（设置页不透明，雾看不见，白合成 9 层全屏大图） */
    LaunchedEffect(swiping) { Session.settingsSwiping = swiping }

    /* 内容切换（点顶栏 tab）：淡入 + 从右边轻轻推上来（非线性）。
       滑动切栏不走这条路（那是下面那条跟手轨道）。 */
    val cp = remember { Animatable(1f) }
    /* 这一次切栏是不是滑动划出来的（是 ⇒ 画面已经在正中，别再淡一次，否则会闪一下） */
    var fromSwipe by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (fromSwipe) {
            fromSwipe = false
            cp.snapTo(1f)
            swiping = false
        } else {
            cp.snapTo(0f)
            cp.animateTo(1f, tween(240, easing = KY_EASE_OUT))
        }
    }
    /* 横滑跟手：内容整个跟着手指走，松手再过阈值翻页（边界有阻尼，收尾 easeOut）。
       。 */
    var swDx by remember { mutableStateOf(0f) }
    var contentW by remember { mutableStateOf(0f) }
    /* 屏外邻栏（提前挂好，见下面三格轨道的注释） */
    var warmL by remember { mutableStateOf<String?>(null) }
    var warmR by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var glideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /* 收尾动画：跟帧走。⛔ 别再手搓 `delay(16)` 循环 —— 它和渲染帧不对齐，
       越拖越散，本身就会一阵一阵的。 */
    fun glideTo(from: Float, target: Float, dur: Int, then: suspend () -> Unit) {
        glideJob?.cancel()
        val job = scope.launch {
            swDx = from
            val a = Animatable(from)
            a.animateTo(target, tween(dur, easing = KY_EASE_OUT)) { swDx = value }
            swDx = target
            then()
        }
        glideJob = job
    }

    /* 轻扫判定：原生手势「快划一下就该翻页」，Kuikly 不给 velocity，自己差分攒一个。
       ⚠ change.uptimeMillis 是毫秒；amount 是像素 ⇒ 单位 px/s。 */
    var lastMs by remember { mutableStateOf(0L) }
    var swipeVel by remember { mutableStateOf(0f) }

    /* 邻栏预挂：每次切栏后隔一拍再挂（别跟淡入/换主体抢同一帧）。
       挡了一帧。提前把左右邻栏挂在屏外一格，滑的时候它已经画好了，翻过去是现成的。
       ⚠ 这里**不要先置 null**：滑动切栏刚用完邻栏，立刻清掉会让居中那格在换主体前消失一帧
         ⇒ 屏幕上闪一下上一页。直接覆盖成新邻栏即可。 */
    LaunchedEffect(tab) {
        kotlinx.coroutines.delay(340)
        val i = SET_PAGES.indexOfFirst { it.k == tab }
        warmL = SET_PAGES.getOrNull(i - 1)?.k
        warmR = SET_PAGES.getOrNull(i + 1)?.k
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = sheetPx * (1f - p.value) }
            .background(KY_BG)
    ) {
        Spacer(modifier = Modifier.height(46.dp))
        /* 早期版本 `.sethead`（legacy.css）：**一行** flex，gap 14、padding 13/22、底下一条细线 ——
           [← 返回 胶囊] [设置] ── [版本号] */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .glassMaterial(RoundedCornerShape(999.dp), control = true)
                    .tapNoIndication { Session.settingsOpen = false }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text_(text = "← 返回", fontSize = 13f, lineHeight = 18f, color = KY_INK)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text_(
                text = "设置",
                fontSize = 15f,
                lineHeight = 22f,
                weight = 600,
                letterSpacing = 1.5f,
                color = KY_INK,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text_(
                text = "v" + (Platform.version ?: "0.1"),
                fontSize = 11f,
                lineHeight = 18f,
                letterSpacing = 0.66f,
                color = KY_DIM2,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
        /* 顶栏 tab：一行六格，选中项下面一条强调色细条（滑过去） */
        Column(modifier = Modifier.fillMaxWidth().glassMaterial(RoundedCornerShape(0.dp))) {
            /* ⚠ 指示条位置**别用坐标反推**：Kuikly 里 positionInRoot/positionInParent 拿到的是相对
               没和上面的字对齐」，实测中心差 21px ≈ 7dp）。
               改成：六格等宽（weight 1f，行内不留边距）⇒ 第 i 格的中心 = 行宽/6*(i+0.5)，
               指示条宽取**文字实测宽**、按这个中心摆，天然压在字下方。 */
            var rowW by remember { mutableStateOf(0f) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rowW = it.size.width.toFloat() }
            ) {
                SET_PAGES.forEach { pg ->
                    val on = pg.k == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .tapNoIndication {
                                if (!on) Session.settingsTab = pg.k
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text_(
                            text = pg.n,
                            fontSize = 13f,
                            lineHeight = 20f,
                            weight = if (on) 500 else 400,
                            color = if (on) KY_INK else KY_DIM2,
                            modifier = Modifier.onGloballyPositioned {
                                if (pg.k == tab) tabW = it.size.width.toFloat()
                            },
                        )
                    }
                }
            }
            /* 指示条：第 i 格中心 − 文字半宽 ⇒ 压在这格文字正下方。
                 ⇒ 不做「当帧瞬移」，也不做原来那 280ms 慢悠悠：**短时长 + 强 ease-out**
                    （KY_EASE_OUT = cubic-bezier(.22,1,.36,1)：起手就把大部分路走完，尾巴轻轻收住），
                    看上去是被手带过去、拖一下停稳，而不是「等页面切完才慢慢挪」。 */
            val tabIdx = SET_PAGES.indexOfFirst { it.k == tab }.coerceAtLeast(0)
            LaunchedEffect(tabIdx, rowW, tabW) {
                if (rowW > 0f && tabW > 0f) {
                    val cell = rowW / SET_PAGES.size.toFloat()
                    val tx = cell * tabIdx + (cell - tabW) / 2f
                    /* ⚠ 两段动画**同时起**：原来先等宽度那 160ms 走完，位置才出发 ⇒ 看着就是「线段慢半拍」。
                       宽度和位移并发，位移从第一帧就在动，再靠 ease-out 的强起手把大部分路走完。 */
                    launch { barW.animateTo(tabW, tween(150, easing = KY_EASE_OUT)) }
                    barX.animateTo(tx, tween(190, easing = KY_EASE_OUT))
                }
            }
            /* 下划线指示条 */
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .width((barW.value / tabDp).dp)
                    .graphicsLayer { translationX = barX.value }
                    .background(KY_ACCENT)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
        /* 三格轨道：[上一栏][当前栏][下一栏]。邻栏（warmL/warmR）提前挂在屏外一格，
           手指一拖它已经画好了 —— 翻过去是现成的，不会现场组合、也就不会卡那一帧。 */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onGloballyPositioned { contentW = it.size.width.toFloat() }
        ) {
            warmL?.let { k ->
                key(k) {
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = swDx - contentW }) {
                        SetPageBody(k)
                    }
                }
            }
            warmR?.let { k ->
                key(k) {
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = swDx + contentW }) {
                        SetPageBody(k)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        /* 滑动期间不淡（跟手滑看着像淡出很怪）；点 tab 才走 cp 的淡入 */
                        alpha = if (swiping) 1f else cp.value
                        translationX = (1f - cp.value) * 36f + swDx
                    }
                    /* 左右滑切栏：**跟手** —— 内容整个跟着手指走；
                       拖过头有阻尼压着（越拖越沉），松手过 22% 宽就翻栏，邻栏跟着一起滑到正中。
                       竖向滚动不受影响（只吃横向手势）。 */
                    .pointerInput(tab, contentW) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                glideJob?.cancel()
                                lastMs = 0L
                                swipeVel = 0f
                                swiping = true
                            },
                            onDragEnd = {
                                val th = contentW * 0.15f
                                /* 轻扫也能翻：速度够快 + 至少有 10px 位移（防止纯抖动误判） */
                                val fast = kotlin.math.abs(swipeVel) > 900f && kotlin.math.abs(swDx) > 10f
                                if (contentW > 0f && (kotlin.math.abs(swDx) > th || fast)) {
                                    val dir = if (swDx < 0f) 1 else -1   /* +1 = 往左拖 ⇒ 下一栏 */
                                    val i = SET_PAGES.indexOfFirst { it.k == tab }
                                    val j = i + dir
                                    if (j in SET_PAGES.indices) {
                                        /* 分两拍，别压在同一帧：
                                           ① 先滑到邻栏正中（此刻主体在屏外一格）；
                                           ② 到位后**先换主体**（新栏在屏外开始组合，看不见），
                                              等两三帧组合完，再把轨道归位。
                                           ⛔ 原来「换主体 + 归位」同拍 ⇒ 新栏组合那一帧会闪一下上一页
                                              。 */
                                        glideTo(swDx, -dir * contentW, 200) {
                                            fromSwipe = true
                                            Session.settingsTab = SET_PAGES[j].k
                                            kotlinx.coroutines.delay(48)
                                            swDx = 0f
                                        }
                                    } else {
                                        glideTo(swDx, 0f, 220) { swiping = false }
                                    }
                                } else {
                                    glideTo(swDx, 0f, 220) { swiping = false }
                                }
                            },
                            onDragCancel = { glideTo(swDx, 0f, 220) { swiping = false } },
                            onHorizontalDrag = { change, amount ->
                                /* 攒速度（px/s）：轻扫判定用 */
                                val tNow = change.uptimeMillis
                                if (lastMs > 0L && tNow > lastMs) {
                                    val inst = amount * 1000f / (tNow - lastMs).toFloat()
                                    swipeVel = swipeVel * 0.6f + inst * 0.4f
                                }
                                lastMs = tNow
                                if (contentW <= 0f) return@detectHorizontalDragGestures
                                val next = swDx + amount
                                /* 到头（第一栏再往右 / 最后一栏再往左）按阻尼折算，最多让出 18% 宽 */
                                val i = SET_PAGES.indexOfFirst { it.k == tab }
                                val atStart = i <= 0 && next > 0f
                                val atEnd = i >= SET_PAGES.size - 1 && next < 0f
                                swDx = if (atStart || atEnd) {
                                    val lim = contentW * 0.18f
                                    val s = if (next < 0f) -1f else 1f
                                    s * lim * (1f - 1f / (1f + kotlin.math.abs(next) / (lim * 2.2f)))
                                } else next
                            },
                        )
                    }
            ) {
                key(tab) { SetPageBody(tab) }
            }
        }
    }
}

/* 设置里的一栏（六选一）。三格轨道里当前栏与左右邻栏都调它 */
@Composable
private fun SetPageBody(k: String) {
    when (k) {
        "llm" -> LlmPage()
        "skin" -> SkinPage()
        "data" -> DataPage()
        "about" -> AboutPage()
        "faq" -> FaqPage()
        "note" -> NotePage()
    }
}

/* ---------------- 下面这些小件各栏共用 ---------------- */

@Composable
internal fun SetSecTitle(t: String, top: Int = 4) {
    Text_(
        text = t,
        fontSize = 12.5f,
        lineHeight = 20f,
        weight = 500,
        modifier = Modifier.padding(top = top.dp, bottom = 6.dp),
    )
}

@Composable
internal fun SetLead(t: String) {
    Text_(text = t, fontSize = 11.5f, lineHeight = 19f, color = KY_DIM2)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
internal fun SetHint(t: String) {
    Text_(
        text = t,
        fontSize = 11f,
        lineHeight = 18f,
        color = KY_DIM2,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun SetLabel(t: String) {
    Text_(
        text = t,
        fontSize = 11.5f,
        lineHeight = 18f,
        color = KY_DIM2,
        modifier = Modifier.padding(bottom = 5.dp),
    )
}

@Composable
internal fun SetField(value: String, onChange: (String) -> Unit, hint: String = "", ph: String = "") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(veil(0.55f))
            .border(Skin.borderW, KY_CARD_LINE, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        /* 占位文字得自己画：Kuikly 的 BasicTextField 没有 placeholder 参数。
            占位字一换行就被 .clip() 切掉第二行（截图上是「复盘要用它」的「它」只露上半截）。
            口径＝任何时候都只显示一行 ⇒ 占位字 maxLines=1、输入本身 singleLine=true。 */
        if (value.isEmpty() && ph.isNotEmpty()) {
            Text_(
                text = ph,
                fontSize = 13f,
                lineHeight = 20f,
                color = KY_DIM2,
                maxLines = 1,
                modifier = Modifier.padding(start = 1.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 13f.sp, lineHeight = 20f.sp, color = KY_INK),
            cursorBrush = SolidColor(KY_INK),
            singleLine = true,
        )
    }
    if (hint.isNotEmpty()) SetHint(hint)
}

/* 设置/记录里所有小按钮共用这一个：
   · 形状＝整圆胶囊（不是小圆角方块），高度 34 / 紧凑 30；
   · 次按钮**不铺底色**，只留 1px 极淡描边 + 深灰字 —— 原来「白 .55 底 + 白 .8 边」在浅底上是一块糊的灰；
   · 主按钮＝墨色实心 + 米白字，无描边（描边和底同时上就是「三件套」，最显廉价）；
   · 破坏性动作（danger）＝只把**字与描边**换成朱砂，不铺红底；
   · 按下：缩到 0.965 + 底浮一点点、描边加深，130~150ms 走 KY_EASE_OUT（口径：动画一律非线性）。 */
@Composable
internal fun SetBtn(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    var press by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(
        if (press && enabled) 0.965f else 1f,
        tween(130, easing = KY_EASE_OUT)
    )
    val base = if (primary) KY_ACCENT else if (danger) KY_DANGER else KY_INK
    val fillA by animateFloatAsState(
        when {
            !enabled -> 0f
            primary -> 1f
            press -> if (danger) 0.09f else 0.06f
            else -> 0f
        },
        tween(150, easing = KY_EASE_OUT)
    )
    val lineA by animateFloatAsState(
        when {
            !enabled -> 0.06f
            primary -> 0f
            press -> if (danger) 0.75f else 0.22f
            else -> if (danger) 0.55f else 0.12f
        },
        tween(150, easing = KY_EASE_OUT)
    )
    val shape = RoundedCornerShape(Skin.rPill)
    /* 卡通那套「实黑粗边 + 700 字重 + 实心 accent」随 2026-11-09 下架一起没了：
       现在所有皮肤都走淡描边 + 玻璃面。 */
    val lineColor = Color(base.red, base.green, base.blue, lineA)
    val fillColor = Color(base.red, base.green, base.blue, fillA)
    /* ★
       1.2dp 的 accent 描边（0.55，按下 0.85）+ accent 字 → 深浅皮肤都自带对比。 */
    val solidPrimary = primary
    Box(
        modifier = Modifier
            .height(if (small) 30.dp else 34.dp)
            .clip(shape)
            .then(
                when {
                    solidPrimary -> Modifier
                        .glassMaterial(shape, control = true, primary = false,
                            pressed = if (press && enabled) 1f else 0f)
                        .border(1.2.dp, Skin.def.accent.copy(alpha = if (press && enabled) 0.85f else 0.55f), shape)
                    danger -> Modifier.background(fillColor).border(Skin.borderW, lineColor, shape)
                    else -> Modifier.glassMaterial(shape, control = true, primary = false,
                        pressed = if (press && enabled) 1f else 0f)
                }
            )
            .tapPressable(onPress = { p -> press = p && enabled }, onTap = { if (enabled) onClick() })
            .padding(horizontal = if (small) 12.dp else 15.dp)
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
                alpha = if (enabled) 1f else 0.8f
            },
        contentAlignment = Alignment.Center,
    ) {
        Text_(
            text = label,
            fontSize = if (small) 11.5f else 12.5f,
            lineHeight = if (small) 18f else 20f,
            weight = 500,
            letterSpacing = 0.4f,
            color = when {
                !enabled -> KY_DIM
                primary -> Skin.def.accent
                danger -> KY_DANGER
                else -> KY_DIM
            },
        )
    }
}
