package com.xlr.k.pages

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.animation.AnimatedVisibility
import com.tencent.kuikly.compose.animation.expandVertically
import com.tencent.kuikly.compose.animation.shrinkVertically
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
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
import com.tencent.kuikly.compose.foundation.shape.CircleShape
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.draw.clipToBounds
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.graphics.Brush
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.StrokeCap
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.semantics.Role
import com.tencent.kuikly.compose.ui.semantics.clearAndSetSemantics
import com.tencent.kuikly.compose.ui.semantics.contentDescription
import com.tencent.kuikly.compose.ui.semantics.onClick
import com.tencent.kuikly.compose.ui.semantics.role
import com.tencent.kuikly.compose.ui.semantics.semantics
import com.tencent.kuikly.compose.ui.semantics.stateDescription
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.text.style.TextAlign
import com.tencent.kuikly.compose.ui.text.style.TextOverflow
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A saved-model summary is also the disclosure; the adjacent guide remains a separate control. */
@Composable
private fun ModelSettingsSummary(
    configured: Boolean,
    model: String,
    provider: String,
    open: Boolean,
    reducedMotion: Boolean,
    onToggle: () -> Unit,
) {
    val pressScope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    var confirmedPulse by remember { mutableStateOf(false) }
    var pulseReset by remember { mutableStateOf<Job?>(null) }
    // Preserve a visible beat after a quick successful tap. A canceled drag never starts a pulse.
    val down = held || confirmedPulse
    val pressure by animateFloatAsState(
        if (down) 1f else 0f,
        tween(if (reducedMotion) 0 else if (down) 48 else 170, easing = KY_EASE_OUT),
    )
    val activate = {
        pulseReset?.cancel()
        confirmedPulse = true
        pulseReset = pressScope.launch {
            delay(96)
            confirmedPulse = false
        }
        onToggle()
    }
    val rotation by animateFloatAsState(
        if (open) 90f else 0f,
        tween(
            if (reducedMotion) 0 else if (open) ModelRevealInMs else ModelRevealOutMs,
            easing = if (open) KY_EASE_OUT else KY_EASE_IN,
        ),
    )
    val state = if (configured) "已配置" else "配置未完成"
    val modelLabel = if (configured) model else "尚未选择模型"
    val providerLabel = if (configured) provider else "填写服务商与模型"
    val ink = KY_DIM
    // Transform only the glass and its contents, not the full-size input/accessibility bounds.
    Box(
        modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "模型配置，$state，$modelLabel，$providerLabel"
                stateDescription = if (open) "已展开" else "已收起"
                onClick(label = if (open) "收起" else "展开") { activate(); true }
            }
            .tapPressable(onPress = { held = it }, onTap = activate),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .graphicsLayer {
                    if (!reducedMotion) {
                        scaleX = 1f - .023f * pressure
                        scaleY = 1f - .055f * pressure
                    }
                }
                .modelSummaryGlass(pressure, open)
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(.43f).clearAndSetSemantics { }) {
                Text_("模型配置", fontSize = 14f, lineHeight = 20f, weight = 600, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (configured) KY_ACCENT else KY_DIM2))
                    Spacer(Modifier.width(6.dp))
                    Text_(state, color = KY_DIM, fontSize = 11.5f, lineHeight = 16f, maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(.57f).clearAndSetSemantics { },
                horizontalAlignment = Alignment.End,
            ) {
                Text(modelLabel, color = KY_INK, fontSize = 13.5.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight(600), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(providerLabel, color = KY_DIM, fontSize = 11.5.sp, lineHeight = 16.sp,
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(9.dp))
            Canvas(Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }) {
                val tip = Offset(size.width * .64f, size.height * .5f)
                drawLine(ink, Offset(size.width * .40f, size.height * .27f), tip, strokeWidth = 1.45.dp.toPx(), cap = StrokeCap.Round)
                drawLine(ink, tip, Offset(size.width * .40f, size.height * .73f), strokeWidth = 1.45.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/** The existing skin glass vocabulary, without a gray face or any external cast shadow. */
private fun Modifier.modelSummaryGlass(pressure: Float, open: Boolean): Modifier {
    val specular = (if (open) .025f else 0f) + pressure * .10f
    val dark = Skin.dark
    val face = Skin.def.surface
    val rim = if (dark) Skin.def.ink else Color.White
    val shape = RoundedCornerShape(12.dp)
    // Use the Kuikly brush path: unlike a flat translucent background, it renders alpha reliably.
    return clip(shape)
        .background(Brush.verticalGradient(
            0f to rim.copy(alpha = ((if (dark) .40f else .86f) + specular).coerceAtMost(1f)),
            .026f to rim.copy(alpha = (if (dark) .17f else .46f) + specular),
            .12f to face.copy(alpha = (if (dark) .25f else .31f) + specular),
            .78f to face.copy(alpha = (if (dark) .12f else .15f) + specular),
            .965f to face.copy(alpha = if (dark) .095f else .115f),
            1f to rim.copy(alpha = if (dark) .10f else .37f),
        ))
        .background(Brush.horizontalGradient(
            0f to rim.copy(alpha = if (dark) .15f else .45f),
            .045f to rim.copy(alpha = if (dark) .027f else .09f),
            .18f to Color.Transparent,
            .965f to Color.Transparent,
            1f to rim.copy(alpha = if (dark) .085f else .22f),
        ))
        .border(.7.dp + .45.dp * pressure, rim.copy(alpha = (if (dark) .18f else .49f) + .28f * pressure), shape)
}

private const val ModelRevealInMs = 245
private const val ModelRevealOutMs = 190

/** Stable slots let AnimatedVisibility retain the form through exit and reverse from its current height. */
private fun LazyListScope.modelEditItem(open: Boolean, reducedMotion: Boolean, content: @Composable () -> Unit) {
    item {
        if (reducedMotion) {
            if (open) Column(content = { content() })
        } else {
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                AnimatedVisibility(
                    visible = open,
                    enter = expandVertically(animationSpec = tween(ModelRevealInMs, easing = KY_EASE_OUT), expandFrom = Alignment.Top),
                    exit = shrinkVertically(animationSpec = tween(ModelRevealOutMs, easing = KY_EASE_IN), shrinkTowards = Alignment.Top),
                ) {
                    Column { content() }
                }
            }
        }
    }
}

/* 设置 · 大模型（旧 SettingsScreen.jsx 的 #s-llm，五步向导）
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
    val reducedMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
    var canShowCollapsedStatus by remember { mutableStateOf(true) }
    LaunchedEffect(editOpen, reducedMotion) {
        if (!editOpen) {
            if (!reducedMotion && !canShowCollapsedStatus) delay(ModelRevealOutMs.toLong())
            canShowCollapsedStatus = true
        }
    }
    val providerName = tiles.firstOrNull {
        it.base.isNotBlank() && it.base.trimEnd('/') == cfgSnap.base.trimEnd('/')
    }?.n ?: "自定义接口"

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            Column(Modifier.fillMaxWidth()) {
                Text_("起卦、排盘和断卦依据均在本机计算。配置后可使用「让模型解卦」。",
                    fontSize = 11.5f, lineHeight = 18f, color = KY_DIM)
                Spacer(Modifier.height(12.dp))
                ModelSettingsSummary(configured, cfgSnap.model, providerName, editOpen, reducedMotion) {
                    if (editOpen) Platform.hideIme?.invoke() else canShowCollapsedStatus = false
                    editOpen = !editOpen
                }
                Spacer(Modifier.height(8.dp))
                SetBtn(if (configured) "重新看引导" else "查看配置引导", small = true) {
                    Session.guideStep = 0
                    Session.guideOn = true
                }
                // Do not insert a result above the form midway through its upward collapse.
                if (!editOpen && (reducedMotion || canShowCollapsedStatus)) {
                    val status = when {
                        testing -> "正在测试草稿配置…"
                        pulling -> d.pullHint
                        else -> msg
                    }
                    if (status.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text_(status, fontSize = 11.5f, lineHeight = 18f, color = KY_DIM2)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        modelEditItem(editOpen, reducedMotion) { SetSecTitle("1　选择服务商", top = 0) }

        /* 两列网格：预设 + 自定义，末尾跟一个「＋ 自定义」 */
        cells.chunked(2).forEach { pair ->
            modelEditItem(editOpen, reducedMotion) {
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
            modelEditItem(editOpen, reducedMotion) {
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


        modelEditItem(editOpen, reducedMotion) {
            SetHint("预设会填写接口地址和模型名；再补服务商的 API Key。")
            Spacer(modifier = Modifier.height(20.dp))

            /* ② 接口地址 */
            SetSecTitle("2　接口与凭据")
            SetLabel("接口地址（Base URL）")
            SetField(d.base, { d.base = it; msg = "" })
            SetHint("自定义服务商可在这里填写地址。")
            Spacer(modifier = Modifier.height(10.dp))

            /* API Key */
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
            SetSecTitle("3　选择模型")
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
            SetSecTitle("4　测试与保存")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetBtn(if (testing) "测试中…" else "测试连接", enabled = !testing) { if (!testing) doTest() }
                Spacer(modifier = Modifier.width(8.dp))
                SetBtn("保存", primary = true) { doSave() }

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
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
            Spacer(Modifier.height(12.dp))
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
            Spacer(modifier = Modifier.height(34.dp))
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
            /* 名字那格可点（✕ 是兄弟节点，别让父层 tap 抢走 —— 同 §五-40） */
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
    只是没有底色，靠一条淡边区分。。 */
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

