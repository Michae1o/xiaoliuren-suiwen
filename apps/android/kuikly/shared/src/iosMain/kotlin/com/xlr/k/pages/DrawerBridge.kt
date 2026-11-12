package com.xlr.k.pages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 记录抽屉（iOS 原生版）的数据桥。
 *
 * 分工：
 *   · Swift 负责抽屉的一切：滑出/收回手势、搜索框、页签、按天分组、行、左滑删除、长按菜单、详情；
 *   · Kotlin 只当数据源 + 干重活：把记录/准则打成一份 JSON 推过去；
 *     复盘（reviewCase）和追问（askModelStream）是挂起函数 + 流式回调，仍留在 Kotlin 里跑，
 *     跑完再推一次数据。
 *
 * ⚠ 只有 Platform.nativeDrawer 为 true（iOS 且桥装好）时 Kuikly 才不画自己的抽屉；
 *   否则照旧，天然回退。
 */
internal object DrawerFeed {
    /** Kotlin → Swift：整份抽屉数据（JSON 字符串） */
    var push: ((String) -> Unit)? = null
    /** Swift → Kuikly 的其它动作（打开设置页等） */
    var action: ((String) -> Unit)? = null
}

/** 复盘/追问的工作线程（都是 IO 等待，不占主线程） */
private val drawerScope = CoroutineScope(Dispatchers.Main)

/** 复盘失败的原因（按记录 id），下次成功就清掉 */
private val reviewErrs = HashMap<String, String>()

/** 把当前记录/准则打成 JSON。字段跟 Kuikly 那张抽屉一一对应。 */
internal fun drawerJson(): String {
    val sb = StringBuilder(4096)
    val recs = RecStore.load().reversed()
    val rules = RecStore.loadRules()
    sb.append("{\"noModel\":\"").append(if (LlmStore.missing(LlmStore.load()).isEmpty()) "" else "1")
    sb.append("\",\"recs\":[")
    recs.forEachIndexed { i, r ->
        if (i > 0) sb.append(',')
        sb.append('{')
        var first = true
        fun f(k: String, v: String) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(k).append("\":\"").append(esc(v)).append('"')
        }
        f("id", r["id"] ?: "")
        f("q", r["q"] ?: "")
        f("bg", r["bg"] ?: "")
        f("gua", r["gua"] ?: "")
        f("cat", r["cat"] ?: "")
        f("t", r["t"] ?: "")
        f("duanci", r["duanci"] ?: "")
        f("fb", r["fb"] ?: "")
        f("note", r["note"] ?: "")
        f("ans", r["ans"] ?: "")
        f("rv", r["rv"] ?: "")
        f("rvErr", reviewErrs[r["id"] ?: ""] ?: "")
        /* 追问：[[问,答],…] */
        sb.append(",\"thread\":[")
        threadOf(r).forEachIndexed { j, p ->
            if (j > 0) sb.append(',')
            sb.append("[\"").append(esc(p.first)).append("\",\"")
                .append(esc(p.second)).append("\"]")
        }
        sb.append(']')
        sb.append('}')
    }
    sb.append("],\"rules\":[")
    rules.forEachIndexed { i, r ->
        if (i > 0) sb.append(',')
        sb.append("{\"id\":\"").append(esc(r["id"] ?: "")).append("\",")
        sb.append("\"at\":\"").append(esc(r["at"] ?: "")).append("\",")
        sb.append("\"gua\":\"").append(esc(r["gua"] ?: "")).append("\",")
        /* 来源那一卦的所问与 id：准则详情里要能说清「这条是从哪一卦、问什么事来的」 */
        sb.append("\"q\":\"").append(esc(r["q"] ?: "")).append("\",")
        sb.append("\"from\":\"").append(esc(r["from"] ?: "")).append("\",")
        sb.append("\"rule\":\"").append(esc(r["rule"] ?: "")).append("\",")
        sb.append("\"why\":\"").append(esc(r["why"] ?: "")).append("\"}")
    }
    /* 回收站：删掉的记录（deleted 非空）。Swift 底部那条「回收站 · N 则」+ 展开列表就吃这份。 */
    sb.append("],\"dead\":[")
    RecStore.loadDeleted().reversed().forEachIndexed { i, r ->
        if (i > 0) sb.append(',')
        sb.append("{\"id\":\"").append(esc(r["id"] ?: "")).append("\",")
        sb.append("\"q\":\"").append(esc(r["q"] ?: "")).append("\",")
        sb.append("\"t\":\"").append(esc(r["t"] ?: "")).append("\"}")
    }
    sb.append("]}")
    return sb.toString()
}

private fun pushData() {
    DrawerFeed.push?.invoke(drawerJson())
}

/** 让 Swift 那边重拿一份（解卦写完断辞之后要它立刻刷新） */
internal fun drawerPush() {
    pushData()
}

private fun findRec(id: String): MutableMap<String, String>? =
    RecStore.load().firstOrNull { it["id"] == id }?.toMutableMap()

/** Swift 发回来的动作。 */
internal fun applyDrawerAct(k: String, v: String) {
    when (k) {
        "del" -> RecStore.del(v)
        /* 回收站：单条还原 / 彻底删（老接口留着），以及整页那颗「一键还原 / 清理」 */
        "restore" -> RecStore.restore(v)
        "purge" -> RecStore.purge(v)
        "restoreAll" -> RecStore.loadDeleted().forEach { RecStore.restore(it["id"] ?: "") }
        "purgeAll" -> RecStore.loadDeleted().forEach { RecStore.purge(it["id"] ?: "") }
        "ruleDel" -> RecStore.delRule(v)
        "fb" -> {
            val i = v.indexOf('|')
            if (i > 0) RecStore.setFb(v.substring(0, i), v.substring(i + 1))
        }
        "note" -> {
            val i = v.indexOf('|')
            if (i > 0) RecStore.setNote(v.substring(0, i), v.substring(i + 1))
        }
        "settings" -> {
            Session.settingsOpen = true
            Session.settingsPage = ""
        }
        /* 原生抽屉自己的开合：只影响「卡片要不要让开」，不用回推数据 */
        "open" -> {
            Session.recordsOpen = true
            Session.recProg = 1f
        }
        "close" -> {
            Session.recordsOpen = false
            Session.recProg = 0f
        }
        /* 复盘：跑一次 reviewCase，结果写进记录（rv/rvWhy/rvRule） */
        "review" -> drawerScope.launch {
            val rec = findRec(v)
            if (rec != null) {
                try {
                    val out = reviewCase(rec) { t -> Platform.drawerDelta?.invoke(t) }
                    Platform.drawerDelta?.invoke("")
                    RecStore.setRv(v, out.show, out.why, out.rule)
                    reviewErrs.remove(v)
                } catch (e: Throwable) {
                    Platform.drawerDelta?.invoke("")
                    reviewErrs[v] = "复盘失败：" + (e.message ?: "")
                }
            }
            pushData()
        }
        /* 写入准则：把 rvRule/rvWhy 记成一条准则，然后让 Swift 切到准则页 */
        "ruleAdd" -> {
            val rec = findRec(v)
            if (rec != null) {
                RecStore.addRule(rec, rec["rvRule"] ?: "", rec["rvWhy"] ?: "")
                RecStore.setRv(v, "", "", "")
            }
            pushData()
        }
        /* 算了，不记 */
        "ruleDrop" -> {
            RecStore.setRv(v, "", "", "")
            pushData()
        }
        /* 接着问：id|问题 */
        "follow" -> drawerScope.launch {
            val i = v.indexOf('|')
            if (i > 0) {
                val id = v.substring(0, i)
                val qq = v.substring(i + 1)
                val rec = findRec(id)
                if (rec != null && qq.isNotBlank()) {
                    try {
                        val text = askModelStream(
                            LlmStore.load(),
                            com.xlr.k.judge.SYS,
                            followUpUser(rec, qq),
                        ) { t -> Platform.drawerDelta?.invoke(t) }
                        /* ⚠ 顺序要紧：先把答案落库、把新数据推过去，最后才清流式文本。
                           反过来（先清空再落库）中间会有一帧「啥都没有」，
                           用户看到的就是「答案刚出来又消失了」（报的）。 */
                        appendThread(id, qq, text)
                        pushData()
                        Platform.drawerDelta?.invoke("")
                    } catch (e: Throwable) {
                        appendThread(id, qq, "问失败：" + (e.message ?: ""))
                        pushData()
                        Platform.drawerDelta?.invoke("")
                    }
                }
            }
            pushData()
        }
    }
}

private fun esc(s: String): String {
    /* ⚠ 不再走「没引号/反斜杠/换行就原样返回」那条捷径：模型答里偶发制表符、
       垂直制表符这类控制字符，原样塞进 JSON 会让整份数据解析失败
       （Swift 那边 load() 直接 return ⇒ 界面停在上一次的数据，看着就是「答案消失了」）。 */
    val b = StringBuilder(s.length + 8)
    s.forEach { c ->
        when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\r' -> {}
            c == '\t' -> b.append("\\t")
            c < ' ' -> b.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> b.append(c)
        }
    }
    return b.toString()
}
