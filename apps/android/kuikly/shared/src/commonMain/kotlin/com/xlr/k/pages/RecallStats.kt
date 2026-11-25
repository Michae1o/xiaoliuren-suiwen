package com.xlr.k.pages

import kotlinx.datetime.LocalDate

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

/** Anchor the report on the final day of the completed local calendar period. */
internal data class RecallReminderPeriod(val anchor: LocalDate, val monthly: Boolean) {
    val label: String get() = if (monthly) "上月" else "上周"
}

internal fun pendingRecallReminder(
    rows: List<Map<String, String>>,
    today: LocalDate,
    lastWeekShown: LocalDate?,
    lastMonthShown: LocalDate?,
): RecallReminderPeriod? {
    val lastMonth = LocalDate(today.year, today.monthNumber, 1).step(-1)
    val lastWeek = today.step(-today.dayOfWeek.ordinal - 1)
    // Month first; at most one result, no scanning backwards through historical periods.
    if ((lastMonthShown == null || lastMonthShown < lastMonth) && buildRecallSummary(rows, lastMonth, monthly = true).total > 0)
        return RecallReminderPeriod(lastMonth, monthly = true)
    if ((lastWeekShown == null || lastWeekShown < lastWeek) && buildRecallSummary(rows, lastWeek, monthly = false).total > 0)
        return RecallReminderPeriod(lastWeek, monthly = false)
    return null
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
