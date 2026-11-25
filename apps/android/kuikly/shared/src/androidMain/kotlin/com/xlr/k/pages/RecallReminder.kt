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
import com.tencent.kuikly.compose.animation.core.LinearEasing
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.drawBehind
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.semantics.Role
import com.tencent.kuikly.compose.ui.semantics.onClick
import com.tencent.kuikly.compose.ui.semantics.role
import com.tencent.kuikly.compose.ui.semantics.semantics
import com.tencent.kuikly.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal var recallForeground by mutableStateOf(false)
    private set
private var recallForegroundSession by mutableStateOf(0)

fun setRecallReminderForeground(foreground: Boolean) {
    if (foreground && !recallForeground) recallForegroundSession++
    recallForeground = foreground
}

private fun recallShownKey(monthly: Boolean) =
    if (monthly) "recall.reminder.month.shown" else "recall.reminder.week.shown"

private fun readRecallShown(monthly: Boolean): LocalDate? =
    Platform.get?.invoke(recallShownKey(monthly))?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal data class RecallBannerPhase(val period: RecallReminderPeriod, val hint: Boolean = false)
internal enum class RecallBannerAction { View, Later, Skip, Timeout }

@Composable
internal fun currentRecallReminder(eligible: Boolean): Pair<RecallBannerPhase?, (RecallBannerAction) -> Unit> {
    val session = recallForegroundSession
    var candidate by remember(session) { mutableStateOf<RecallBannerPhase?>(null) }
    var offered by remember(session) { mutableStateOf(false) }
    fun mark(period: RecallReminderPeriod) {
        if (readRecallShown(period.monthly)?.let { it < period.anchor } != false) {
            Platform.put?.invoke(recallShownKey(period.monthly), period.anchor.toString())
        }
        offered = true
    }

    LaunchedEffect(eligible, session) {
        if (!eligible) {
            candidate = null
            return@LaunchedEffect
        }
        delay(400)
        while (true) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            if (candidate?.let { !it.period.covers(now) } == true) candidate = null
            if (!offered && candidate == null && Platform.get != null && Platform.put != null) {
                candidate = pendingRecallReminder(RecStore.loadRaw(), now, readRecallShown(false), readRecallShown(true))
                    ?.let { RecallBannerPhase(it) }
            }
            delay(30_000)
        }
    }

    val visible = candidate.takeIf { eligible }
    LaunchedEffect(visible) {
        if (visible != null && !visible.hint) {
            delay(100)
            if (candidate == visible) mark(visible.period)
        }
    }
    return visible to { action ->
        val phase = candidate
        if (phase != null && phase == visible && eligible) {
            mark(phase.period)
            if (!phase.hint && (action == RecallBannerAction.Timeout || action == RecallBannerAction.Later)) {
                candidate = phase.copy(hint = true)
            } else {
                candidate = null
                if (!phase.hint && action == RecallBannerAction.View) RecallScreen.enterReminder(phase.period)
            }
        }
    }
}

@Composable
internal fun RecallReminderBanner(phase: RecallBannerPhase, onAction: (RecallBannerAction) -> Unit) {
    val reduceMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
    val progress = remember(phase) { Animatable(1f) }
    val presence = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val wording = remember { Animatable(1f) }
    var pending by remember(phase) { mutableStateOf<RecallBannerAction?>(null) }
    val currentAction by rememberUpdatedState(onAction)

    fun request(action: RecallBannerAction) {
        if (pending == null) pending = action
    }

    LaunchedEffect(Unit) {
        if (!reduceMotion) presence.animateTo(1f, tween(215, easing = KY_EASE_OUT))
    }
    LaunchedEffect(phase, pending) {
        if (pending != null) return@LaunchedEffect
        if (phase.hint || reduceMotion) {
            delay(if (phase.hint) RECALL_HINT_MS.toLong() else RECALL_OFFER_MS.toLong())
        } else {
            progress.animateTo(0f, tween(RECALL_OFFER_MS, easing = LinearEasing))
        }
        request(RecallBannerAction.Timeout)
    }
    LaunchedEffect(phase, pending, reduceMotion) {
        val selected = pending ?: return@LaunchedEffect
        val swapsText = !phase.hint &&
            (selected == RecallBannerAction.Later || selected == RecallBannerAction.Timeout)
        if (!reduceMotion) {
            if (swapsText) wording.animateTo(0f, tween(75, easing = KY_EASE_IN))
            else presence.animateTo(0f, tween(if (selected == RecallBannerAction.View) 145 else 205, easing = KY_EASE_IN))
        }
        currentAction(selected)
    }
    LaunchedEffect(phase.hint) {
        if (phase.hint && !reduceMotion) wording.animateTo(1f, tween(170, easing = KY_EASE_OUT))
    }

    val shape = RoundedCornerShape(28.dp)
    val ink = KY_INK
    val dark = Skin.dark
    val support = KY_SURFACE.copy(alpha = if (dark) .49f else .52f)
    val detailInk = recallMix(ink, KY_SURFACE, .24f)
    val period = phase.period
    val summary = remember(period) { buildRecallSummary(RecStore.loadRaw(), period.anchor, period.monthly) }
    val shownDate = remember(period) { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val title = if (phase.hint) "手动回观" else period.label(shownDate) + "回观"
    val range = if (period.monthly) "${period.anchor.monthNumber} 月" else summary.start.let { start ->
        val end = summary.endInclusive
        if (start.monthNumber == end.monthNumber) "${start.monthNumber} 月 ${start.dayOfMonth}–${end.dayOfMonth} 日"
        else "${start.monthNumber}/${start.dayOfMonth}–${end.monthNumber}/${end.dayOfMonth}"
    }
    val subtitle = if (phase.hint) "设置 › 关于 › 开始回观" else "$range · ${summary.total} 则卦例"

    Box(
        modifier = Modifier.widthIn(max = 342.dp).fillMaxWidth()
            .graphicsLayer {
                val v = presence.value
                alpha = v
                translationY = -(1f - v) * 7.dp.toPx()
                scaleX = .982f + v * .018f
                scaleY = scaleX
            }
            .clip(shape)
            .background(Brush.verticalGradient(0f to support, 1f to support))
            .glassMaterial(shape, control = true),
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    .then(if (phase.hint) Modifier else Modifier.recallInkPress(reduceMotion, pending == RecallBannerAction.View) {
                        request(RecallBannerAction.View)
                    })
                    .graphicsLayer {
                        alpha = wording.value
                        translationY = (1f - wording.value) * (if (phase.hint) 3.dp else (-3).dp).toPx()
                    }.padding(start = 18.dp, end = 3.dp, bottom = 3.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text_(title, fontSize = 14.5f, lineHeight = 19f, weight = 600, color = ink, maxLines = 1)
                Text_(subtitle, fontSize = 11.5f, lineHeight = 16f, color = detailInk, maxLines = 1)
            }
            Box(
                Modifier.width(69.dp).height(56.dp)
                    .recallInkPress(reduceMotion, pending == RecallBannerAction.Later || pending == RecallBannerAction.Skip) {
                        request(if (phase.hint) RecallBannerAction.Skip else RecallBannerAction.Later)
                    }
                    .padding(end = 5.dp)
                    .graphicsLayer { alpha = wording.value },
                contentAlignment = Alignment.Center,
            ) {
                Text_(if (phase.hint) "跳过" else "稍后", fontSize = 13f, lineHeight = 20f, weight = 500,
                    color = ink, maxLines = 1)
            }
        }
        if (!phase.hint && !reduceMotion) {
            val countdownInk = recallMix(ink, KY_ACCENT, .60f).copy(alpha = if (dark) .61f else .49f)
            Canvas(
                Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 5.dp).size(46.dp, 2.dp),
            ) {
                val y = size.height / 2f
                val thickness = 1.dp.toPx()
                val x = thickness / 2f
                val span = (size.width - thickness).coerceAtLeast(0f)
                val remaining = progress.value.coerceIn(0f, 1f)
                if (remaining > 0f) drawLine(countdownInk, Offset(x, y), Offset(x + span * remaining, y), thickness, StrokeCap.Round)
            }
        }
    }
}

private fun recallMix(base: Color, toward: Color, fraction: Float) = Color(
    base.red + (toward.red - base.red) * fraction,
    base.green + (toward.green - base.green) * fraction,
    base.blue + (toward.blue - base.blue) * fraction,
)

@Composable
private fun Modifier.recallInkPress(reduceMotion: Boolean, committed: Boolean, action: () -> Unit): Modifier {
    val current by rememberUpdatedState(action)
    var pressed by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(if (pressed || committed) 1f else 0f,
        tween(if (reduceMotion) 0 else if (pressed || committed) 55 else 135, easing = KY_EASE_OUT))
    val ink = KY_INK
    val tint = if (Skin.dark) .16f else .11f
    return this
        .semantics(mergeDescendants = true) { role = Role.Button; onClick { current(); true } }
        .drawBehind { if (amount > 0f) drawRect(ink.copy(alpha = amount * tint)) }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    try { tryAwaitRelease() } finally { pressed = false }
                },
                onTap = { current() },
            )
        }
}
