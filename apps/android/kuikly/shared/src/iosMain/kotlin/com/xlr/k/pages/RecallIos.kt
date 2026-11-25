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
    private data class Offer(val period: RecallReminderPeriod, val total: Int) {
        val key: String get() = "${reportKey(period.monthly)}:${period.anchor}"
    }

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

    fun localToday() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private fun lastShown(monthly: Boolean): LocalDate? =
        Platform.get?.invoke(seenKey(monthly))?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun rememberShown(offer: Offer) {
        shownOn = localToday()
        val p = offer.period
        if (lastShown(p.monthly)?.let { it < p.anchor } != false)
            Platform.put?.invoke(seenKey(p.monthly), p.anchor.toString())
    }

    fun updateOffer(today: LocalDate) {
        if (!canOffer() || Platform.get == null || Platform.put == null) return
        offered?.let { current ->
            // Don't leave a previous period's card up after a calendar rollover.
            val nowStart = buildRecallSummary(emptyList(), today, current.period.monthly).start
            if (current.period.anchor.toEpochDays() == nowStart.toEpochDays() - 1) return
            hideOffer()
        }
        if (shownOn == today) return
        val rows = RecStore.loadRaw()
        val period = pendingRecallReminder(rows, today, lastShown(false), lastShown(true)) ?: return
        val summary = buildRecallSummary(rows, period.anchor, period.monthly)
        if (summary.total == 0) return
        val next = Offer(period, summary.total)
        offered = next
        publishOffer(next)
    }

    fun hideOffer() {
        if (offered == null) return
        offered = null
        publish("""{"kind":"clearReminder"}""")
    }

    private fun publishOffer(offer: Offer) = publish(
        """{"kind":"reminder","key":${wire(offer.key)},"active":${wire(reportKey(offer.period.monthly))},"title":${wire(offer.period.label + "回观")},"message":${wire("${offer.total} 则卦例")}}""",
    )

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
        open("reminder", reportKey(p.monthly), listOf(iosRecallLens(summary, p.monthly, true, at)))
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
        var checked: LocalDate? = null
        while (true) {
            val today = IosRecall.localToday()
            if (checked != today) {
                checked = today
                IosRecall.updateOffer(today)
            }
            delay(30_000)
        }
    }
}
