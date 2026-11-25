package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xlr.k.judge.jesc
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue

// The wire layer receives aggregates only. No question, note, reading, record
// id or model settings can be serialized to the native presentation.
private fun wire(s: String) = "\"" + jesc(s) + "\""
private fun wireArray(values: List<String>) = values.joinToString(",", "[", "]")
private fun reportKey(monthly: Boolean) = if (monthly) "month" else "week"

internal fun iosRecallLens(summary: RecallSummary, monthly: Boolean, prior: Boolean, seasonAt: Instant): String = buildString {
    val trace = recallTrace(summary)
    val label = if (monthly) (if (prior) "上月" else "本月") else (if (prior) "上周" else "本周")
    append("{\"key\":").append(wire(reportKey(monthly))).append(",\"label\":").append(wire(label))
    append(",\"start\":").append(wire(summary.start.toString())).append(",\"end\":").append(wire(summary.endInclusive.toString()))
    append(",\"dayCount\":").append(summary.dayCount).append(",\"total\":").append(summary.total)
    append(",\"noted\":").append(summary.noted).append(",\"clusters\":").append(trace.clusters)
    append(",\"categories\":").append(wireArray(summary.categories.map {
        val category = it.label.lineSequence().first().take(48)
        "{\"label\":${wire(category)},\"count\":${it.count}}"
    }))
    append(",\"trace\":").append(wireArray(trace.points.mapIndexed { i, p ->
        val pulse = recallPulse(i + p.day * 101)
        """{"day":${p.day},"dayIndex":${p.dayIndex},"dayCount":${p.dayCount},"cluster":${p.cluster},"clusterIndex":${p.clusterIndex},"clusterCount":${p.clusterCount},"status":${p.status},"statusIndex":${p.statusIndex},"statusCount":${p.statusCount},"period":${pulse.period},"phase":${pulse.phase}}"""
    }))
    if (monthly) {
        val s = recallSeason(seasonAt)
        append(""","season":{"term":${wire(s.term)},"tagline":${wire(s.tagline)},"mood":${wire(s.mood)},"ink":${s.ink},"accent":${s.accent},"second":${s.second},"glow":${s.glow}}""")
    }
    append("}")
}

internal object IosRecall {
    private data class Offer(val period: RecallReminderPeriod) {
        val key: String get() = "${reportKey(period.monthly)}:${period.anchor}"
    }

    var systemNotificationsEnabled by mutableStateOf(false)
        private set
    var foreground by mutableStateOf(false)
        private set
    var nativeBlocked by mutableStateOf(true)
        private set
    var nativeReady by mutableStateOf(false)
        private set
    var reportOpen by mutableStateOf(false)
        private set

    // All bridge actions run on the main queue. Keep at most one visible
    // offer per foreground visit if the week and month close together.
    var composeHomeEligible = false
    private var shownOn: LocalDate? = null
    private var offered: Offer? = null
    private var openPayload: String? = null
    private var observerInstalled = false

    @OptIn(ExperimentalForeignApi::class)
    fun install() {
        if (observerInstalled) return
        observerInstalled = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            "xlrRecallCommand", null, NSOperationQueue.mainQueue,
        ) { note ->
            val info = note?.userInfo ?: return@addObserverForName
            val key = info["key"]?.toString()
            fun active(): Boolean? = when (val value = info["active"]) {
                is NSNumber -> value.boolValue
                is Boolean -> value
                else -> null
            }
            when (info["action"]?.toString()) {
                "systemNotificationsState" -> active()?.let { systemNotificationsEnabled = it }
                "ready" -> {
                    nativeReady = true
                    openPayload?.let(::publish)
                    if (canOffer()) offered?.let(::publishOffer)
                }
                "foreground" -> active()?.let { value ->
                    if (!foreground && value) shownOn = null
                    foreground = value
                    if (!value) hideOffer()
                }
                "visibility" -> active()?.let { blocked ->
                    nativeBlocked = blocked
                    if (blocked) hideOffer()
                }
                "reminderShown" -> matching(key)?.takeIf { canOffer() }?.let(::rememberShown)
                "dismissReminder" -> matching(key)?.let { rememberShown(it); hideOffer() }
                "openReminder" -> matching(key)?.takeIf { canOffer() }?.let(::openReminder)
                "close" -> close()
            }
        }
    }

    private fun canOffer() = nativeReady && foreground && !nativeBlocked && !reportOpen && composeHomeEligible
    private fun matching(key: String?) = offered?.takeIf { it.key == key }
    private fun seenKey(monthly: Boolean) =
        if (monthly) "recall.reminder.month.shown" else "recall.reminder.week.shown"

    fun localNow() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private fun lastShown(monthly: Boolean): LocalDate? =
        Platform.get?.invoke(seenKey(monthly))?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun rememberShown(offer: Offer) {
        shownOn = localNow().date
        val p = offer.period
        if (lastShown(p.monthly)?.let { it < p.anchor } != false)
            Platform.put?.invoke(seenKey(p.monthly), p.anchor.toString())
    }

    fun updateOffer(now: LocalDateTime) {
        if (!canOffer() || Platform.get == null || Platform.put == null) return
        offered?.let { current ->
            if (current.period.covers(now)) return
            hideOffer()
        }
        if (shownOn == now.date) return
        val rows = RecStore.loadRaw()
        val period = pendingRecallReminder(rows, now, lastShown(false), lastShown(true)) ?: return
        val summary = buildRecallSummary(rows, period.anchor, period.monthly)
        if (summary.total == 0) return
        val next = Offer(period)
        offered = next
        publishOffer(next)
    }

    fun hideOffer() {
        if (offered == null) return
        offered = null
        publish("""{"kind":"clearReminder"}""")
    }

    private fun publishOffer(offer: Offer) {
        val p = offer.period
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val title = p.label(today) + "回观"
        val summary = buildRecallSummary(RecStore.loadRaw(), p.anchor, p.monthly)
        val start = summary.start
        val end = summary.endInclusive
        val range = if (p.monthly) "${p.anchor.monthNumber} 月"
            else if (start.monthNumber == end.monthNumber) "${start.monthNumber} 月 ${start.dayOfMonth}–${end.dayOfMonth} 日"
            else "${start.monthNumber}/${start.dayOfMonth}–${end.monthNumber}/${end.dayOfMonth}"
        val subtitle = "$range · ${summary.total} 则卦例"
        // Derive an opaque shade from the actual page palette, not from the window backdrop.
        val bg = KY_BG
        val surface = KY_SURFACE
        val ink = KY_INK
        val amount = if (Skin.dark) .15f else .085f
        fun channel(background: Float, panel: Float, primary: Float): String {
            val paper = background + (panel - background) * .28f
            val value = ((paper + (primary - paper) * amount) * 255 + .5f).toInt().coerceIn(0, 255)
            return value.toString(16).padStart(2, '0')
        }
        val face = "#" + channel(bg.red, surface.red, ink.red) + channel(bg.green, surface.green, ink.green) +
            channel(bg.blue, surface.blue, ink.blue)
        publish(
            """{"kind":"reminder","key":${wire(offer.key)},"active":${wire(reportKey(offer.period.monthly))},"title":${wire(title)},"message":${wire(subtitle)},"face":${wire(face)},"offerMs":$RECALL_OFFER_MS,"hintMs":$RECALL_HINT_MS}""",
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    fun querySystemNotifications() {
        NSNotificationCenter.defaultCenter.postNotificationName("xlrRecallSystemQuery", null, emptyMap<Any?, Any?>())
    }

    @OptIn(ExperimentalForeignApi::class)
    fun setSystemNotifications(enabled: Boolean) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrRecallSystemToggle", null, mapOf<Any?, Any?>("enabled" to enabled),
        )
    }

    fun openAbout() {
        val instant = Clock.System.now()
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val rows = RecStore.loadRaw()
        val week = iosRecallLens(buildRecallSummary(rows, date, false), false, false, instant)
        val month = iosRecallLens(buildRecallSummary(rows, date, true), true, false, instant)
        open("about", "week", listOf(week, month))
    }

    private fun openReminder(offer: Offer) {
        val p = offer.period
        val summary = buildRecallSummary(RecStore.loadRaw(), p.anchor, p.monthly)
        rememberShown(offer)
        if (summary.total == 0) { hideOffer(); return }
        val at = LocalDateTime(p.anchor.year, p.anchor.monthNumber, p.anchor.dayOfMonth, 12, 0)
            .toInstant(TimeZone.currentSystemDefault())
        open("reminder", reportKey(p.monthly), listOf(iosRecallLens(summary, p.monthly, localNow().date > p.anchor, at)))
    }

    private fun open(source: String, key: String, lenses: List<String>) {
        // Keep Settings' route and scroll intact below the native report.
        reportOpen = true
        hideOffer()
        val payload = """{"kind":"open","source":${wire(source)},"active":${wire(key)},"lenses":${wireArray(lenses)}}"""
        openPayload = payload
        publish(payload)
    }

    fun close(): Boolean {
        if (!reportOpen) return false
        openPayload = null
        reportOpen = false
        publish("""{"kind":"close"}""")
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun publish(payload: String) {
        if (!nativeReady) return
        NSNotificationCenter.defaultCenter.postNotificationName(
            "xlrRecall", null, mapOf<Any?, Any?>("payload" to payload),
        )
    }
}

@Composable
internal fun IosRecallIdleGate(composeEligible: Boolean) {
    val eligible = composeEligible && IosRecall.nativeReady && IosRecall.foreground &&
        !IosRecall.nativeBlocked && !IosRecall.reportOpen
    SideEffect { IosRecall.composeHomeEligible = composeEligible }
    DisposableEffect(Unit) {
        onDispose {
            IosRecall.composeHomeEligible = false
            IosRecall.hideOffer()
        }
    }
    LaunchedEffect(eligible) {
        if (!eligible) {
            IosRecall.hideOffer()
            return@LaunchedEffect
        }
        // Wait for overlay exit motions, then only check while home is idle.
        delay(420)
        while (true) {
            IosRecall.updateOffer(IosRecall.localNow())
            delay(30_000)
        }
    }
}
