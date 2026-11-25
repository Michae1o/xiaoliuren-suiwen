package com.xlr.k.pages

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

internal data class RecallCategory(val label: String, val count: Int)
internal data class RecallMark(val day: Int, val category: String, val noted: Boolean)
internal data class RecallSummary(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val dayCount: Int,
    val categories: List<RecallCategory>,
    val noted: Int,
    val marks: List<RecallMark>,
) {
    val total: Int get() = marks.size
    private fun LocalDate.localLabel() = "${year} 年 ${monthNumber} 月 ${dayOfMonth} 日"
    val range: String get() = "${start.localLabel()} 至 ${endInclusive.localLabel()}"
}

private val recallDatePattern = Regex("""^\d{4}-\d{2}-\d{2}(?=$|[ T])""")
private fun LocalDate.step(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)

internal const val RECALL_OFFER_MS = 8_000
internal const val RECALL_HINT_MS = 5_200

/** Local period closed by the reminder, pinned across midnight. */
internal data class RecallReminderPeriod(val anchor: LocalDate, val monthly: Boolean) {
    fun covers(now: LocalDateTime): Boolean =
        (now.date == anchor && now.hour >= 21) || now.date == anchor.step(1)
    fun label(on: LocalDate): String = (if (on > anchor) "上" else "本") + if (monthly) "月" else "周"
}

internal fun recallReminderWindow(now: LocalDateTime, monthly: Boolean): RecallReminderPeriod? {
    val date = now.date
    val start = if (monthly) LocalDate(date.year, date.monthNumber, 1) else date.step(-date.dayOfWeek.ordinal)
    val next = if (!monthly) start.step(7)
        else if (date.monthNumber == 12) LocalDate(date.year + 1, 1, 1)
        else LocalDate(date.year, date.monthNumber + 1, 1)
    val target = if (date == start) start.step(-1) else next.step(-1)
    return RecallReminderPeriod(target, monthly).takeIf { it.covers(now) }
}

internal fun pendingRecallReminder(
    rows: List<Map<String, String>>,
    now: LocalDateTime,
    lastWeekShown: LocalDate?,
    lastMonthShown: LocalDate?,
): RecallReminderPeriod? = listOf(true, false).firstNotNullOfOrNull { monthly ->
    val period = recallReminderWindow(now, monthly) ?: return@firstNotNullOfOrNull null
    val seen = if (monthly) lastMonthShown else lastWeekShown
    period.takeIf { (seen == null || seen < it.anchor) && buildRecallSummary(rows, it.anchor, monthly).total > 0 }
}

/** Matches desktop V7 huiguanStats.js (blob a40772a). This is a local read-only report, not another divination path. */
internal fun buildRecallSummary(rows: List<Map<String, String>>, today: LocalDate, monthly: Boolean): RecallSummary {
    val start = if (monthly) LocalDate(today.year, today.monthNumber, 1) else today.step(-today.dayOfWeek.ordinal)
    val end = if (monthly) {
        if (today.monthNumber == 12) LocalDate(today.year + 1, 1, 1)
        else LocalDate(today.year, today.monthNumber + 1, 1)
    } else start.step(7)
    val startOrdinal = start.toEpochDays()
    val seenIds = mutableSetOf<String>()
    val marks = buildList {
        for (row in rows) {
            if (!row["deleted"].isNullOrEmpty()) continue
            val raw = recallDatePattern.find(row["t"].orEmpty().trim())?.value ?: continue
            val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: continue
            if (date < start || date >= end) continue
            val id = row["id"].orEmpty().trim()
            if (id.isNotEmpty() && !seenIds.add(id)) continue
            add(RecallMark(date.toEpochDays() - startOrdinal,
                row["cat"].orEmpty().trim().ifEmpty { "未分类" }, !row["note"].isNullOrBlank()))
        }
    }
    val categories = marks.groupingBy { it.category }.eachCount().map { (label, count) ->
        RecallCategory(label, count)
    }.sortedWith(compareByDescending<RecallCategory> { it.count }.thenBy { it.label })
    return RecallSummary(start, end.step(-1), end.toEpochDays() - startOrdinal,
        categories, marks.count { it.noted }, marks)
}

internal const val RECALL_TRACE_MAX = 72
internal data class RecallTracePoint(
    val day: Int, val dayIndex: Int, val dayCount: Int,
    val cluster: Int, val clusterIndex: Int, val clusterCount: Int,
    val status: Int, val statusIndex: Int, val statusCount: Int,
)
internal data class RecallTrace(val points: List<RecallTracePoint>, val clusters: Int)

internal fun recallTrace(summary: RecallSummary): RecallTrace {
    val categoryCount = summary.categories.size
    val labels = summary.categories.take(if (categoryCount > 9) 8 else 9).map { it.label }
    val clusters = labels.size + if (categoryCount > 9) 1 else 0
    val sorted = summary.marks.sortedBy { it.day }
    val sample = if (sorted.size <= RECALL_TRACE_MAX) sorted else List(RECALL_TRACE_MAX) { i ->
        sorted[((i + .5) * sorted.size / RECALL_TRACE_MAX).toInt()]
    }
    val days = IntArray(summary.dayCount)
    val categories = IntArray(clusters)
    val statuses = IntArray(2)
    data class Ranked(val d: Int, val c: Int, val s: Int, val dr: Int, val cr: Int, val sr: Int)
    val ranked = sample.map { mark ->
        val day = mark.day
        val category = labels.indexOf(mark.category).takeIf { it >= 0 } ?: (clusters - 1)
        val status = if (mark.noted) 0 else 1
        Ranked(day, category, status, days[day]++, categories[category]++, statuses[status]++)
    }
    return RecallTrace(ranked.map {
        RecallTracePoint(it.d, it.dr, days[it.d], it.c, it.cr, categories[it.c], it.s, it.sr, statuses[it.s])
    }, clusters)
}
