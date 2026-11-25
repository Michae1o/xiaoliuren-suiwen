package com.xlr.k.pages

import android.animation.ValueAnimator
import android.os.Build

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import com.tencent.kuikly.compose.foundation.gestures.detectHorizontalDragGestures
import com.tencent.kuikly.compose.foundation.gestures.horizontalDrag
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxHeight
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.rememberLazyListState
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xlr.k.judge.Rec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/* 记录（旧 apps/mobile-web/src/components/Records.jsx + 2026-10-28 参照图）
   左侧抽屉：搜索框 → 页签「卦例 / 准则」→ 按天分组列表（所问 · ⋮）→ 回收站胶囊 → 设置。
   · 点一条 = 打开**详情**（早期版本 `Detail`：‹ 返回列表 / 所问 / 背景 / 起卦 / 当时的断辞 / 准不准·实际情况）
   · ⋮ = 贴行浮出菜单（早期版本 sheetActs：看这一卦 / 应验 / 没应验 / 删）
   · 列表左右滑切页；删除保留在 ⋮ / 长按菜单内
   ⚠ 还没搬：复制断辞、接着问、复盘（标「没应验」→ 模型说错在哪 → 写入准则）、准则列表、导出导入。 */

/** 记录抽屉的开关缓动（点开/点关走这里；手指拖时由手势直接写 Session.recProg，跟手）。
 *  非线性：开 = easeOutCubic（起手就给出去大半），关 = easeInCubic（先慢后快收回）。 */
internal object RecDrawer {
    private var job: kotlinx.coroutines.Job? = null
    var trashOpen by mutableStateOf(false)
        private set
    var trashMenuId by mutableStateOf("")
    var trashConfirmId by mutableStateOf("")

    fun openTrash() {
        trashOpen = true
        trashMenuId = ""
        trashConfirmId = ""
    }

    fun leaveTrash() {
        trashOpen = false
        trashMenuId = ""
        trashConfirmId = ""
    }

    fun handleTrashBack(): Boolean {
        if (!trashOpen) return false
        when {
            trashConfirmId.isNotEmpty() -> trashConfirmId = ""
            trashMenuId.isNotEmpty() -> trashMenuId = ""
            else -> leaveTrash()
        }
        return true
    }

    fun anim(scope: kotlinx.coroutines.CoroutineScope, target: Float) {
        job?.cancel()
        if (target > 0.5f && Session.recProg < 0.05f) {
            /* 从 0 打开（点顶栏菜单图标那条路）：重档震动一次；拖动松手吸附那条不再重复震 */
            Platform.haptic?.invoke(2)
        }
        if (target > 0.5f) Session.recordsOpen = true
        job = scope.launch {
            val from = Session.recProg
            if (kotlin.math.abs(target - from) < 0.002f) {
                Session.recProg = target
                Session.recordsOpen = target > 0.5f
                if (target <= 0.5f) leaveTrash()
                return@launch
            }
            val dur = if (target > from) 300 else 240
            var t = 0
            while (t <= dur) {
                if (Session.recDragging) return@launch
                val k = (t.toFloat() / dur).coerceIn(0f, 1f)
                val e = if (target > from) 1f - (1f - k) * (1f - k) * (1f - k) else k * k * k
                Session.recProg = from + (target - from) * e
                kotlinx.coroutines.delay(16)
                t += 16
            }
            Session.recProg = target
            Session.recordsOpen = target > 0.5f
            if (target <= 0.5f) leaveTrash()
        }
    }

    fun open(scope: kotlinx.coroutines.CoroutineScope) = anim(scope, 1f)
    fun close(scope: kotlinx.coroutines.CoroutineScope) = anim(scope, 0f)
}

@Composable
internal fun RecordsOverlay() {
    var mounted by remember { mutableStateOf(false) }
    val prog = Session.recProg
    LaunchedEffect(Session.recordsOpen, prog > 0.001f) {
        mounted = Session.recordsOpen || prog > 0.001f
    }
    if (!mounted) return

    var ver by remember { mutableStateOf(0) }
    var settledPage by remember { mutableStateOf(0) }
    var pagerPosition by remember { mutableStateOf(0f) }
    var pagerSettling by remember { mutableStateOf(false) }
    var pagerJob by remember { mutableStateOf<Job?>(null) }
    val pagerAnimation = remember { Animatable(0f) }
    // Kuikly requires the animated value to be read in composition, not only in graphicsLayer.
    val pageFrame = if (pagerSettling) pagerAnimation.value else pagerPosition
    var q by remember { mutableStateOf("") }
    var detailId by remember { mutableStateOf("") }
    /* 准则详情（跟记录一样：列表只出关键句，点进去看结论/依据/来源） */
    var ruleId by remember { mutableStateOf("") }
    val trashOpen = RecDrawer.trashOpen
    val caseList = rememberLazyListState()
    val ruleList = rememberLazyListState()
    var menuId by remember { mutableStateOf("") }
    var menuX by remember { mutableStateOf(0f) }      /* 锚点（根坐标 px） */
    var menuY by remember { mutableStateOf(0f) }
    var menuDots by remember { mutableStateOf(false) } /* true = 从 ⋮ 打开，菜单右沿对齐它 */
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }
    var panelW by remember { mutableStateOf(0f) }
    val dens = LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reducedMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
    val easedTrash by animateFloatAsState(
        targetValue = if (trashOpen) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 244, easing = KY_EASE_OUT),
    )
    val trashFrame = if (reducedMotion) { if (trashOpen) 1f else 0f } else easedTrash
    val trashMoving = if (trashOpen) trashFrame < .999f else trashFrame > .001f
    val drawerWidth = panelW
    val skipPagerAnimation by rememberUpdatedState(reducedMotion)

    fun settlePage(page: Int) {
        val from = if (pagerSettling) pagerAnimation.value else pagerPosition
        pagerJob?.cancel()
        menuId = ""
        pagerPosition = from
        fun commit() {
            val previousPage = settledPage
            settledPage = page
            pagerPosition = page.toFloat()
            if (page != previousPage) {
                if (page == 0) ruleId = "" else detailId = ""
            }
            pagerSettling = false
        }
        if (skipPagerAnimation || panelW <= 0f || kotlin.math.abs(from - page) < 0.001f) {
            commit()
            return
        }
        pagerJob = scope.launch {
            pagerAnimation.snapTo(from)
            pagerSettling = true
            val remaining = kotlin.math.abs(from - page)
            pagerAnimation.animateTo(page.toFloat(), tween((155f + 85f * remaining).toInt(), easing = KY_EASE_OUT))
            commit()
        }
    }

    val all = remember(ver, Session.recordsOpen, RecStore.caseRevision) { RecStore.load().reversed() }
    val dead = remember(ver, Session.recordsOpen, RecStore.caseRevision) { RecStore.loadDeleted() }
    val rules = remember(ver, Session.recordsOpen, RecStore.ruleRevision) { RecStore.loadRules() }
    val rows = remember(all, q) {
        if (q.isBlank()) all else all.filter { r ->
            ((r["q"] ?: "") + (r["duanci"] ?: "")).lowercase().contains(q.lowercase())
        }
    }
    val detail = if (detailId.isBlank()) null else rows.firstOrNull { it["id"] == detailId }
    val ruleDetail = if (ruleId.isBlank()) null else rules.firstOrNull { it["id"] == ruleId }
    val canPageByGesture by rememberUpdatedState(
        !trashOpen && !trashMoving && !RecallScreen.open && !CardSelection.active && menuId.isEmpty() &&
            (pagerSettling || if (settledPage == 0) detail == null else ruleDetail == null),
    )

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned {
        canvasW = it.size.width.toFloat()
        canvasH = it.size.height.toFloat()
    }) {
        /* 右边露出主界面那条：点一下收抽屉 */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0.07f, 0.06f, 0.05f, 0.10f * Session.recProg))
                .tapSwallow { RecDrawer.close(scope) }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .onGloballyPositioned { panelW = it.size.width.toFloat() }
                .graphicsLayer { translationX = -panelW * (1f - Session.recProg) }
                .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
                .glassMaterial(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp), drawer = true)
                /* ⚠ 面板自己吃掉空白处的点击：否则会**穿透到后面那层**（点了就关抽屉/跳回主界面）。
                   ★ 用 tapSwallow 不用 tapNoIndication：后者按下会把整块压到 0.82（长按=整屏变透明）。 */
                .tapSwallow { menuId = "" }

        ) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                Column(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = -trashFrame * drawerWidth
                        alpha = if (trashFrame >= 1f) 0f else 1f
                    },
                ) {
                    /* 搜索框 */
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .glassMaterial(RoundedCornerShape(19.dp), control = true)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchGlyph()
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f).height(20.dp)) {
                                if (q.isEmpty()) {
                                    Text_(text = "搜索卦例内容…", fontSize = 12f, lineHeight = 18f, color = KY_DIM2)
                                }
                                BasicTextField(
                                    value = q,
                                    onValueChange = { q = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 12f.sp, lineHeight = 18f.sp, color = KY_INK),
                                    cursorBrush = SolidColor(KY_INK),
                                )
                            }
                        }
                    }
                    val frame = pageFrame
                    val viewport = panelW
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                            // Claim only a horizontal swipe on the list/tab region. A vertical list scroll,
                            // long-press menu or native text selection keeps its original priority.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!canPageByGesture || panelW <= 0f) return@awaitEachGesture
                                var overSlop = 0f
                                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                    if (canPageByGesture) {
                                        overSlop = over
                                        change.consume()
                                    }
                                } ?: return@awaitEachGesture
                                val w = panelW
                                val origin = if (pagerSettling) pagerAnimation.value else pagerPosition
                                pagerJob?.cancel()
                                pagerSettling = false
                                menuId = ""
                                val firstDx = if (start.id == down.id) start.position.x - down.position.x else overSlop
                                // Include the first move after slop recognition; thereafter each pixel maps
                                // to exactly one pixel of content travel until the physical end of the track.
                                var totalDx = firstDx
                                pagerPosition = (origin - totalDx / w).coerceIn(0f, 1f)
                                var lastMs = start.uptimeMillis
                                var velocity = if (lastMs > down.uptimeMillis) {
                                    firstDx * 1000f / (lastMs - down.uptimeMillis)
                                } else 0f
                                val completed = horizontalDrag(start.id) { change ->
                                    val delta = change.position.x - change.previousPosition.x
                                    val nextMs = change.uptimeMillis
                                    if (nextMs > lastMs) {
                                        val speed = delta * 1000f / (nextMs - lastMs)
                                        velocity = velocity * .45f + speed * .55f
                                    }
                                    lastMs = nextMs
                                    totalDx += delta
                                    pagerPosition = (origin - totalDx / w).coerceIn(0f, 1f)
                                    change.consume()
                                }
                                if (!completed) {
                                    settlePage(settledPage)
                                } else {
                                    val minimumFling = 600.dp.toPx()
                                    val minimumDistance = 14.dp.toPx()
                                    val upMs = currentEvent.changes.maxOfOrNull { it.uptimeMillis } ?: lastMs
                                    val fling = upMs - lastMs <= 110L && kotlin.math.abs(velocity) > minimumFling &&
                                        kotlin.math.abs(totalDx) > minimumDistance && velocity * totalDx > 0f
                                    val shift = pagerPosition - origin
                                    val next = when {
                                        kotlin.math.abs(shift) > .24f || fling -> if (totalDx < 0f) 1 else 0
                                        else -> if (origin >= .5f) 1 else 0
                                    }
                                    settlePage(next)
                                }
                            }
                        },
                    ) {
                        RecordPagerTabs(
                            ruleCount = rules.size,
                            position = frame,
                            onTab = { page -> settlePage(page) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                            Box(
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    translationX = -frame * viewport
                                    alpha = if (viewport > 0f || settledPage == 0) 1f else 0f
                                },
                            ) {
                                when {
                                    /* ---- 详情（早期版本 Detail） ---- */
                                    detail != null -> Box(modifier = Modifier.fillMaxSize()) {
                                        RecDetail(
                                            r = detail,
                                            onChange = { ver++ },
                                            onBack = { detailId = "" },
                                            /* 早期版本写完准则会 sdbTab('rules')：详情收起、落在准则列表上 */
                                            onRuleWritten = { settlePage(1) },
                                        )
                                    }

                                    rows.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                        Text_(
                                            text = if (q.isBlank())
                                                "还没有记录。\n每起一卦都会自动记进这里。事后回来标一下准不准——错的卦才值钱。"
                                            else "没有匹配的记录。",
                                            fontSize = 11.5f,
                                            lineHeight = 21f,
                                            color = KY_DIM2,
                                        )
                                    }

                                    else -> LazyColumn(state = caseList, modifier = Modifier.fillMaxSize()) {
                                        var lastDay: String? = null
                                        rows.forEachIndexed { index, r ->
                                            val id = r["id"].orEmpty()
                                            val day = (r["t"] ?: "").take(10)
                                            if (day != lastDay) {
                                                lastDay = day
                                                item(key = "d-$index-$day") { DayHeader(day) }
                                            }
                                            item(key = "r-" + id.ifBlank { "missing-$index" }) {
                                                RecRow(
                                                    r = r,
                                                    onOpen = { detailId = id },
                                                    onMenu = { mx, my, dots ->
                                                        /* 长按记录 / 点 ⋮ 都走这里：重档震动一下 */
                                                        Platform.haptic?.invoke(2)
                                                        menuId = id
                                                        menuX = mx
                                                        menuY = my
                                                        menuDots = dots
                                                    },
                                                )
                                            }
                                        }
                                        item { Spacer(modifier = Modifier.height(12.dp)) }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    translationX = (1f - frame) * viewport
                                    alpha = if (viewport > 0f || settledPage == 1) 1f else 0f
                                },
                            ) {
                                    if (ruleDetail != null) {
                                        RuleDetail(
                                            r = ruleDetail,
                                            onBack = { ruleId = "" },
                                            onDel = { RecStore.delRule(ruleDetail["id"] ?: ""); ruleId = ""; ver++ },
                                        )
                                    } else if (rules.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                            Text_(
                                                text = "还没有准则。\n准则只从【你确认过的复盘】里来——不自动写、不偷偷学。",
                                                fontSize = 11.5f,
                                                lineHeight = 21f,
                                                color = KY_DIM2,
                                            )
                                        }
                                    } else {
                                        /* 列表只出关键句（那条准则本身）——依据、来源都收进详情 */
                                        LazyColumn(state = ruleList, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                                            rules.forEachIndexed { idx, r ->
                                                item(key = "rule-" + (r["id"] ?: idx.toString())) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .recordRowPress(onTap = { ruleId = r["id"] ?: "" })
                                                            .padding(horizontal = 10.dp, vertical = 13.dp),
                                                    ) {
                                                        Text_(
                                                            text = r["rule"].orEmpty().ifBlank { "未填写准则" },
                                                            fontSize = 13f,
                                                            lineHeight = 21f,
                                                            weight = 500,
                                                            maxLines = 2,
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text_(
                                                            text = "第 " + (idx + 1) + " 条 · " + (r["at"] ?: "") + (if ((r["gua"] ?: "").isNotEmpty()) " · " + r["gua"] else ""),
                                                            fontSize = 10f,
                                                            lineHeight = 16f,
                                                            color = KY_DIM2,
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                    }

                    /* 底栏：回收站胶囊 + 居中设置 */
                    if (!(settledPage == 0 && detail != null)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                                // The footer is outside the pager: only here is a left drag still
                                // the old close-drawer shortcut. System edge Back is untouched.
                                var start = 1f
                                var dx = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { start = Session.recProg; dx = 0f },
                                    onHorizontalDrag = { _, delta ->
                                        if (panelW <= 0f) return@detectHorizontalDragGestures
                                        dx += delta
                                        if (dx < 0f || Session.recDragging) {
                                            Session.recDragging = true
                                            Session.recProg = (start + dx / panelW).coerceIn(0f, 1f)
                                        }
                                    },
                                    onDragEnd = {
                                        if (Session.recDragging) {
                                            Session.recDragging = false
                                            if (Session.recProg < .68f) RecDrawer.close(scope) else RecDrawer.open(scope)
                                        }
                                    },
                                    onDragCancel = {
                                        if (Session.recDragging) {
                                            Session.recDragging = false
                                            RecDrawer.open(scope)
                                        }
                                    },
                                )
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .glassMaterial(RoundedCornerShape(18.dp), control = true)
                                        .tapNoIndication {
                                            menuId = ""
                                            dismissIme()
                                            CardSelection.cancel()
                                            RecDrawer.openTrash()
                                        }
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text_(
                                        text = "回收站 · " + dead.size + " 则",
                                        fontSize = 11.5f,
                                        lineHeight = 18f,
                                        color = KY_DIM2,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    RecordDrawerGlyph("enter")
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .tapNoIndication {
                                        RecDrawer.close(scope)
                                        dismissIme()
                                        Session.settingsOpen = true
                                    },
                                horizontalArrangement = com.tencent.kuikly.compose.foundation.layout.Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                /* ⛔ 别用 "⚙" 这个字符：iOS 会把它渲染成彩色 emoji（安卓是单色），
                                   两端看起来就是「图标变成 emoji 了」。改成 Canvas 画的齿轮。 */
                                GearIcon(color = KY_DIM)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text_(text = "设置", fontSize = 12.5f, lineHeight = 20f, color = KY_DIM)
                                /* 缺 Key 的小圆点（早期版本 #setDot：`.dot` 空心、配好了 `.dot.on` 变实心绿）
                                   —— K 里设置入口在抽屉底部，点就挂在这一行右端 */
                                Spacer(modifier = Modifier.width(6.dp))
                                val llmOk = LlmStore.missing(LlmStore.load()).isEmpty()
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (llmOk) KY_OK else Color.Transparent)
                                        .border(1.dp, if (llmOk) KY_OK else KY_DIM2, CircleShape),
                                )
                            }
                        }
                    }
                }

                if (trashOpen || trashFrame > .001f) {
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            translationX = (1f - trashFrame) * drawerWidth
                            alpha = if (drawerWidth > 0f || trashFrame >= 1f) 1f else 0f
                        }.tapSwallow { },
                    ) {
                        RecordsTrashPage(
                            dead = dead,
                            onBack = { RecDrawer.leaveTrash() },
                            onChanged = { ver++ },
                        )
                    }
                }
                if (trashMoving) Box(modifier = Modifier.fillMaxSize().tapSwallow { })

                /* ---- ⋮ 贴行浮出菜单（早期版本 .cr-menu / sheetActs） ---- */
                val rec = if (trashOpen || trashMoving || menuId.isBlank()) null else rows.firstOrNull { it["id"] == menuId }
                if (rec != null) {
                    /* ---- 位置：长按贴手指、点 ⋮ 对齐 ⋮；放不下就翻到上面 ---- */
                    val nItem = if (rec["ans"] == "model") 6 else 5
                    val mw = with(dens) { 150.dp.toPx() }
                    val mh = with(dens) { (41f * nItem + 8f).dp.toPx() }
                    val gap = with(dens) { 6.dp.toPx() }
                    var mx = if (menuDots) menuX - mw else menuX - with(dens) { 6.dp.toPx() }
                    mx = mx.coerceIn(
                        with(dens) { 8.dp.toPx() },
                        (canvasW - mw - with(dens) { 8.dp.toPx() }).coerceAtLeast(with(dens) { 8.dp.toPx() }),
                    )
                    var my = menuY + gap
                    val flip = canvasH > 0f && my + mh + with(dens) { 10.dp.toPx() } > canvasH
                    if (flip) my = (menuY - mh - gap).coerceAtLeast(with(dens) { 10.dp.toPx() })
                    /* ---- 入场：170ms 淡入 + 6dp 位移---- */
                    var menuShown by remember(menuId) { mutableStateOf(false) }
                    LaunchedEffect(menuId) { menuShown = true }
                    val mp by com.tencent.kuikly.compose.animation.core.animateFloatAsState(
                        targetValue = if (menuShown) 1f else 0f,
                        animationSpec = com.tencent.kuikly.compose.animation.core.tween(
                            170,
                            easing = com.tencent.kuikly.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
                        ),
                    )
                    /* ★
                       真凶不是材质，是下面那个 graphicsLayer 里直接读 mp。
                       Kuikly 只在 lambda 里读 state 时**不会**重新应用这一层 ⇒ alpha 停在首帧的
                       中间值（实测菜单区被压到 214/233，看着就是半透）。必须在组合作用域读一次。 */
                    val mpNow = mp
                    /* 点空白关菜单（这一层同时吃点击）。 */
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .tapSwallow { menuId = "" }
                    )
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .offset(x = with(dens) { mx.toDp() }, y = with(dens) { my.toDp() })
                            /* ★ alpha 必须用**组合作用域**里读出来的 mpNow，
                               不能在 lambda 里直接读 mp（见上面 mpNow 那行注释）。 */
                            .graphicsLayer {
                                alpha = mpNow
                                translationY = (1f - mpNow) * (if (flip) -6f else 6f) * dens.density
                            }
                            .clip(RoundedCornerShape(12.dp))
                            
                            .glassMaterial(RoundedCornerShape(12.dp), popup = true)
                            .padding(vertical = 4.dp)
                    ) {
                        MenuItem("看这一卦") {
                            menuId = ""; detailId = rec["id"] ?: ""
                        }
                        MenuItem("应验") {
                            RecStore.setFb(rec["id"] ?: "", "对"); menuId = ""; ver++
                        }
                        MenuItem("没应验") {
                            RecStore.setFb(rec["id"] ?: "", "错"); menuId = ""; ver++
                        }
                        /* 早期版本只有模型解过的才有「接着问」 */
                        if (rec["ans"] == "model") {
                            MenuItem("接着问") {
                                menuId = ""; detailId = rec["id"] ?: ""
                            }
                        }
                        MenuItem("复制断辞") {
                            val d = rec["duanci"] ?: ""
                            if (d.isNotEmpty()) Platform.copy?.invoke(plainDuanci(d))
                            menuId = ""
                            Session.say(if (d.isEmpty()) "这一卦没出过断辞。" else "断辞已复制。")
                        }
                        MenuItem("删除", danger = true) {
                            RecStore.del(rec["id"] ?: ""); menuId = ""; ver++
                        }
                    }
                }
            }
        }
    }
}

private fun recordQuestion(record: Rec): String =
    record["q"].orEmpty().trim().ifBlank { "未填写所问" }

/** 行平时不铺底；按下只浮一层当前皮肤的淡墨，不移动文字，也不把整个抽屉变透明。 */
@Composable
private fun Modifier.recordRowPress(
    onTap: () -> Unit,
    onLongPress: ((Offset) -> Unit)? = null,
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val tap = rememberUpdatedState(onTap)
    val long = rememberUpdatedState(onLongPress)
    val tint by animateFloatAsState(
        targetValue = if (pressed) 0.055f else 0f,
        animationSpec = tween(if (pressed) 90 else 130, easing = KY_EASE_OUT),
    )
    val shape = RoundedCornerShape(15.dp)
    return clip(shape).background(KY_INK.copy(alpha = tint))
        .pointerInput(onLongPress != null) {
            detectTapGestures(
                onTap = { tap.value() },
                onLongPress = if (onLongPress == null) null else { point -> long.value?.invoke(point) },
                onPress = {
                    pressed = true
                    try { tryAwaitRelease() } finally { pressed = false }
                },
            )
        }
}

/** 同一个抽屉内的下一级，没有第二个关闭整抽屉的按钮。 */
@Composable
private fun RecordsTrashPage(dead: List<Rec>, onBack: () -> Unit, onChanged: () -> Unit) {
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 49.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).recordRowPress(onTap = onBack),
                contentAlignment = Alignment.Center,
            ) {
                RecordDrawerGlyph("back")
            }
            Text_("回收站", fontSize = 18f, lineHeight = 28f, weight = 500)
        }
        if (dead.isEmpty()) {
            Text_(
                "回收站是空的。删掉的卦例会先留在这里，可以还原。",
                fontSize = 12f,
                lineHeight = 22f,
                color = KY_DIM2,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                dead.asReversed().forEachIndexed { index, record ->
                    val id = record["id"].orEmpty()
                    item(key = "trash-$index-$id") {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 9.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text_(
                                    text = recordQuestion(record),
                                    fontSize = 13f,
                                    lineHeight = 21f,
                                    weight = 500,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f),
                                )
                                if (id.isNotBlank()) {
                                    Box(
                                        modifier = Modifier.height(44.dp).recordRowPress(onTap = {
                                            RecDrawer.trashMenuId = ""
                                            RecDrawer.trashConfirmId = ""
                                            RecStore.restore(id)
                                            onChanged()
                                        }).padding(horizontal = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text_("还原", fontSize = 12f, lineHeight = 19f, color = KY_DIM)
                                    }
                                    Box(
                                        modifier = Modifier.size(42.dp).recordRowPress(onTap = {
                                            RecDrawer.trashConfirmId = ""
                                            RecDrawer.trashMenuId = if (RecDrawer.trashMenuId == id) "" else id
                                        }),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RecordDrawerGlyph("more")
                                    }
                                }
                            }
                            if (id.isNotBlank() && RecDrawer.trashConfirmId == id) {
                                Column(modifier = Modifier.fillMaxWidth().padding(start = 9.dp, bottom = 11.dp)) {
                                    Text_(
                                        "彻底删除后无法恢复。",
                                        fontSize = 11.5f,
                                        lineHeight = 19f,
                                        color = KY_DIM2,
                                    )
                                    Spacer(modifier = Modifier.height(7.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SetBtn("取消", small = true) { RecDrawer.trashConfirmId = "" }
                                        Spacer(modifier = Modifier.width(7.dp))
                                        SetBtn("确认彻底删除", danger = true, small = true) {
                                            RecDrawer.trashConfirmId = ""
                                            RecStore.purge(id)
                                            onChanged()
                                        }
                                    }
                                }
                            } else if (id.isNotBlank() && RecDrawer.trashMenuId == id) {
                                Row(modifier = Modifier.fillMaxWidth().padding(start = 9.dp, bottom = 8.dp)) {
                                    SetBtn("彻底删除…", danger = true, small = true) {
                                        RecDrawer.trashMenuId = ""
                                        RecDrawer.trashConfirmId = id
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
            }
        }
    }
}

@Composable
private fun RecordDrawerGlyph(kind: String) {
    val ink = KY_DIM
    Canvas(modifier = Modifier.size(if (kind == "enter" || kind == "more") 16.dp else 20.dp)) {
        fun at(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        fun stroke(a: Offset, b: Offset) = drawLine(ink, a, b, strokeWidth = size.width * .085f, cap = StrokeCap.Round)
        when (kind) {
            "back" -> {
                stroke(at(.24f, .50f), at(.80f, .50f))
                stroke(at(.24f, .50f), at(.51f, .23f))
                stroke(at(.24f, .50f), at(.51f, .77f))
            }
            "enter" -> {
                stroke(at(.39f, .24f), at(.65f, .50f))
                stroke(at(.65f, .50f), at(.39f, .76f))
            }
            "more" -> for (y in listOf(.25f, .50f, .75f)) {
                drawCircle(ink, radius = size.width * .059f, center = at(.50f, y))
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(41.dp)
            .tapNoIndication { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text_(
            text = label,
            fontSize = 12.5f,
            lineHeight = 20f,
            color = if (danger) Color(0.70f, 0.26f, 0.22f) else KY_INK,
        )
    }
}

/** 每行点击、长按和 ⋮ 保持不变；横向拖动由外层页轨接管。 */
@Composable
private fun RecRow(
    r: Rec,
    onOpen: () -> Unit,
    onMenu: (Float, Float, Boolean) -> Unit,
) {
    val dens = LocalDensity.current
    var qAt by remember { mutableStateOf(Offset.Zero) }
    var dotAt by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).height(56.dp)
                .onGloballyPositioned { qAt = it.localToRoot(Offset.Zero) }
                .recordRowPress(
                    onTap = onOpen,
                    onLongPress = { off -> onMenu(qAt.x + off.x, qAt.y + off.y, false) },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text_(text = recordQuestion(r), fontSize = 13f, lineHeight = 21f, weight = 500, maxLines = 2)
        }
        Box(
            modifier = Modifier.size(42.dp)
                .onGloballyPositioned { dotAt = it.localToRoot(Offset.Zero) }
                .tapNoIndication {
                    val sizePx = with(dens) { 42.dp.toPx() }
                    onMenu(dotAt.x + sizePx, dotAt.y + sizePx, true)
                },
            contentAlignment = Alignment.Center,
        ) {
            RecordDrawerGlyph("more")
        }
    }
}

/* 详情（早期版本 Detail：逐节照抄文案） */
@Composable
private fun RecDetail(r: Rec, onChange: () -> Unit, onBack: () -> Unit, onRuleWritten: () -> Unit = {}) {
    val id = r["id"] ?: ""
    val fb = r["fb"] ?: ""
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .tapNoIndication { onBack() }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text_(text = "‹ 返回列表", fontSize = 12f, lineHeight = 18f, color = KY_DIM)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text_(text = "复盘", fontSize = 10.5f, lineHeight = 16f, color = KY_DIM2)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    SelectableCardText(textKey = listOf(r, Session.recordsOpen)) {
                        Column {
                    SecLabel("所问")
                    Text_(text = r["q"] ?: "", fontSize = 13.5f, lineHeight = 23f)
                    val bg = r["bg"] ?: ""
                    if (bg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text_(
                            text = "背景", fontSize = 10.5f, lineHeight = 17f, weight = 600,
                            letterSpacing = 1.2f, color = KY_DIM2,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text_(text = bg, fontSize = 12f, lineHeight = 21f, color = KY_DIM)
                    }
                    SectionDivider()
                    SecLabel("卦体")
                    Text_(text = r["gua"] ?: "", fontSize = 13.5f, lineHeight = 23f, serif = true)
                    Text_(
                        text = "事类：" + (r["cat"] ?: "通用") + " · 记于 " + (r["t"] ?: ""),
                        fontSize = 10.5f,
                        lineHeight = 17f,
                        color = KY_DIM2,
                    )
                    SectionDivider()
                    SecLabel("当时的断辞")
                    val du = r["duanci"] ?: ""
                    if (du.isBlank()) {
                        Text_(text = "这一卦没出过断辞。", fontSize = 10.5f, lineHeight = 17f, color = KY_DIM2)
                    } else {
                        DocView(du)
                    }

                    /* ---- 追问：有才显示（顺序照 iOS：断辞 → 追问 → 是否应验） ---- */
                    val th = threadOf(r)
                    if (th.isNotEmpty()) {
                        SectionDivider()
                        SecLabel("追问（" + th.size + "）")
                        th.forEach { it2 ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text_(text = "问 · ", fontSize = 12.5f, lineHeight = 21f, color = KY_DIM2)
                                Text_(text = it2.first, fontSize = 12.5f, lineHeight = 21f, weight = 500)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text_(text = it2.second, fontSize = 12f, lineHeight = 20f, color = KY_DIM)
                            Spacer(modifier = Modifier.height(9.dp))
                        }
                    }

                        }
                    }

                    SectionDivider()
                    SecLabel("是否应验")
                    /* 口径：应验管的是这一卦所有的断辞 —— 当时的断辞 + 追问，一起算 */
                    Text_(
                        text = "这一卦的断辞（连追问一起）算不算应验",
                        fontSize = 10.5f, lineHeight = 17f, color = KY_DIM2,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SetBtn(if (fb == "对") "应验 ✓" else "应验", primary = fb == "对") {
                            RecStore.setFb(id, "对"); onChange()
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        SetBtn(if (fb == "错") "没应验 ✓" else "没应验", primary = fb == "错") {
                            RecStore.setFb(id, "错"); onChange()
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        SetBtn("删掉这则", danger = true) { RecStore.del(id); onChange(); onBack() }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SetLabel("实际情况")
                    SetField(r["note"] ?: "", { RecStore.setNote(id, it) }, ph = "实际结果如何？会用来复盘。")

                    /* ---- 复盘（标了「没应验」才有；早期版本会自动跑一次） ---- */
                    if (fb == "错") {
                        Spacer(modifier = Modifier.height(14.dp))
                        SetLabel("复盘")
                        RvArea(r = r, onChange = onChange, onRuleWritten = onRuleWritten)
                    }

                    /* ---- 接着问 ---- */
                    SectionDivider()
                    if (r["ans"] == "model") {
                        SecLabel("接着问")
                        FollowAsk(r = r, onChange = onChange)
                    } else {
                        SecLabel("接着问")
                        Text_(
                            text = "用模型解过这一卦之后，这里就能接着问。",
                            fontSize = 10.5f,
                            lineHeight = 18f,
                            color = KY_DIM2,
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun SecLabel(t: String) {
    /* 板块标题要「一眼看出是标题」：半粗 + 字距拉开 + 墨色略收（跟 iOS 同一条理念，
       颜色仍然只用本端的墨色，不引新色） */
    Text_(
        text = t,
        fontSize = 11f,
        lineHeight = 18f,
        weight = 600,
        letterSpacing = 1.2f,
        color = KY_INK.copy(alpha = 0.72f),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/* 板块之间的淡分隔线：用本端现有的线色压一点，不引新色 */
@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(15.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE.copy(alpha = 0.55f)))
    Spacer(modifier = Modifier.height(15.dp))
}

/* 准则详情：列表只出关键句，依据/来源都在这里看 */
@Composable
private fun RuleDetail(r: Rec, onBack: () -> Unit, onDel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.height(30.dp).tapNoIndication { onBack() }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text_(text = "‹ 返回准则", fontSize = 12f, lineHeight = 18f, color = KY_DIM)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text_(text = "准则", fontSize = 10.5f, lineHeight = 16f, color = KY_DIM2)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    SecLabel("结论")
                    Text_(text = r["rule"] ?: "", fontSize = 13.5f, lineHeight = 23f)
                    SectionDivider()
                    SecLabel("依据")
                    val why = r["why"] ?: ""
                    Text_(
                        text = if (why.isEmpty()) "这条没留下依据。" else why,
                        fontSize = 12f, lineHeight = 21f,
                        color = if (why.isEmpty()) KY_DIM2 else KY_DIM,
                    )
                    SectionDivider()
                    SecLabel("来源")
                    val gua = r["gua"] ?: ""
                    if (gua.isNotEmpty()) {
                        Text_(text = "这一卦：" + gua, fontSize = 12f, lineHeight = 20f, color = KY_DIM)
                    }
                    val q0 = r["q"] ?: ""
                    if (q0.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text_(text = "所问：" + q0, fontSize = 12f, lineHeight = 20f, color = KY_DIM)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text_(text = "记于 " + (r["at"] ?: ""), fontSize = 10.5f, lineHeight = 17f, color = KY_DIM2)
                    SectionDivider()
                    SetBtn("删掉这条准则", danger = true, small = true) { onDel() }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: String) {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    fun p(x: Int) = if (x < 10) "0$x" else "$x"
    val td = "" + today.year + p(today.monthNumber) + p(today.dayOfMonth)
    val ydDate = kotlinx.datetime.LocalDate.fromEpochDays(today.date.toEpochDays() - 1)
    val yd = "" + ydDate.year + p(ydDate.monthNumber) + p(ydDate.dayOfMonth)
    val label = when (day.replace("-", "")) {
        "" -> "日期不详"
        td -> "今天"
        yd -> "昨天"
        else -> {
            val mm = day.drop(5).take(2).trimStart('0')
            val dd = day.drop(8).take(2).trimStart('0')
            if (day.length >= 8 && day.take(4) == td.take(4)) mm + " 月 " + dd + " 日" else day
        }
    }
    Text_(
        text = label,
        fontSize = 11f,
        lineHeight = 18f,
        color = KY_DIM2,
        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** 两个常驻等宽标签，只有一根短线，和内容共用同一个进度。 */
@Composable
private fun RecordPagerTabs(ruleCount: Int, position: Float, onTab: (Int) -> Unit) {
    val accent = KY_ACCENT
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            RecTab("卦例", position < .5f, Modifier.weight(1f)) { onTab(0) }
            RecTab("准则 $ruleCount", position >= .5f, Modifier.weight(1f)) { onTab(1) }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            val x = size.width * (.25f + position * .5f)
            val half = 9.dp.toPx()
            drawLine(accent, Offset(x - half, center.y), Offset(x + half, center.y),
                strokeWidth = size.height, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun RecTab(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(42.dp).recordRowPress(onTap = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text_(text = label, fontSize = 12.5f, lineHeight = 20f,
            weight = if (on) 500 else 400, color = if (on) KY_INK else KY_DIM2)
    }
}

@Composable
private fun SearchGlyph() {
    Box(modifier = Modifier.width(13.dp).height(13.dp)) {
        Box(
            modifier = Modifier
                .width(9.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .border(1.2f.dp, KY_DIM2, RoundedCornerShape(5.dp))
                .align(Alignment.TopStart)
        )
        Box(
            modifier = Modifier
                .width(1.2f.dp)
                .height(5.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = -45f }
                .background(KY_DIM2)
        )
    }
}

/* 齿轮图标（早期版本是 "⚙" 字符）——
   ⛔ 别再用那个字符：iOS 上它会被渲染成彩色 emoji、安卓是单色，两端对不上。
   形状：8 根齿（从 0.52r 伸到 0.96r）+ 一圈描边，中间留空就是孔。
   ⚠ 纯色 drawLine/drawCircle + Stroke —— 项目里已验证能用（断辞页的复制图标同款画法）。 */
@Composable
private fun GearIcon(color: Color, d: Dp = 11.dp) {
    Canvas(modifier = Modifier.size(d)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width / 2f
        for (i in 0 until 8) {
            val a = (i * 45.0) * kotlin.math.PI / 180.0
            val ca = kotlin.math.cos(a).toFloat()
            val sa = kotlin.math.sin(a).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + ca * r * 0.52f, cy + sa * r * 0.52f),
                end = Offset(cx + ca * r * 0.96f, cy + sa * r * 0.96f),
                strokeWidth = r * 0.22f,
            )
        }
        drawCircle(
            color = color,
            radius = r * 0.42f,
            center = Offset(cx, cy),
            style = Stroke(r * 0.30f),
        )
    }
}
