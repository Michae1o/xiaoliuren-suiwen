package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ui.layout.onSizeChanged
import com.tencent.kuikly.compose.ui.semantics.Role
import com.tencent.kuikly.compose.ui.semantics.clearAndSetSemantics
import com.tencent.kuikly.compose.ui.semantics.onClick
import com.tencent.kuikly.compose.ui.semantics.role
import com.tencent.kuikly.compose.ui.semantics.selected
import com.tencent.kuikly.compose.ui.semantics.semantics
import com.tencent.kuikly.compose.foundation.selection.toggleable
import com.tencent.kuikly.compose.coil3.rememberAsyncImagePainter
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.draw.clip
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.animation.core.Animatable
import com.tencent.kuikly.compose.animation.core.animateFloatAsState
import com.tencent.kuikly.compose.animation.core.tween
import com.tencent.kuikly.compose.foundation.gestures.detectHorizontalDragGestures
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.ui.geometry.CornerRadius
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.geometry.Size
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.graphics.graphicsLayer
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput

/* 设置里剩下四栏（外观 / 数据 / 关于 / 常见问题 / 说明）
   正文照抄旧 SettingsScreen.jsx；K 版还没搬到的功能（卦例本、皮肤九套、更新）按钮留着但置灰。 */

/* ---------------- 外观 ---------------- */
@Composable
internal fun SkinPage() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            SetSecTitle("主题", top = 0)
            var i = 0
            while (i < SKINS.size) {
                val a = SKINS[i]
                val b = SKINS.getOrNull(i + 1)
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Box(modifier = Modifier.weight(1f)) { SkinTile(a) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (b != null) SkinTile(b)
                    }
                }
                i += 2
            }

            Spacer(Modifier.height(10.dp))
            SetSecTitle("显示", top = 0)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(veil(0.42f)).border(Skin.borderW, KY_LINE, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text_("日夜", fontSize = 13f, lineHeight = 20f, weight = 600)
                        Text_(if (Skin.dark) "深色 · " + Skin.def.name else "浅色 · " + Skin.def.name,
                            fontSize = 11f, lineHeight = 17f, color = KY_DIM2)
                    }
                    SetBtn(if (Skin.dark) "切到白天" else "切到黑夜", small = true) { Skin.toggleDayNight() }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
                Spacer(Modifier.height(12.dp))
                Text_("背景雾气", fontSize = 13f, lineHeight = 20f, weight = 600)
                Spacer(Modifier.height(8.dp))
                FogLevelControl()
                Spacer(Modifier.height(6.dp))
                val hint = when {
                    Skin.fogLevel == 0 -> "淡雾静置，减少动态消耗。"
                    Skin.fogLevel == 1 -> "雾气沿平滑路径缓缓游移。"
                    !realtimeFogAvailable() -> "当前系统使用轻柔流动，实时流雾需要 Android 13 或以上。"
                    else -> "雾形持续变化、交织流动。"
                }
                Text_(hint, fontSize = 11f, lineHeight = 17f, color = KY_DIM2)
                if (Skin.fogLevel > 0) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(KY_LINE))
                    FogFreezeControl()
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/** 用真实主题色与该套北斗素材画一个静态预览，不启动开屏动画。 */
@Composable
private fun SkinTile(d: SkinDef) {
    val on = d.key == Skin.cur
    val corner = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(corner)
            .background(veil(0.42f))
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                selected = on
                onClick { Skin.apply(d.key); true }
            }
            .tapNoIndication { Skin.apply(d.key) },
    ) {
        Box(Modifier.fillMaxWidth().height(59.dp).background(d.bg)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp)) {
                Box(Modifier.width(23.dp).height(2.dp).background(d.accent))
                Spacer(Modifier.height(7.dp))
                Column(Modifier.width(54.dp).height(31.dp).clip(RoundedCornerShape(5.dp))
                    .background(d.surface).border(0.5.dp, d.line2, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)) {
                    Box(Modifier.width(14.dp).height(2.dp).background(d.ink))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(d.line2))
                }
            }
            Image(
                painter = rememberAsyncImagePainter("assets://splash-orbit/${d.key}/north.png"),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(61.dp, 48.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text_(d.name, fontSize = 13f, lineHeight = 19f, weight = 600)
                Text_(d.tag, fontSize = 10.5f, lineHeight = 15f, color = KY_DIM2)
            }
            if (on) {
                Box(Modifier.size(19.dp).clip(RoundedCornerShape(10.dp)).background(KY_ACCENT_BG).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center) {
                    Text_("✓", fontSize = 12f, lineHeight = 16f, weight = 700, color = KY_ACCENT)
                }
            }
        }
    }
}

/* ---------------- 数据 ---------------- */
@Composable
internal fun DataPage() {
    var msg by remember { mutableStateOf("") }
    var cleanupMsg by remember { mutableStateOf("") }
    var ver by remember { mutableStateOf(0) }
    val caseN = remember(ver, RecStore.caseRevision) { RecStore.load().size }
    val ruleN = remember(ver, RecStore.ruleRevision) { RecStore.loadRules().size }
    var rulesArmed by remember { mutableStateOf(false) }
    LaunchedEffect(rulesArmed, ruleN > 0) {
        if (rulesArmed) {
            if (ruleN > 0) kotlinx.coroutines.delay(4000)
            rulesArmed = false
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            SetSecTitle("保存位置", top = 0)
            /* 保存位置
               设过 ⇒ 导出直接写进那个文件夹，不再每次弹面板；没设 ⇒ 空着，导出时弹系统「保存到…」 */
            var dirLabel by remember { mutableStateOf(Platform.exportDirLabel?.invoke()) }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text_(
                    text = dirLabel ?: "没设，导出时自己选",
                    fontSize = 11f,
                    lineHeight = 18f,
                    color = if (dirLabel == null) KY_DIM2 else KY_DIM,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetBtn(if (dirLabel == null) "选文件夹" else "改位置") {
                        Platform.pickExportDir?.invoke { lbl -> if (lbl != null) dirLabel = lbl }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SetBtn("打开文件夹", enabled = dirLabel != null) {
                        Platform.openExportDir?.invoke()
                    }
                    if (dirLabel != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SetBtn("恢复默认", small = true) {
                            Platform.resetExportDir?.invoke()
                            dirLabel = null
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
            Spacer(Modifier.height(14.dp))
            SetSecTitle("备份与恢复", top = 0)
            SetDataRow(
                title = "卦例本",
                sub = "记下来的卦。标过「应验/没应验」的，会在下次解卦时一并交给模型作校准。",
                count = caseN.toString() + " 则",
                canExport = caseN > 0,
                onExport = {
                    exportAsk("道家小六壬-卦例本.json", RecStore.exportCasesText()) { ok ->
                        msg = if (ok == null) "没保存（可能取消了）。" else "✓ 已导出 " + caseN + " 则 → " + ok
                        Session.say(msg)
                        ver++
                    }
                },
            )
            SetDataRow(
                title = "准则",
                sub = "复盘里你逐条确认过的修正，解卦时会一起交给模型。导得走，换机器不至于从零调教。",
                count = ruleN.toString() + " 条",
                canExport = ruleN > 0,
                onExport = {
                    exportAsk("道家小六壬-准则.json", RecStore.exportRulesText()) { ok ->
                        msg = if (ok == null) "没保存（可能取消了）。" else "✓ 已导出 " + ruleN + " 条准则 → " + ok
                        Session.say(msg)
                        ver++
                    }
                },
            )
            SetDataRow(
                title = "旧数据导入",
                sub = "从早期版本或别的机器带来的备份。只往里加，不覆盖本机已有的，重复的自动跳过。",
                count = "",
                canExport = true,
                exportLabel = "导入",
                onExport = {
                    Platform.pickText?.invoke { txt ->
                        if (txt == null) {
                            msg = "没读到文件（可能取消了）。"
                        } else {
                            val plain = txt.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                            var cases: List<com.xlr.k.judge.Rec> = emptyList()
                            var rules: List<com.xlr.k.judge.Rec> = emptyList()
                            try {
                                if (plain.startsWith("[")) {
                                    for (r in com.xlr.k.judge.parseRows(plain)) {
                                        if ((r["rule"] ?: "").isNotEmpty()) rules = rules + r
                                        else if (r.containsKey("q") || r.containsKey("gua")) cases = cases + r
                                    }
                                } else {
                                    cases = com.xlr.k.judge.parseRows(com.xlr.k.judge.sliceArray(plain, "cases"))
                                    rules = com.xlr.k.judge.parseRows(com.xlr.k.judge.sliceArray(plain, "rules"))
                                }
                            } catch (e: Throwable) {
                                msg = "这个文件不是本应用导出的 JSON。"
                                ver++
                                return@invoke
                            }
                            if (cases.isEmpty() && rules.isEmpty()) {
                                msg = "这个文件里没有卦例，也没有准则。"
                            } else {
                                val nc = RecStore.mergeCases(cases)
                                val nr = RecStore.mergeRules(rules)
                                msg = "✓ 导入 " + nc + " 则卦例、" + nr + " 条准则（重复的跳过了）"
                                Session.say(msg)
                            }
                        }
                        ver++
                    }
                },
            )
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text_(msg, fontSize = 11.5f, lineHeight = 19f, color = KY_ACCENT, weight = 500)
            }
            Spacer(Modifier.height(16.dp))
            SetSecTitle("清理与重置", top = 0)
            DataActionRow("清空卦例本", "卦例移入回收站，可恢复。", "清空", enabled = caseN > 0) {
                RecStore.clearAll()
                cleanupMsg = "卦例本已清空（进了回收站，能捞回来）"
                ver++
            }
            DataActionRow(
                "清空准则",
                if (rulesArmed) "清空后无法恢复；卦例本不受影响。" else "清空复盘确认过的准则，不影响卦例本。",
                if (rulesArmed) "确认清空" else "清空",
                enabled = ruleN > 0,
            ) {
                if (rulesArmed) {
                    rulesArmed = false
                    RecStore.clearRules()
                    cleanupMsg = "准则已清空（卦例本不受影响）"
                    ver++
                } else {
                    rulesArmed = true
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text_(text = "全部重置", fontSize = 13.5f, lineHeight = 22f, weight = 500)
                    Text_(
                        text = "清掉模型配置、主题选择和卦例本，回到刚装好的状态。",
                        fontSize = 11f,
                        lineHeight = 18f,
                        color = KY_DIM2,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                /* 两段式确认（K 端没有系统对话框；点一下变「真的要重置？」，4 秒不动自动撤回） */
                var resetArm by remember { mutableStateOf(false) }
                LaunchedEffect(resetArm) {
                    if (resetArm) {
                        kotlinx.coroutines.delay(4000)
                        resetArm = false
                    }
                }
                SetBtn(if (resetArm) "真的要重置？" else "重置", danger = true) {
                    if (!resetArm) {
                        resetArm = true
                    } else {
                        LlmStore.clear()
                        RecStore.wipeCases()
                        resetArm = false
                        cleanupMsg = "✓ 已重置：模型配置、卦例本都清空了"
                        Session.say(cleanupMsg)
                        ver++
                    }
                }
            }
            if (cleanupMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text_(cleanupMsg, fontSize = 11.5f, lineHeight = 19f, color = KY_ACCENT, weight = 500)
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/* 导出：先弹系统「保存到…」让用户自己挑位置；宿主没实现（老 ROM / 没有文件选择器）
   就退回写固定「下载/道家小六壬/」。done 拿到的是给用户看的文件名，取消或失败为 null。 */
private fun exportAsk(name: String, body: String, done: (String?) -> Unit) {
    val asFn = Platform.saveFileAs
    if (asFn != null) asFn(name, body, done) else done(Platform.saveFile?.invoke(name, body))
}

@Composable
private fun SetDataRow(
    title: String,
    sub: String,
    count: String,
    canExport: Boolean,
    onExport: () -> Unit,
    exportLabel: String = "导出",
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text_(text = title, fontSize = 13.5f, lineHeight = 22f, weight = 500)
                Text_(text = sub, fontSize = 11f, lineHeight = 18f, color = KY_DIM2)
            }
            if (count.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text_(text = count, fontSize = 11.5f, lineHeight = 20f, color = KY_DIM2)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetBtn(exportLabel, enabled = canExport, onClick = onExport)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
}

@Composable
private fun DataActionRow(title: String, sub: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text_(title, fontSize = 13.5f, lineHeight = 22f, weight = 500)
            Text_(sub, fontSize = 11f, lineHeight = 18f, color = KY_DIM2)
        }
        Spacer(Modifier.width(8.dp))
        SetBtn(action, enabled = enabled, small = true, danger = true,
            modifier = Modifier.width(76.dp), keepDisabledDangerOutline = true, onClick = onClick)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
}

/* ---------------- 关于 ---------------- */
@Composable
internal fun AboutPage() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf<UpdItem?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text_(text = "遂问", fontSize = 20f, lineHeight = 28f, color = KY_INK, weight = 600)
                Text_(
                    text = "　v" + (Platform.version ?: "0.1"),
                    fontSize = 12.5f,
                    lineHeight = 25f,
                    color = KY_DIM,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            SetSecTitle("回观")
            Text_("按周或月回顾本机卦例。", fontSize = 11.5f, lineHeight = 18f, color = KY_DIM2)
            Spacer(Modifier.height(8.dp))
            SetBtn("打开回观", primary = true) { RecallScreen.enterFromAbout() }
            Spacer(Modifier.height(12.dp))

            /* ★ app 内更新（早期版本这一栏只有版本号 + 更新入口；简介/免责已搬「说明」栏） */
            if (Upd.available) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text_(text = "版本更新", fontSize = 13.5f, lineHeight = 22f, weight = 500)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SetBtn(
                            label = if (busy) "请稍等…" else if (ready != null) "立即更新" else "检查更新",
                            primary = ready != null,
                            enabled = !busy,
                            onClick = {
                                if (busy) return@SetBtn
                                val it = ready
                                if (it != null) {
                                    /* 第二次点：真下载，下完交系统安装器 */
                                    busy = true
                                    msg = ""
                                    scope.launch {
                                        try {
                                            val p = Upd.download(it)
                                            busy = false
                                            msg = "已下好，正在打开系统安装器…"
                                            try {
                                                Upd.install(p)
                                            } catch (e2: Exception) {
                                                msg = e2.message ?: "打不开系统安装器"
                                            }
                                        } catch (e: Exception) {
                                            busy = false
                                            msg = e.message ?: "下载失败，稍后再试"
                                        }
                                    }
                                } else {
                                    busy = true
                                    msg = ""
                                    scope.launch {
                                        try {
                                            val n = Upd.check()
                                            busy = false
                                            if (n == null) {
                                                msg = "已是最新版本（v" + (Platform.version ?: "?") + "）"
                                                ready = null
                                            } else {
                                                ready = n
                                                msg = "发现新版本 v" + (n.versionName.ifEmpty { "" + n.versionCode }) +
                                                    "　点「立即更新」下载"
                                            }
                                        } catch (e: Exception) {
                                            busy = false
                                            ready = null
                                            msg = e.message ?: "暂时连不上更新服务，稍后再试"
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (msg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text_(text = msg, fontSize = 11.5f, lineHeight = 19f, color = KY_ACCENT, weight = 500)
                    }
                }
            }

            /* 此前：这段（版权 + 三条前提）挪到关于页末尾；「版权声明」四个字删掉，
               换成各模块之间那条普通分割线（跟数据页/关于页其它处同一款：1dp KY_LINE） */
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
            Spacer(modifier = Modifier.height(12.dp))
            Text_(
                text = "本软件以邵一尘先生的小六壬神通·卜法卷为基础，部分功能及生成内容由人工智能技术辅助完成，结果仅供参考。所有内容仅供交流、学习使用，不用于商业用途；如涉及版权争议，请权利人联系删除，造成不便敬请谅解。",
                fontSize = 12f,
                lineHeight = 21f,
                color = KY_DIM,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text_(text = "三条前提：无事不卦、心动即卦、一事不二卦。", fontSize = 12f, lineHeight = 21f, weight = 500)

            Spacer(modifier = Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
            SetSecTitle("回观 · 星图与口径", top = 14)
            Text_(
                "宋代皇祐天图复原（约 1052 年）。星官与宿距：孙殳玮，Stellarium 中国宋代星空 v26.2；星位与星等：David Nash / Astronomy Nexus，HYG v4.1。星图数据及衍生图以 CC BY-SA 4.0 授权，并非实时星空。",
                fontSize = 11.5f, lineHeight = 20f, color = KY_DIM,
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text_(
                "回观只读本机卦例；历史星点与卦例亮点分开。卦例亮点超过 72 枚时做抽样，显示的统计数字仍是全量。",
                fontSize = 11.5f, lineHeight = 20f, color = KY_DIM,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SetBtn("宋代星空数据", small = true) {
                Platform.openUrl?.invoke("https://github.com/Stellarium/stellarium/blob/v26.2/skycultures/chinese_song_dynasty/description.md")
            }
            Spacer(modifier = Modifier.height(4.dp))
            SetBtn("HYG 星表", small = true) {
                Platform.openUrl?.invoke("https://github.com/astronexus/HYG-Database/blob/main/hyg/README.md")
            }
            Spacer(modifier = Modifier.height(4.dp))
            SetBtn("CC BY-SA 4.0 许可", small = true) {
                Platform.openUrl?.invoke("https://creativecommons.org/licenses/by-sa/4.0/")
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/* ---------------- 常见问题 ---------------- */
@Composable
internal fun FaqPage() {
    var openQ by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item { SetSecTitle("使用答疑", top = 0); SetHint("轻点问题展开答案。") }
        items(FAQ_ITEMS) { it ->
            val on = openQ == it.q
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tapNoIndication { openQ = if (on) "" else it.q }
                    .padding(vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text_(
                        text = it.q,
                        fontSize = 13f,
                        lineHeight = 21f,
                        weight = if (on) 600 else 500,
                        modifier = Modifier.weight(1f),
                    )
                    Text_(
                        text = if (on) "▾" else "▸",
                        fontSize = 12f,
                        lineHeight = 20f,
                        color = KY_DIM2,
                    )
                }
                if (on) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text_(text = it.a, fontSize = 11.5f, lineHeight = 21f, color = KY_DIM2)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
        }
        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

/* ---------------- 说明 ---------------- */
@Composable
internal fun NotePage() {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            /* 里那段（版权声明 + 三条前提）挪到关于页末尾；
               「三条前提」保持一行，第一段给它一个小标题、跟其他块同构。 */
            SetSecTitle("免责声明", top = 0)
            listOf(
                "本软件是传统术数文化的学习与自用工具。起卦、排盘、本机速断与模型断辞都只是按这套体系的推演，不构成预测、承诺或任何专业意见；请当作参考，不必据此替自己做重大决定。",
                "医疗、诉讼、投资、婚育、就业这类专业事项，请找有资质的专业人士；本软件的输出不能替代他们的判断，也不该被用来拖延就诊或决策。",
                "模型断辞由你自己配置的第三方模型服务生成，作者无法预知、也无法控制它写了什么；其中若有错漏、偏差或不当表述，作者不承担责任。",
                "请勿在「所问」「背景」里填身份证号、银行卡号、病历等敏感个人信息 —— 你填的内容会随请求发送给你自己配置的那家服务商。",
                "本软件按「现状」提供，不承诺无缺陷、不承诺永久可用。因使用本软件（含模型输出有误、服务中断、记录丢失）造成的任何直接或间接损失，由使用者自行承担。",
                "请在所在地法律与公序良俗的范围内使用；不得用它行骗、敛财，或做其他违法的事。",
            ).forEach { t ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                    Text_(text = "·", fontSize = 12f, lineHeight = 21f, color = KY_DIM2, modifier = Modifier.width(10.dp))
                    Text_(text = t, fontSize = 12f, lineHeight = 21f, color = KY_DIM, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
            Spacer(Modifier.height(12.dp))
            SetSecTitle("联系作者", top = 0)
            Text_(text = "软件使用反馈：微信 Trick_Mage", fontSize = 12f, lineHeight = 21f, color = KY_DIM)
            Text_(text = "小六壬交流：微信 gny950107", fontSize = 12f, lineHeight = 21f, color = KY_DIM)
            Text_(text = "添加请注明来意。", fontSize = 12f, lineHeight = 21f, color = KY_DIM2)

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(KY_LINE))
            Spacer(Modifier.height(12.dp))
            SetSecTitle("开源仓库", top = 0)
            /* 早期版本这里是 <a href> 走 openOut 交系统浏览器（SettingsScreen.jsx:1202）⇒ K 里必须可点，
               Platform.openUrl 早就接了（引导里的智谱链接在用），这里直接用 */
            Text_(
                text = "github.com/Michae1o/xiaoliuren-suiwen  ↗",
                modifier = Modifier.tapNoIndication {
                    Platform.openUrl?.invoke("https://github.com/Michae1o/xiaoliuren-suiwen")
                }.padding(vertical = 4.dp),
                fontSize = 12f,
                lineHeight = 21f,
                color = KY_ACCENT,
            )
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/* ── 开关（玻璃材质 + 跟手 + 非线性）─────────────────────────────────────────
   · 材质：与卡片/按钮同一套 glassMaterial（平涂面 + 上缘一道亮边 + 淡描边）——
     ⛔ 不用阴影、⛔ 不用模糊、⛔ 不加径向渐变（Kuikly 这几样都不落地）。
     轨道在玻璃面上再压一层半透明主色（0.58×p），底面那层玻璃仍透得出来。
   · 跟手：横拖时旋钮**拖多少给多少**（按手指位移 / 可走距离算进度，不吸附）；
     松手按当前位置过没过一半决定开/关，200ms 走 KY_EASE_OUT（cubic-bezier(.22,1,.36,1)）收尾。
   · 按下/拖动中旋钮放大 6%（120ms 非线性），松手回原尺寸。
   · 整行仍可点（父级 toggleable 管点击），拖动由这一层吃掉。 */
@Composable
internal fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val trackW = 44.dp
    val trackH = 26.dp
    val padD = 3.dp
    val knobD = 20.dp
    /* 旋钮能走的距离：44 - 20 - 3×2 = 18dp */
    val travel = trackW - knobD - padD * 2
    val shape = RoundedCornerShape(999.dp)
    val p = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val press by animateFloatAsState(if (dragging) 1f else 0f, tween(120, easing = KY_EASE_OUT))

    /* 只有「外部改值」（回读配置 / 别处写）才复位；拖动中不打断手 */
    LaunchedEffect(checked) {
        val t = if (checked) 1f else 0f
        if (!dragging && p.targetValue != t) p.animateTo(t, tween(200, easing = KY_EASE_OUT))
    }

    // pointerInput(Unit) lives across recompositions. Its gesture callbacks must
    // read the latest model/callback, not the checked value at first composition.
    val latestChecked by rememberUpdatedState(checked)
    val latestOnChange by rememberUpdatedState(onChange)

    Box(
        modifier = Modifier
            .size(trackW, trackH)
            .clip(shape)
            .glassMaterial(shape, control = true, pressed = if (dragging) 1f else 0f)
            .background(KY_ACCENT.copy(alpha = 0.58f * p.value))
            .pointerInput(Unit) {
                val travelPx = travel.toPx().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val on = p.value >= 0.5f
                        if (on != latestChecked) latestOnChange(on)
                        else scope.launch { p.animateTo(if (on) 1f else 0f, tween(200, easing = KY_EASE_OUT)) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { p.animateTo(if (latestChecked) 1f else 0f, tween(200, easing = KY_EASE_OUT)) }
                    },
                    onHorizontalDrag = { _, amount ->
                        val next = (p.value + amount / travelPx).coerceIn(0f, 1f)
                        scope.launch { p.snapTo(next) }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = padD + travel * p.value)
                .size(knobD)
                .graphicsLayer {
                    scaleX = 1f + 0.06f * press
                    scaleY = 1f + 0.06f * press
                }
                .clip(shape)
                /* 旋钮不做纯白死面：深色皮肤上用近白，浅色皮肤上留一圈极淡墨边做「厚度」 */
                .background(if (Skin.dark) Color(0.93f, 0.93f, 0.94f) else Color.White)
                .border(
                    1.dp,
                    if (Skin.dark) Color.White.copy(alpha = 0.22f) else Skin.def.ink.copy(alpha = 0.12f),
                    shape,
                )
        )
    }
}

/** Three discrete stops; direct manipulation while dragging, eased settling on release. */
@Composable
private fun FogLevelControl() {
    val level = Skin.fogLevel
    val position = remember { Animatable(level.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var request by remember { mutableStateOf(0) }
    val press by animateFloatAsState(if (dragging) 1f else 0f, tween(if (dragging) 100 else 420, easing = KY_EASE_OUT))
    var widthPx by remember { mutableStateOf(0f) }
    val latestLevel by rememberUpdatedState(level)
    val easing = com.tencent.kuikly.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    LaunchedEffect(level, request, dragging) {
        if (!dragging) {
            // Seed the settle from the last actual finger position, even if drag end was in the same frame.
            val from = dragPosition
            if (from != null) {
                position.snapTo(from)
                if (dragging || dragPosition != from) return@LaunchedEffect
                dragPosition = null
            }
            position.animateTo(level.toFloat(), tween(360, easing = easing))
        }
    }
    fun settle(target: Int) {
        Skin.updateFogLevel(target)
        request++ // A canceled drag or retap may target the already selected stop.
    }

    val visiblePosition = dragPosition ?: position.value
    val trackFill = Skin.def.surface.copy(alpha = if (Skin.dark) .52f else .42f)
    val trackLine = Skin.def.ink.copy(alpha = if (Skin.dark) .15f else .11f)
    val thumbFill = KY_ACCENT.copy(alpha = if (Skin.dark) .25f + press * .05f else .13f + press * .05f)
    val thumbLine = KY_ACCENT.copy(alpha = if (Skin.dark) .40f else .25f)
    val activeInk = if (Skin.dark) KY_INK else KY_ACCENT
    Box(Modifier.fillMaxWidth().height(42.dp)
        .onSizeChanged { widthPx = it.width.toFloat() }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    dragPosition = dragPosition ?: position.value
                    dragging = true
                },
                onHorizontalDrag = { _, amount ->
                    if (widthPx > 0f) {
                        dragPosition = ((dragPosition ?: position.value) + amount / (widthPx / 3f)).coerceIn(0f, 2f)
                    }
                },
                onDragEnd = {
                    dragging = false
                    settle(kotlin.math.round(dragPosition ?: position.value).toInt().coerceIn(0, 2))
                },
                onDragCancel = {
                    dragging = false
                    settle(latestLevel)
                },
            )
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val keyline = .85.dp.toPx()
            fun pill(x: Float, y: Float, w: Float, h: Float, fill: Color, line: Color) {
                if (w <= 0f || h <= 0f) return
                val radius = minOf(w, h) * .5f
                drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(radius))
                val stroke = minOf(keyline, radius * .5f)
                val half = stroke * .5f
                drawRoundRect(
                    line, Offset(x + half, y + half), Size(w - stroke, h - stroke),
                    CornerRadius(radius - half), style = Stroke(stroke),
                )
            }
            pill(0f, 0f, size.width, size.height, trackFill, trackLine)
            val cell = size.width / 3f
            val inset = minOf(3.5.dp.toPx(), cell * .25f, size.height * .25f)
            val thumbWidth = (cell - 2f * inset) * (1f - .025f * press)
            val thumbHeight = (size.height - 2f * inset) * (1f - .06f * press)
            val center = cell * (visiblePosition.coerceIn(0f, 2f) + .5f)
            val left = (center - thumbWidth * .5f).coerceIn(inset, size.width - inset - thumbWidth)
            pill(left, (size.height - thumbHeight) * .5f, thumbWidth, thumbHeight, thumbFill, thumbLine)
        }
        Row(Modifier.fillMaxSize()) {
            listOf("静态雾景", "轻柔流动", "实时流雾").forEachIndexed { index, title ->
                Box(Modifier.weight(1f).height(42.dp)
                    .clickable { settle(index) }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text_(title, fontSize = 12f, lineHeight = 20f,
                            weight = if (level == index) 600 else 400,
                            color = if (level == index) activeInk else KY_DIM2)
                    }
                }
            }
        }
    }
}


/** Freeze is a separate, explained control for the currently selected moving tier. */
@Composable
private fun FogFreezeControl() {
    val level = Skin.fogLevel
    val frozen = Skin.isFogFrozen(level)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .toggleable(value = frozen, role = Role.Switch, onValueChange = { enabled ->
                if (enabled != Skin.isFogFrozen(Skin.fogLevel)) Skin.toggleFogFrozen(Skin.fogLevel)
            })
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text_("冻结雾气", fontSize = 13f, lineHeight = 21f, weight = 500)
                Spacer(Modifier.width(8.dp))
                Text_(if (frozen) "已冻结" else "流动中", fontSize = 10f, lineHeight = 18f,
                    weight = if (frozen) 600 else 400,
                    color = if (frozen) KY_ACCENT else KY_DIM2)
            }
            Text_(
                if (frozen) "已停在当前画面，关闭后继续流动。"
                else if (level == 2 && realtimeFogAvailable()) "开启后定格当前雾景，停止持续生成。"
                else "开启后停在此刻，减少持续动画开销。",
                fontSize = 11f, lineHeight = 18f, color = KY_DIM2,
            )
        }
        GlassSwitch(checked = frozen, onChange = { enabled ->
            if (enabled != Skin.isFogFrozen(Skin.fogLevel)) Skin.toggleFogFrozen(Skin.fogLevel)
        })
    }
}
