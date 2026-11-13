package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.text.BasicTextField
import com.tencent.kuikly.compose.foundation.text.KeyboardActions
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.SolidColor
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.text.TextStyle
import com.tencent.kuikly.compose.ui.text.input.ImeAction
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.xlr.k.judge.Rec
import kotlinx.coroutines.launch

/* 记录详情里的两块：复盘（RvArea）与接着问（FollowAsk）。
   语义照早期版本 Record.jsx 的 <Rv> 与 Detail：
   · 标了「没应验」→ 自动跑一次复盘（出网，让模型指出错在哪一步）；
   · 复盘只写在这条记录上（rv/rvWhy/rvRule），**点「写入准则」才进准则表**；
   · 接着问必须是用模型解过的卦（ans == model），一问一答追加到 thread。 */

@Composable
internal fun RvArea(r: Rec, onChange: () -> Unit, onRuleWritten: () -> Unit = {}) {
    val id = r["id"] ?: ""
    val rv = r["rv"] ?: ""
    var live by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var started by remember(id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val miss = LlmStore.missing(LlmStore.load())

    if (rv.isNotEmpty()) {
        SelectableCardText(textKey = listOf(id, rv, Session.recordsOpen)) { Text_(text = rv, fontSize = 12.5f, lineHeight = 22f, color = KY_INK) }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            /* 旧 Records.jsx L203-205：写入准则 / 算了，不记 ＋ 一句 mini 说明；
               写完跟早期版本一样切到「准则」那一页（旧 20-script L533 末尾 sdbTab('rules')） */
            SetBtn("写入准则", primary = true) {
                RecStore.addRule(r, r["rvRule"] ?: "", r["rvWhy"] ?: "")
                onChange()
                onRuleWritten()
            }
            Spacer(modifier = Modifier.width(8.dp))
            SetBtn("算了，不记") {
                RecStore.setRv(id, "", "", "")
                onChange()
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text_(
            text = "写入后，以后每次解卦都会一并交给模型",
            fontSize = 10.5f,
            lineHeight = 17f,
            color = KY_DIM2,
        )
        return
    }

    if (miss.isNotEmpty()) {
        Text_(
            text = "要复盘得先接一个模型——" + miss +
                "\n复盘会把这一卦和实际结果一起交给模型，让它指出错在哪一步。" +
                "\n（不接模型的话，这一条就只留在记录里，不生成复盘。）",
            fontSize = 10.5f,
            lineHeight = 19f,
            color = KY_DIM2,
        )
        return
    }

    if (live.isNotEmpty()) {
        SelectableCardText(textKey = listOf(id, live, Session.recordsOpen)) { Text_(text = live, fontSize = 12.5f, lineHeight = 22f, color = KY_INK) }
        Spacer(modifier = Modifier.height(6.dp))
    }
    if (busy) {
        Text_(text = "复盘进行中…", fontSize = 10.5f, lineHeight = 17f, color = KY_DIM2)
    }
    if (err.isNotEmpty()) {
        Text_(text = err, fontSize = 10.5f, lineHeight = 18f, color = Color(0.70f, 0.26f, 0.22f))
        Spacer(modifier = Modifier.height(6.dp))
        SetBtn("重新复盘") {
            err = ""
            started = false
            scope.launch {
                started = true
                busy = true
                try {
                    val out = reviewCase(r) { t -> live = t }
                    RecStore.setRv(id, out.show, out.why, out.rule)
                    live = ""
                    onChange()
                } catch (e: Throwable) {
                    err = "复盘失败：" + (e.message ?: "")
                }
                busy = false
            }
        }
    }

    /* 早期版本：标「没应验」自动复盘一次（这一条还没复盘过才跑） */
    LaunchedEffect(id, rv) {
        if (rv.isEmpty() && !started && miss.isEmpty()) {
            started = true
            busy = true
            try {
                val out = reviewCase(r) { t -> live = t }
                RecStore.setRv(id, out.show, out.why, out.rule)
                live = ""
                onChange()
            } catch (e: Throwable) {
                err = "复盘失败：" + (e.message ?: "")
            }
            busy = false
        }
    }
}

@Composable
internal fun FollowAsk(r: Rec, onChange: () -> Unit) {
    val id = r["id"] ?: ""
    var q by remember { mutableStateOf("") }
    /* 刚发出去、还没在 thread 里出现的那一问：先就地显示，落库回来再让位给正式那一条 */
    var pendingQ by remember { mutableStateOf("") }
    var pendingA by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val th = threadOf(r)
    val pendingShown = pendingQ.isNotEmpty() && th.none { it.first == pendingQ }

    val send: () -> Unit = {
        val qq = q.trim()
        if (qq.isNotEmpty() && !busy) {
            busy = true
            pendingQ = qq
            pendingA = ""
            q = ""
            scope.launch {
                try {
                    val text = askModelStream(LlmStore.load(),
                                              com.xlr.k.judge.SYS,
                                              followUpUser(r, qq)) { t ->
                        pendingA = t
                    }
                    /* ⚠ 顺序要紧：先落库，再让父级重读，最后才让临时那条退场。
                       以前是「落库 → 立刻清空 → 刷新」，只要这一条没落上（id 空、分隔符被写成空格），
                       界面上就只剩「答案闪一下没了」（安卓线上报的就是这个）。 */
                    appendThread(id, qq, text)
                    pendingA = text
                    onChange()
                } catch (e: Throwable) {
                    pendingA = "问失败：" + (e.message ?: "")
                }
                busy = false
            }
        }
    }

    /* ---- 输入行：透明底 + 1px 边（跟叁页同款），回车即发 ---- */
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, KY_LINE, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (q.isEmpty()) {
            Text_(text = "接着问一句…", fontSize = 12f, lineHeight = 18f, color = KY_DIM2, maxLines = 1)
        }
        BasicTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().height(18.dp),
            textStyle = TextStyle(fontSize = 12f.sp, lineHeight = 18f.sp, color = KY_INK),
            cursorBrush = SolidColor(KY_INK),
            maxLines = 1,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    /* 发送：忙时三点跳动，不再写「问着呢」 */
    Box(
        modifier = Modifier
            .width(76.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(KY_ACCENT)
            .tapNoIndication { send() },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            ThreeDots(KY_ON_ACCENT)
        } else {
            Text_(text = "发送", fontSize = 13f, weight = 600, color = KY_ON_ACCENT)
        }
    }

    /* ---- 本轮：问句 + 流式答（落进 thread 之后这块自动消失） ---- */
    if (pendingShown) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text_(text = "问 · ", fontSize = 12.5f, lineHeight = 21f, color = KY_DIM2)
            SelectableCardText(textKey = listOf(id, pendingQ, Session.recordsOpen)) {
                Text_(text = pendingQ, fontSize = 12.5f, lineHeight = 21f, weight = 500)
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        if (pendingA.isEmpty()) {
            ThreeDots(KY_DIM2)
        } else {
            SelectableCardText(textKey = listOf(id, pendingA, Session.recordsOpen)) { Text_(text = pendingA, fontSize = 12f, lineHeight = 20f, color = KY_DIM) }
        }
        Spacer(modifier = Modifier.height(9.dp))
    }
}

/* 加载态：三点跳动（跟 iOS 同一套观感，色用本端的 accent） */
@Composable
private fun ThreeDots(color: Color) {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            t = (t + 16f) % 960f
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            var ph = (t - i * 150f) % 960f
            if (ph < 0f) ph += 960f
            val k = if (ph < 480f) ph / 480f else (960f - ph) / 480f
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp)
                    .graphicsLayer {
                        scaleX = 0.7f + 0.45f * k
                        scaleY = 0.7f + 0.45f * k
                        alpha = 0.45f + 0.55f * k
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
