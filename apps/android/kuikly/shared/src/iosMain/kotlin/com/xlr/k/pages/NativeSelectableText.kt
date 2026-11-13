package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.core.base.Attr
import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.math.abs

/** iOS-only UITextView. UIKit owns the handles AND the standard selection menu.
 * No Compose long-press recognizer or app-drawn copy popup wraps this component.
 * Text/logic remain in Kotlin; UIKit only renders the already-parsed styled runs.
 */
private class NativeTextAttr : Attr() {
    fun configure(spec: String) { "textSpec" with spec }
}
private class NativeTextEvent : Event() {
    fun metrics(handler: (JSONObject) -> Unit) {
        register("onMetrics", { (it as? JSONObject)?.let(handler) })
    }
    fun selection(handler: (Boolean) -> Unit) {
        register("onSelection", { handler((it as? JSONObject)?.optBoolean("active") == true) }, isSync = true)
    }
}
private class NativeTextNode : DeclarativeBaseView<NativeTextAttr, NativeTextEvent>() {
    override fun createAttr() = NativeTextAttr()
    override fun createEvent() = NativeTextEvent()
    override fun viewName() = "XLRSelectableText"
}

@Composable
private fun NativeSelectableText(
    paragraphs: JSONArray,
    identity: String,
    active: Boolean,
    maxHeight: Float? = null,
    inset: Float = 0f,
    busy: Boolean = false,
) {
    val token = remember { Any() }
    var naturalHeight by remember(identity) { mutableStateOf(24f) }
    var clearEpoch by remember { mutableStateOf(0) }
    fun clear() { clearEpoch++; CardSelection.end(token) }
    LaunchedEffect(identity, active) { clear() }
    DisposableEffect(Unit) { onDispose { CardSelection.end(token) } }
    val spec = JSONObject().put("paragraphs", paragraphs).put("identity", identity)
        .put("active", active).put("clearEpoch", clearEpoch).put("busy", busy)
        .put("scrollable", maxHeight != null)
        .put("inset", JSONObject().put("left", inset).put("right", inset)
            .put("top", if (maxHeight != null) 12 else 0)
            .put("bottom", if (maxHeight != null) 12 else 0)).toString()
    val height = if (maxHeight == null) naturalHeight else naturalHeight.coerceAtMost(maxHeight)
    MakeKuiklyComposeNode(
        factory = { NativeTextNode() },
        modifier = Modifier.fillMaxWidth().height(height.coerceAtLeast(1f).dp),
        viewUpdate = { node ->
            // Register before textSpec, because the native view may measure immediately.
            node.event {
                metrics { value ->
                    if (value.optString("identity") == identity) {
                        val h = value.optDouble("height").toFloat()
                        if (h.isFinite() && h > 0f && abs(h - naturalHeight) > 0.5f) naturalHeight = h
                    }
                }
                selection { selected ->
                    if (selected && active) CardSelection.begin(token, ::clear)
                    else CardSelection.end(token)
                }
            }
            node.attr { configure(spec) }
        },
    )
}

private fun nativeColor(c: Color): String {
    fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f).toInt().toString(16).padStart(2, '0')
    return "#" + ch(c.red) + ch(c.green) + ch(c.blue) + ch(c.alpha)
}
private fun run(text: String, size: Float, line: Float, color: Color,
                weight: Int = 400, serif: Boolean = false, baseline: Float = 0f,
                letterSpacing: Float = 0f): JSONObject = JSONObject()
    .put("text", text).put("size", size).put("lineHeight", line)
    .put("color", nativeColor(color)).put("weight", weight).put("serif", serif)
    .put("baselineOffset", baseline).put("letterSpacing", letterSpacing)

private fun richRuns(text: String, size: Float, line: Float, color: Color): JSONArray {
    val runs = JSONArray()
    splitBold(text).forEach { (s, bold) -> runs.put(run(s, size, line, color, if (bold) 500 else 400)) }
    return runs
}
private fun paragraph(runs: JSONArray, before: Float = 0f, after: Float = 0f): JSONObject =
    JSONObject().put("runs", runs).put("spaceBefore", before).put("spaceAfter", after)
private val nativeVerdict = Regex("^(\\*\\*)?([^（）()]{1,16})（([^（）]{0,44})）(\\*\\*)?(.*)$")

/** Same DocBlk parser as the existing card, now one attributed text for cross-paragraph selection. */
@Composable
internal fun NativeDuanciText(raw: String, bigFirst: Boolean, identity: String,
                              active: Boolean, maxHeight: Float, busy: Boolean) {
    val paragraphs = JSONArray()
    docBlocks(raw, bigFirst).forEach { block ->
        when (block) {
            DocBlk.Gap -> paragraphs.put(paragraph(JSONArray().put(run("", 1f, 7f, KY_DIM))))
            is DocBlk.H -> paragraphs.put(paragraph(JSONArray().put(
                run(stripBold(block.t), 13f, 21f, KY_INK, 500)), 14f, 4f))
            is DocBlk.P -> {
                if (block.kind == 0) {
                    paragraphs.put(paragraph(richRuns(block.t, 13.5f, 26f, KY_DIM)))
                } else {
                    val verdict = nativeVerdict.find(block.t)
                    if (verdict != null) {
                        paragraphs.put(paragraph(JSONArray()
                            .put(run(stripBold(verdict.groupValues[2]), 30f, 42f, KY_JYC, 500, true, letterSpacing = 1f))
                            .put(run("（" + verdict.groupValues[3] + "）", 17f, 42f, KY_DIM2, baseline = 6f))))
                        val tail = verdict.groupValues[5]
                        if (tail.isNotBlank()) paragraphs.put(paragraph(richRuns(tail, 13.5f, 26f, KY_DIM)))
                    } else {
                        val text = stripBold(block.t)
                        val size = if (block.kind == 1 || text.length <= 18) 30f else 22f
                        val line = if (block.kind == 1) 42f else if (size == 30f) 37f else 33f
                        paragraphs.put(paragraph(JSONArray().put(run(text, size, line, KY_JYC, 500, true, letterSpacing = 1f))))
                    }
                }
            }
        }
    }
    NativeSelectableText(paragraphs, identity, active, maxHeight, inset = 18f, busy = busy)
}

@Composable
internal fun NativeFollowText(question: String, answer: String, identity: String, active: Boolean) {
    val paragraphs = JSONArray().put(paragraph(JSONArray()
        .put(run("问 · ", 13f, 21f, KY_DIM2))
        .put(run(question, 13f, 21f, KY_INK, 600)), after = 4f))
    if (answer.isNotEmpty()) paragraphs.put(paragraph(richRuns(answer, 13f, 22.75f, KY_INK)))
    NativeSelectableText(paragraphs, identity, active)
}
