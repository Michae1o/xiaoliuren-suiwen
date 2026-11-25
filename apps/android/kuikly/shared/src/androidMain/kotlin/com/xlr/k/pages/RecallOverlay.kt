package com.xlr.k.pages

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.fadeIn
import com.tencent.kuikly.compose.animation.fadeOut
import com.tencent.kuikly.compose.animation.slideInVertically
import com.tencent.kuikly.compose.animation.slideOutVertically
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.gestures.awaitEachGesture
import com.tencent.kuikly.compose.foundation.gestures.awaitFirstDown
import com.tencent.kuikly.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import com.tencent.kuikly.compose.foundation.gestures.horizontalDrag
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

internal object RecallScreen {
    var open by mutableStateOf(false)
        private set

    var period: RecallReminderPeriod? = null
        private set
    private var backToAbout = false

    fun enterFromAbout() {
        dismissIme()
        CardSelection.cancel()
        backToAbout = true
        period = null
        Session.settingsOpen = false
        open = true
    }

    fun enterReminder(target: RecallReminderPeriod) {
        backToAbout = false
        period = target
        open = true
    }

    fun dismiss() {
        open = false
        period = null
        if (backToAbout) {
            backToAbout = false
            Session.settingsTab = "about"
            Session.settingsOpen = true
        }
    }

    fun back(): Boolean {
        if (!open) return false
        dismiss()
        return true
    }
}

private val fixedInk = RecallInk(Color(0xFF101923), Color(0xFFF1D7A8), Color(0xFF9DB6C9), Color(0xFF3D617D))
private val recallFrameNames = listOf("卦数", "事类", "实际情况")
private val paper = Color(0xFFF4F1E8)

/** Read-only local report, with no model request. */
@Composable
internal fun RecallOverlay() {
    val reducedMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
    if (reducedMotion) {
        if (RecallScreen.open) RecallOverlayContent()
        return
    }
    AnimatedVisibility(
        visible = RecallScreen.open,
        enter = fadeIn(tween(275, easing = KY_EASE_OUT)) +
            slideInVertically(tween(305, easing = KY_EASE_OUT)) { it / 30 },
        exit = fadeOut(tween(210, easing = KY_EASE_IN)) +
            slideOutVertically(tween(235, easing = KY_EASE_IN)) { it / 40 },
    ) {
        RecallOverlayContent()
    }
}

@Composable
private fun RecallOverlayContent() {
    DisposableEffect(Unit) {
        val update = Platform.setRecallStatusBackdrop
        update?.invoke(true)
        onDispose { update?.invoke(false) }
    }
    val snapshot = remember { RecStore.loadRaw().map { it.toMap() } }
    val now = remember { Clock.System.now() }
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { now.toLocalDateTime(zone).date }
    val target = remember { RecallScreen.period }
    var month by remember { mutableStateOf(target?.monthly == true) }
    val anchor = target?.takeIf { it.monthly == month }?.anchor
    val season = remember(anchor) {
        recallSeason(anchor?.let { LocalDateTime(it.year, it.monthNumber, it.dayOfMonth, 12, 0).toInstant(zone) } ?: now)
    }
    val summary = remember(month, anchor) { buildRecallSummary(snapshot, anchor ?: today, month) }
    val trace = remember(summary) { recallTrace(summary) }
    val term = season.term.takeIf { month }
    val ink = when {
        term == "秋分" -> RecallInk(Color(0xFF101B2A), Color(0xFFEFDBB7), Color(0xFFBED3DF), Color(0xFF607D96))
        month -> RecallInk(Color(season.ink), Color(season.accent), Color(season.second), Color(season.glow))
        else -> fixedInk
    }
    val reducedMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
    val scope = rememberCoroutineScope()
    var width by remember { mutableStateOf(0f) }
    var dragPosition by remember { mutableStateOf(0f) }
    var settledPage by remember { mutableStateOf(0) }
    var animating by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }
    val glide = remember { Animatable(0f) }
    val position = if (animating) glide.value else dragPosition
    val measuredWidth = width
    val selected = position.roundToInt().coerceIn(0, 2)
    fun settle(target: Int) {
        val page = target.coerceIn(0, 2)
        val from = if (animating) glide.value else dragPosition
        motion?.cancel()
        dragPosition = from
        fun finish() {
            settledPage = page
            dragPosition = page.toFloat()
            animating = false
        }
        if (reducedMotion || abs(page - from) < .001f) {
            finish()
        } else {
            motion = scope.launch {
                glide.snapTo(from)
                animating = true
                glide.animateTo(page.toFloat(), tween((170 + abs(page - from) * 95).toInt(), easing = KY_EASE_OUT))
                finish()
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(ink.background).tapSwallow { }) {
        // One uninterrupted sky from the transparent status area to the footer.
        // The touch viewport and foreground tabs stay in their original padded column.
        val camera = if (reducedMotion) selected.toFloat() else position
        RecallSky(
            camera, summary, trace, ink,
            ambientRunning = !dragging && !animating,
            reduceMotion = reducedMotion,
            modifier = Modifier.fillMaxSize(),
            seasonTerm = term,
        )
        Column(modifier = Modifier.fillMaxSize().padding(top = 44.dp, bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).tapNoIndication { RecallScreen.back() },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val c = center
                        val unit = size.width / 20f
                        fun p(x: Float, y: Float) = Offset(c.x + x * unit, c.y + y * unit)
                        drawLine(paper, p(-6f, 0f), p(6f, 0f), 1.35f * unit, cap = StrokeCap.Round)
                        drawLine(paper, p(-6f, 0f), p(-1f, -5f), 1.35f * unit, cap = StrokeCap.Round)
                        drawLine(paper, p(-6f, 0f), p(-1f, 5f), 1.35f * unit, cap = StrokeCap.Round)
                    }
                }
                Text_("回观", fontSize = 21f, lineHeight = 32f, serif = true, weight = 500, color = paper)
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(paper.copy(alpha = .055f))
                        .border(.7.dp, paper.copy(alpha = .13f), RoundedCornerShape(22.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecallChoice(target?.takeIf { !it.monthly }?.label(today) ?: "本周", !month, ink) { month = false }
                    RecallChoice(target?.takeIf { it.monthly }?.label(today) ?: "本月", month, ink) { month = true }
                }
            }
            Text_(
                summary.range, fontSize = 10.5f, lineHeight = 17f, letterSpacing = .3f,
                color = paper.copy(alpha = .65f),
                modifier = Modifier.padding(start = 24.dp, top = 11.dp, bottom = 3.dp),
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .onGloballyPositioned { width = it.size.width.toFloat() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (width <= 0f) return@awaitEachGesture
                            var over = 0f
                            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, surplus ->
                                over = surplus
                                change.consume()
                            } ?: return@awaitEachGesture
                            val pageWidth = width
                            val origin = if (animating) glide.value else dragPosition
                            motion?.cancel()
                            animating = false
                            dragging = true
                            val first = if (start.id == down.id) start.position.x - down.position.x else over
                            var dx = first
                            var last = start.uptimeMillis
                            var v = if (last > down.uptimeMillis) first * 1000f / (last - down.uptimeMillis) else 0f
                            dragPosition = (origin - dx / pageWidth).coerceIn(0f, 2f)
                            val ended = horizontalDrag(start.id) { change ->
                                val delta = change.position.x - change.previousPosition.x
                                if (change.uptimeMillis > last)
                                    v = .45f * v + .55f * delta * 1000f / (change.uptimeMillis - last)
                                last = change.uptimeMillis
                                dx += delta
                                dragPosition = (origin - dx / pageWidth).coerceIn(0f, 2f)
                                change.consume()
                            }
                            dragging = false
                            if (!ended) settle(settledPage)
                            else {
                                val up = currentEvent.changes.maxOfOrNull { it.uptimeMillis } ?: last
                                val fling = up - last < 110L && abs(v) > 620.dp.toPx() &&
                                    abs(dx) > 14.dp.toPx() && v * dx > 0f
                                val fromPage = origin.roundToInt()
                                val target = when {
                                    abs(dragPosition - origin) >= .55f -> dragPosition.roundToInt()
                                    abs(dragPosition - origin) > .22f || fling -> fromPage + if (dx < 0) 1 else -1
                                    else -> fromPage
                                }
                                settle(target)
                            }
                        }
                    },
            ) {
                for (page in 0..2) {
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = (page - position) * measuredWidth
                        alpha = if (measuredWidth > 0f || page == 0) 1f else 0f
                    }) {
                        when (page) {
                            0 -> RecallCountFrame(summary, ink, if (month) season else null)
                            1 -> RecallCategoryFrame(summary, ink)
                            else -> RecallNotesFrame(summary, ink)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                recallFrameNames.forEachIndexed { i, label ->
                    Column(
                        modifier = Modifier.weight(1f).height(44.dp).tapNoIndication { settle(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(modifier = Modifier.width(if (selected == i) 22.dp else 5.dp).height(2.dp)
                            .background(if (selected == i) ink.accent else ink.second.copy(alpha = .45f)))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text_(label, fontSize = 11f, lineHeight = 17f,
                            color = paper.copy(alpha = if (selected == i) 1f else .66f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecallChoice(label: String, active: Boolean, ink: RecallInk, action: () -> Unit) {
    Box(
        modifier = Modifier.width(54.dp).height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (active) ink.accent.copy(alpha = .16f) else Color.Transparent)
            .tapNoIndication { action() },
        contentAlignment = Alignment.Center,
    ) {
        Text_(label, fontSize = 11.5f, lineHeight = 18f, color = if (active) ink.accent else paper.copy(alpha = .70f))
    }
}

@Composable
private fun RecallCountFrame(s: RecallSummary, ink: RecallInk, season: RecallSeason?) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 25.dp, top = 38.dp)) {
            Text_("壹  ·  卦数", fontSize = 11f, lineHeight = 18f, letterSpacing = 2f, color = ink.second)
            Spacer(modifier = Modifier.height(7.dp))
            Text_(season?.term ?: "本周", fontSize = 28f, lineHeight = 39f, weight = 500, serif = true, color = paper)
            if (season != null)
                Text_(season.tagline, fontSize = 12f, lineHeight = 20f, color = ink.second.copy(alpha = .86f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text_(s.total.toString(), fontSize = 70f, lineHeight = 83f, serif = true, color = ink.accent)
                Text_("则", fontSize = 14f, lineHeight = 22f, color = paper,
                    modifier = Modifier.padding(start = 7.dp, bottom = 13.dp))
            }
        }
        Text_(
            if (s.total == 0) "这段时间还没有卦例。" else "按起卦日呈现 · 亮点为卦例",
            fontSize = 11f, lineHeight = 18f, color = paper.copy(alpha = .76f),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 26.dp),
        )
    }
}

@Composable
private fun RecallCategoryFrame(s: RecallSummary, ink: RecallInk) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.57f).fillMaxHeight(.69f)
            .padding(end = 23.dp, start = 7.dp)) {
            Text_("贰  ·  所问", fontSize = 11f, lineHeight = 18f, letterSpacing = 2f, color = ink.second)
            Text_("事类", fontSize = 33f, lineHeight = 48f, weight = 500, serif = true, color = paper)
            Spacer(modifier = Modifier.height(9.dp))
            if (s.categories.isEmpty()) {
                Text_("这段时间没有事类记录。", fontSize = 12f, lineHeight = 21f, color = paper.copy(alpha = .78f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    s.categories.forEach { c ->
                        item(key = c.label) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text_(c.label, fontSize = 12.5f, lineHeight = 19f, maxLines = 2,
                                    color = paper, modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text_(c.count.toString(), fontSize = 18f, lineHeight = 25f, color = ink.accent, serif = true)
                            }
                        }
                    }
                }
            }
        }
        Text_(if (s.categories.size > 9) "低频事类仅在点图合并" else "同类卦例，聚为一簇",
            fontSize = 10.5f, lineHeight = 17f, color = paper.copy(alpha = .72f),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 25.dp, bottom = 25.dp))
    }
}

@Composable
private fun RecallNotesFrame(s: RecallSummary, ink: RecallInk) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text_("叁  ·  后来", fontSize = 11f, lineHeight = 18f, letterSpacing = 2f, color = ink.second,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 35.dp, end = 25.dp))
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 25.dp, vertical = 23.dp)) {
            Text_("实际情况", fontSize = 32f, lineHeight = 47f, weight = 500, serif = true, color = paper)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                RecallLedger(s.noted, "已记", ink.accent, Modifier.weight(1f))
                RecallLedger(s.total - s.noted, "未记", ink.second, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text_(
                if (s.total == 0) "这段时间还没有卦例。" else "只统计是否填写，不判断是否应验。",
                fontSize = 11f, lineHeight = 18f, color = paper.copy(alpha = .77f),
            )
        }
    }
}

@Composable
private fun RecallLedger(count: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text_(count.toString(), fontSize = 42f, lineHeight = 52f, color = color, serif = true)
        Text_(label, fontSize = 12f, lineHeight = 21f, color = paper)
    }
}

