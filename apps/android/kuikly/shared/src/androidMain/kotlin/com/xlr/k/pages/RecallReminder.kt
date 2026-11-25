package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
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

// Activity lifecycle gates both the banner and its silent, foreground-only date check.
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

@Composable
internal fun currentRecallReminder(eligible: Boolean): Pair<RecallReminderPeriod?, () -> Unit> {
    val session = recallForegroundSession
    var checked by remember(session) { mutableStateOf<LocalDate?>(null) }
    var candidate by remember(session) { mutableStateOf<RecallReminderPeriod?>(null) }
    var shown by remember(session) { mutableStateOf<RecallReminderPeriod?>(null) }

    LaunchedEffect(eligible, session) {
        if (!eligible) {
            if (candidate != null && shown == candidate) candidate = null
            return@LaunchedEffect
        }
        // Give settings/drawer/guide exit animations time to clear before inserting the banner.
        delay(400)
        while (true) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            if (checked != now && Platform.get != null && Platform.put != null) {
                checked = now
                candidate = pendingRecallReminder(RecStore.loadRaw(), now, readRecallShown(false), readRecallShown(true))
            }
            delay(30_000)
        }
    }

    val visible = if (eligible) candidate else null
    LaunchedEffect(visible) {
        if (visible != null && shown != visible) {
            // Do not record a candidate hidden by another surface. Wait until it has had time to draw.
            delay(100)
            Platform.put?.invoke(recallShownKey(visible.monthly), visible.anchor.toString())
            shown = visible
        }
    }
    return visible to {
        candidate?.let { period ->
            if (shown != period) {
                Platform.put?.invoke(recallShownKey(period.monthly), period.anchor.toString())
                shown = period
            }
        }
        candidate = null
    }
}

@Composable
internal fun RecallReminderBanner(period: RecallReminderPeriod, onDismiss: () -> Unit) {
    val open = { onDismiss(); RecallScreen.enterReminder(period) }
    Row(
        Modifier.fillMaxWidth().height(36.dp).padding(vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp)).background(veil(.46f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .semantics(mergeDescendants = true) { role = Role.Button; onClick { open(); true } }
                .tapNoIndication(open).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text_("${period.label}回观已就绪", fontSize = 11.5f, lineHeight = 17f,
                color = KY_DIM, maxLines = 1, modifier = Modifier.weight(1f))
            Text_("查看 ›", fontSize = 11.5f, lineHeight = 17f, weight = 600, color = KY_ACCENT)
        }
        Box(
            Modifier.width(47.dp).fillMaxHeight()
                .semantics(mergeDescendants = true) { role = Role.Button; onClick { onDismiss(); true } }
                .tapNoIndication(onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text_("关闭", fontSize = 10.5f, lineHeight = 17f, color = KY_DIM2)
        }
    }
}
