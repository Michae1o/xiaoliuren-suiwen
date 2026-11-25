package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.snap
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.animation.expandVertically
import com.tencent.kuikly.compose.animation.fadeIn
import com.tencent.kuikly.compose.animation.fadeOut
import com.tencent.kuikly.compose.animation.shrinkVertically
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.heightIn
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.LazyListScope
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.semantics.Role
import com.tencent.kuikly.compose.ui.semantics.onClick
import com.tencent.kuikly.compose.ui.semantics.role
import com.tencent.kuikly.compose.ui.semantics.semantics
import com.tencent.kuikly.compose.ui.semantics.stateDescription
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* 设置 · 大模型（旧 SettingsScreen.jsx 的 #s-llm，五步向导）
   ★ 口径：预设**不要列表**，要早期版本那种「一格一家」的贴片（legacy.css .provs/.prov：
     grid auto-fill minmax(148px,1fr) / gap 10px；名字 13.5px 600、注 11px dim2）；
     并且补上「拉取可用模型」。 */

/* 编辑态挂在对象上：切到别的栏再切回来，填了一半的地址/Key 不会丢 */
internal object LlmDraft {
    private var ready = false
    var base by mutableStateOf("")
    var model by mutableStateOf("")
    var key by mutableStateOf("")
    var temp by mutableStateOf("0.5")
    var wire by mutableStateOf("direct")
    var proxy by mutableStateOf("http://127.0.0.1:8080")
    var eye by mutableStateOf(false)
    var models by mutableStateOf(listOf<String>())
    var pullHint by mutableStateOf("")
    var modelsNote by mutableStateOf("")
    /* 当前草稿属于哪一家自定义（点自定义贴片 / 刚添加时记下）；
       空串 = 不是自定义的。保存时按它回写，⛔不要只按地址反认——
       新建的自定义还没有地址记录，按地址永远认不出来（这就是上一版「保存只做了一半」的根因） */
    var customK by mutableStateOf("")

    fun ensure() {
        if (ready) return
        val c = LlmStore.load()
        base = c.base
        model = c.model
        key = c.key
        temp = c.temp.toString()
        wire = c.wire
        proxy = c.proxy
        ready = true
    }

    fun cfg() = LlmCfg(
        wire = wire,
        base = base.trim(),
        model = model.trim(),
        key = key.trim(),
        temp = temp.toDoubleOrNull() ?: 0.5,
        proxy = proxy.trim(),
    )
}

/** Navigation intent, not a model preference. Only the guide's explicit «去配置» sets it. */
internal object LlmGuideHandoff {
    var revealRequested by mutableStateOf(false)
        private set

    fun requestReveal() { revealRequested = true }
    fun consumeReveal() { revealRequested = false }
}

@Composable
internal fun LlmPage() {
    LlmDraft.ensure()
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    var advOpen by remember { mutableStateOf(false) }
    val d = LlmDraft

    /* 自定义服务商（旧 #stCustomGo / prov-del）：贴片列表来自 xlr-customs，地址/Key 记在 xlr-svcs */
    var customVer by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var delArm by remember { mutableStateOf("") }      /* 两段式删除：点一次变成「删?」 */
    LaunchedEffect(delArm) {
        if (delArm.isNotEmpty()) {
            kotlinx.coroutines.delay(4000)
            delArm = ""
        }
    }
    val customs = remember(customVer) { LlmStore.customs() }
    val tiles = remember(customVer) {
        PROVS.map { PvTile(it.k, it.n, it.note, it.base, it.model, false) } +
            customs.map { c ->
                val k = c["k"] ?: ""
                val sv = LlmStore.svcOf(k)
                PvTile(k, c["n"] ?: "", "自定义 · 自己填的地址", sv?.get("base") ?: "", sv?.get("model") ?: "", true)
            }
    }
    val cells: List<PvTile?> = tiles + listOf(null)

    /* 顶部那张卡的状态要跟着「保存 / 清空」变——原来是 remember 一次性快照，保存完还显示旧模型名 */
    var cfgSnap by remember { mutableStateOf(LlmStore.load()) }

    fun doSave() {
        val c = d.cfg()
        val miss = LlmStore.missing(c)
        if (miss.isNotEmpty()) {
            msg = miss
            good = false
        } else {
            LlmStore.save(c)
            /* 自定义那几家：把这一家的地址/模型/Key 一起记下来，下次点回来还在 */
            val ck = d.customK.ifEmpty { LlmStore.customKeyOf(c.base) }
            LlmStore.upsertSvc(ck, c.base, c.model, c.key)
            if (ck.isNotEmpty()) customVer++
            cfgSnap = c
            msg = "已保存 · " + c.model
            good = true
        }
    }

    fun doTest() {
        val c = d.cfg()
        val miss = LlmStore.missing(c)
        if (miss.isNotEmpty()) {
            msg = miss
            good = false
            return
        }
        scope.launch {
            testing = true
            msg = ""
            try {
                val out = askModel(c, "只回一个字：通", "测试连接，请只回一个字：通")
                msg = "通了 · 模型回了「" + out.trim().take(16) + "」"
                good = true
            } catch (e: Throwable) {
                msg = e.message ?: "连不上"
                good = false
            } finally {
                testing = false
            }
        }
    }

    fun doPull() {
        val c = d.cfg()
        d.pullHint = "正在连 " + c.base + " …"
        scope.launch {
            pulling = true
            try {
                val ids = pullModels(c)
                d.models = ids
                d.modelsNote = ""
                d.pullHint = "拉到了 " + ids.size + " 个，点一个就选中。"
            } catch (e: Throwable) {
                val why = (e.message ?: "").take(170)
                if (isLocalBase(c.base)) {
                    d.pullHint = "连不上本机，先把它跑起来。"
                    msg = "连不上这个地址（" + why + "）。① 服务起来了吗；② 地址写对了吗（一般以 /v1 结尾）；③ 再点一次「拉取可用模型」。"
                    good = false
                } else {
                    val fb = FALLBACK[guessProv(c.base)] ?: emptyList()
                    if (fb.isNotEmpty()) {
                        d.models = fb
                        d.modelsNote = "这是内置的常用清单——没能拉到这家的实时列表。可以直接在下面手填模型名，或换个时间再拉。"
                        d.pullHint = "没拉成，先给你一份常用清单。"
                    } else {
                        d.pullHint = "没拉成，手动填模型名吧。"
                    }
                    msg = "拉取失败：" + why
                    good = false
                }
            } finally {
                pulling = false
            }
        }
    }

    val configured = LlmStore.missing(cfgSnap).isEmpty()
    var editOpen by remember { mutableStateOf(false) }
    val revealFromGuide = LlmGuideHandoff.revealRequested && Session.settingsOpen &&
        Session.settingsTab == "llm" && !Session.guideOn
    LaunchedEffect(revealFromGuide) {
        if (revealFromGuide) {
            editOpen = true
            LlmGuideHandoff.consumeReveal()
        }
    }

    val savedProvider = when {
        !configured -> "填写服务商与模型"
        else -> tiles.firstOrNull {
            it.base.isNotBlank() && it.base.trimEnd('/') == cfgSnap.base.trimEnd('/')
        }?.n ?: "自定义接口"
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            SetLead("起卦和排盘在本机完成；配置模型后可用「让模型解卦」。")
            ModelConfigCard(
                open = editOpen,
                configured = configured,
                model = if (configured) cfgSnap.model else "尚未选择模型",
                provider = savedProvider,
            ) {
                if (editOpen) dismissIme()
                editOpen = !editOpen
            }
            Spacer(Modifier.height(9.dp))
            SetBtn(if (configured) "重新看配置引导" else "查看配置引导", small = true, primary = !configured) {
                Session.guideStep = 0
                Session.guideOn = true
            }
            Spacer(Modifier.height(14.dp))
        }
        modelConfigItem(editOpen) { SetSecTitle("1　选一个服务商") }

        /* 两列网格：预设 + 自定义，末尾跟一个「＋ 自定义」 */
        cells.chunked(2).forEach { pair ->
            modelConfigItem(editOpen) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    pair.forEachIndexed { i, t ->
                        if (i > 0) Spacer(modifier = Modifier.width(10.dp))
                        if (t == null) {
                            AddTile(Modifier.weight(1f)) {
                                adding = true
                                newName = ""
                                delArm = ""
                            }
                        } else {
                            TileCard(
                                name = t.n,
                                note = t.note,
                                on = t.base.isNotEmpty() && t.base.trimEnd('/') == d.base.trimEnd('/'),
                                custom = t.custom,
                                delArmed = t.custom && delArm == t.k,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    d.base = t.base
                                    d.model = t.model
                                    if (t.custom) d.key = LlmStore.svcOf(t.k)?.get("key") ?: ""
                                    d.customK = if (t.custom) t.k else ""
                                    msg = ""
                                    adding = false
                                },
                                onDel = if (!t.custom) null else {
                                    {
                                        if (delArm == t.k) {
                                            LlmStore.delCustom(t.k)
                                            delArm = ""
                                            if (d.customK == t.k) d.customK = ""
                                            if (d.base.trimEnd('/') == t.base.trimEnd('/')) {
                                                d.base = PROVS[0].base
                                                d.model = PROVS[0].model
                                            }
                                            customVer++
                                            msg = "已删掉自定义服务商「" + t.n + "」"
                                        } else {
                                            delArm = t.k
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (adding) {
            modelConfigItem(editOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SetField(newName, { newName = it }, ph = "给它起个名字，比如「公司网关」")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SetBtn("添加", primary = true, enabled = newName.isNotBlank()) {
                        LlmStore.addCustom(newName.trim())
                        val k = LlmStore.customs().lastOrNull()?.get("k") ?: ""
                        /* 新增的这家立刻成为当前草稿的目标，并把地址/模型/Key 清空（别把上一家的写进这一家） */
                        d.customK = k
                        d.base = ""
                        d.model = ""
                        d.key = ""
                        customVer++
                        adding = false
                        newName = ""
                        msg = if (k.isNotEmpty()) "已添加「" + LlmStore.customs().lastOrNull()?.get("n") + "」—— 下面填地址和 Key，再点保存" else ""
                    }
                }
            }
        }


        modelConfigItem(editOpen) {
            SetHint("选完会自动填好接口地址和模型名，你只要补一个 Key。")
            Spacer(modifier = Modifier.height(20.dp))

            /* ② 接口地址 */
            SetSecTitle("2　核对接口地址")
            SetLabel("接口地址（Base URL）")
            SetField(d.base, { d.base = it; msg = "" })
            SetHint("预设已自动填好，一般不用改；要换成别家的地址，直接改这里。")
            Spacer(modifier = Modifier.height(20.dp))

            /* ③ API Key */
            SetSecTitle("3　把 API Key 粘进来")
            SetLabel("API Key　必填")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    if (d.eye) {
                        SetField(d.key, { d.key = it; msg = "" })
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(veil(0.55f))
                                .border(Skin.borderW, KY_CARD_LINE, RoundedCornerShape(10.dp))
                                .tapNoIndication { d.eye = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text_(
                                text = if (d.key.isEmpty()) "sk-..." else "•".repeat(d.key.length.coerceAtMost(30)),
                                fontSize = 13f,
                                lineHeight = 20f,
                                color = if (d.key.isEmpty()) KY_DIM2 else KY_INK,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                SetBtn(if (d.eye) "隐藏" else "显示") { d.eye = !d.eye }
            }
            val cur = PROVS.firstOrNull { it.base == d.base }
            if (cur != null && cur.url.isNotEmpty()) {
                /* 早期版本 SettingsScreen.jsx L1019：`去哪里拿：<a target=_blank>{url 去掉协议}</a>`
                   ⇒ 这里整段做成可点的，点了走系统浏览器（Platform.openUrl） */
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text_(text = "去哪里拿：", fontSize = 11.5f, lineHeight = 18f, color = KY_DIM2)
                    Box(
                        modifier = Modifier
                            .tapNoIndication { Platform.openUrl?.invoke(cur.url) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Text_(
                            text = cur.url.removePrefix("https://").removePrefix("http://"),
                            fontSize = 11.5f,
                            lineHeight = 18f,
                            weight = 600,
                            color = KY_ACCENT,
                        )
                    }
                }
            } else {
                SetHint("填服务商给你的 API Key，一般以 sk- 开头。")
            }
            Spacer(modifier = Modifier.height(20.dp))

            /* ④ 挑模型：拉取 + 清单 + 手填 */
            SetSecTitle("4　挑一个模型")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetBtn(if (pulling) "拉取中…" else "拉取可用模型", primary = true) { if (!pulling) doPull() }
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    Text_(text = d.pullHint, fontSize = 11f, lineHeight = 18f, color = KY_DIM2)
                }
            }
            if (d.models.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text_(text = "共 " + d.models.size + " 个", fontSize = 11f, lineHeight = 18f, color = KY_DIM2)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        /* ★ 口径：这一格就是「选中的模型」——选中之后它显示模型名，
                           继续打字就变成过滤（过滤不中时把整份清单放回来，别让人以为清单空了）。 */
                        SetField(d.model, { d.model = it; msg = "" }, ph = "输入几个字过滤，或直接填模型名")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val q = d.model.trim()
                /* 选中的正好是清单里的某一个 ⇒ 显示整份（那一行打勾），别把别的选项藏掉；
                   只有「正在打半个名字」时才用它过滤。 */
                val exact = d.models.any { it.equals(q, true) }
                val hit = if (q.isEmpty() || exact) d.models else d.models.filter { it.lowercase().contains(q.lowercase()) }
                val shown = (if (hit.isEmpty()) d.models else hit).take(40)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(veil(0.45f))
                        .border(Skin.borderW, KY_CARD_LINE, RoundedCornerShape(10.dp))
                ) {
                    shown.forEach { m ->
                        val on = m == d.model
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .tapNoIndication { d.model = m; msg = "" }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text_(
                                    text = m,
                                    fontSize = 12f,
                                    lineHeight = 18f,
                                    color = if (on) KY_ACCENT else KY_INK,
                                    weight = if (on) 500 else 400,
                                    modifier = Modifier.weight(1f),
                                )
                                if (on) Text_(text = "✓", fontSize = 11f, lineHeight = 16f, color = KY_ACCENT)
                            }
                        }
                    }
                }
                if (d.modelsNote.isNotEmpty()) SetHint(d.modelsNote)
                SetHint("清单里没有就手填，名字照抄。")
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                SetLabel("选中的模型")
                SetField(d.model, { d.model = it; msg = "" }, hint = "清单里没有就手填，名字照抄。")
            }
            Spacer(modifier = Modifier.height(20.dp))

            /* ⑤ 测试 / 保存 */
            SetSecTitle("5　点一下，看通不通")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetBtn(if (testing) "测试中…" else "测试连接", primary = true) { if (!testing) doTest() }
                Spacer(modifier = Modifier.width(8.dp))
                SetBtn("保存", primary = true) { doSave() }
                Spacer(modifier = Modifier.width(8.dp))
                SetBtn("清空配置", danger = true) {
                    LlmStore.clear()
                    d.base = PROVS[0].base
                    d.model = PROVS[0].model
                    d.customK = ""
                    d.key = ""
                    d.wire = "direct"
                    d.proxy = "http://127.0.0.1:8080"
                    d.temp = "0.5"
                    d.models = emptyList()
                    d.pullHint = ""
                    d.modelsNote = ""
                    msg = "已清空配置"
                    good = true
                    cfgSnap = LlmStore.load()
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text_(
                    text = msg,
                    fontSize = 11.5f,
                    lineHeight = 19f,
                    weight = 500,
                    color = if (good) KY_ACCENT else Color(0.72f, 0.18f, 0.16f),
                )
            }

            /* 进阶设置 */
            Spacer(modifier = Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .tapNoIndication { advOpen = !advOpen },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text_(
                    text = (if (advOpen) "▾　" else "▸　") + "进阶设置（温度 / 连接方式）",
                    fontSize = 12f,
                    lineHeight = 20f,
                    color = KY_DIM2,
                )
            }
            if (advOpen) {
                Spacer(modifier = Modifier.height(6.dp))
                SetLabel("温度（0.2 更稳、0.8 更活，解卦建议 0.5 左右）")
                SetField(d.temp, { d.temp = it; msg = "" })
                Spacer(modifier = Modifier.height(14.dp))
                SetLabel("连接方式")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetBtn("直连", primary = d.wire == "direct") { d.wire = "direct" }
                    Spacer(modifier = Modifier.width(8.dp))
                    SetBtn("本地中转", primary = d.wire == "proxy") { d.wire = "proxy" }
                }
                SetHint("直连最简单，一般不用改。只有这家服务商不让网页调用它、怎么换都连不上时，才需要切到「本地中转」——那要另有一个中转程序在这台电脑上跑着。")
                if (d.wire == "proxy") {
                    Spacer(modifier = Modifier.height(12.dp))
                    SetLabel("中转地址")
                    SetField(d.proxy, { d.proxy = it; msg = "" })
                }
            }
            /* 早期版本这里还有一条「重新看配置引导」，已并入顶部那张卡（同一个动作，别摆两处） */
            Spacer(modifier = Modifier.height(34.dp))
        }
        item {
            if (!editOpen) {
                val status = when {
                    testing -> "正在测试连接…"
                    pulling -> "正在拉取模型…"
                    else -> msg
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text_(status, fontSize = 11.5f, lineHeight = 18f, color = KY_DIM2)
                }
            }
        }
    }
}

private const val ModelConfigEnterMs = 265
private const val ModelConfigExitMs = 210

/** The saved model summary is the disclosure control; the guide remains a separate action. */
@Composable
private fun ModelConfigCard(
    open: Boolean,
    configured: Boolean,
    model: String,
    provider: String,
    onToggle: () -> Unit,
) {
    var touchDown by remember { mutableStateOf(false) }
    var tapPulse by remember { mutableStateOf(false) }
    var pulseJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val pressed = touchDown || tapPulse
    val pressProgress by animateFloatAsState(
        if (pressed) 1f else 0f,
        if (pressed) snap() else tween(155, easing = KY_EASE_OUT),
    )
    fun activate() {
        // Even a touch shorter than one frame leaves a small visible pulse.
        // A drag/cancel never reaches this path; the existing gesture releases touchDown.
        pulseJob?.cancel()
        tapPulse = true
        pulseJob = scope.launch {
            delay(80)
            tapPulse = false
        }
        onToggle()
    }
    val angle by animateFloatAsState(
        if (open) 90f else 0f,
        if (open) tween(ModelConfigEnterMs, easing = KY_EASE_OUT) else tween(ModelConfigExitMs, easing = KY_EASE_IN),
    )
    // Mix against the actual settings background, once, into an opaque flat
    // surface. Only this fill responds to a press; the hit box and type stay put.
    val skin = Skin.def
    val inkAmount = (if (skin.dark) .083f else .052f) + .048f * pressProgress
    val fill = Color(
        skin.bg.red + (skin.ink.red - skin.bg.red) * inkAmount,
        skin.bg.green + (skin.ink.green - skin.bg.green) * inkAmount,
        skin.bg.blue + (skin.ink.blue - skin.bg.blue) * inkAmount,
        1f,
    )
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp)
            .background(fill, RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (open) "已展开" else "已收起"
                onClick(label = if (open) "收起模型配置" else "展开模型配置") { activate(); true }
            }
            .tapPressable(onPress = { touchDown = it }, onTap = { activate() }),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text_("模型配置", fontSize = 14f, lineHeight = 20f, weight = 600)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (configured) KY_OK else KY_DIM2))
                    Spacer(Modifier.width(6.dp))
                    Text_(if (configured) "已配置" else "尚未配置", fontSize = 11f, lineHeight = 17f, color = KY_DIM)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.End) {
                ModelConfigValue(model, fontSize = 13f, lineHeight = 20f, color = KY_INK, weight = 600)
                Spacer(Modifier.height(4.dp))
                ModelConfigValue(provider, fontSize = 11f, lineHeight = 17f, color = KY_DIM, weight = 400)
            }
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.size(16.dp).graphicsLayer { rotationZ = angle }) {
                val x = size.width / 2f
                val y = size.height / 2f
                val dx = size.width * .14f
                val dy = size.height * .29f
                val tip = Offset(x + dx, y)
                val stroke = size.width * .084f
                drawLine(KY_DIM, Offset(x - dx, y - dy), tip, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(KY_DIM, tip, Offset(x - dx, y + dy), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ModelConfigValue(text: String, fontSize: Float, lineHeight: Float, color: Color, weight: Int) {
    // The non-weighted, fixed chevron reserves its own width; content ends in
    // a native ellipsis inside the already bounded value column.
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = 0f.sp,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Keep the provider grid lazy; each visible section reveals from the top and clips during motion. */
private fun LazyListScope.modelConfigItem(open: Boolean, content: @Composable () -> Unit) {
    item {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(animationSpec = tween(ModelConfigEnterMs, easing = KY_EASE_OUT), expandFrom = Alignment.Top)
                    + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(ModelConfigExitMs, easing = KY_EASE_IN), shrinkTowards = Alignment.Top)
                    + fadeOut(animationSpec = tween(155)),
            ) {
                Column(Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

/* 一家的贴片（旧 .prov：细边框方块、名字 13f/500、注 10.5/灰；选中 = 强调色边框 + 浅底 + ✓）
   custom = 自定义那几家：右上角多一个 ✕（两段式：点一次变「删?」再点才删） */
private data class PvTile(
    val k: String,
    val n: String,
    val note: String,
    val base: String,
    val model: String,
    val custom: Boolean,
)

@Composable
private fun TileCard(
    name: String,
    note: String,
    on: Boolean,
    custom: Boolean,
    delArmed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onDel: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) veil(0.92f) else veil(0.40f))
            .border(Skin.borderW, if (on) KY_ACCENT_LINE else veil(0.72f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            /* 名字那格可点（✕ 是兄弟节点，别让父层 tap 抢走 —— 同 原记录-40） */
            Box(modifier = Modifier.weight(1f).tapNoIndication { onClick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text_(text = name, fontSize = 13f, lineHeight = 19f, weight = 500, maxLines = 1)
                    if (on) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text_(text = "✓", fontSize = 11f, lineHeight = 16f, color = KY_ACCENT)
                    }
                }
            }
            if (custom && onDel != null) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .tapNoIndication { onDel() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text_(
                        text = if (delArmed) "删?" else "✕",
                        fontSize = if (delArmed) 9.5f else 12f,
                        lineHeight = 16f,
                        weight = 500,
                        color = if (delArmed) KY_DANGER else KY_DIM2,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(modifier = Modifier.fillMaxWidth().tapNoIndication { onClick() }) {
            Text_(text = note, fontSize = 10.5f, lineHeight = 16f, color = KY_DIM2, maxLines = 1)
        }
    }
}

/** 网格末尾那格「＋ 自定义」——排版和别的贴片一样（名字在上、说明在下、都左对齐），
    只是没有底色，靠一条淡边区分。：「＋自定义怎么居中了，应该和预设一样」。 */
@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(Skin.borderW, KY_LINE, RoundedCornerShape(10.dp))
            .tapNoIndication { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text_(text = "＋ 自定义", fontSize = 13f, lineHeight = 19f, weight = 500, color = KY_DIM, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text_(text = "填自己家的地址", fontSize = 10.5f, lineHeight = 16f, color = KY_DIM2, maxLines = 1)
    }
}

