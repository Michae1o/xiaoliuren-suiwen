package com.xlr.k.pages

import com.xlr.k.judge.Rec

/* 记录页的「复盘 / 准则 / 接着问」——对应早期版本
   apps/mobile-web/src/review/reviewCore.js（reviewCase / ruleBlock / reviewBlock / confirmRule）
   与 components/Records.jsx 的 Detail（caseDetailHtml）。
   ⚠ 语义逐条照旧：① 没接模型就不复盘，只提示，不编；② 复盘要出网，由模型指出错在哪一步；
   ③ 复盘结果只落在这一条记录上（rv/rvWhy/rvRule），**用户点「写入准则」才进准则表**。
*/

/* ---------- 给模型的上下文 ---------- */

/** 旧 ruleBlock()：本门已确认的准则，逐条带上 */
internal fun ruleBlock(): String {
    val rs = RecStore.loadRules()
    if (rs.isEmpty()) return ""
    val l = mutableListOf(
        "【本门已确认的准则 · 必须遵守】",
        "（这些是卦主本人逐条确认过的修正，来自实断复盘。遇到同类情形按它断，与判据冲突时以它为准。）",
    )
    rs.forEachIndexed { i, r ->
        l.add("· 第 " + (i + 1) + " 条（" + (r["at"] ?: "") + (if ((r["gua"] ?: "").isNotEmpty()) "，" + r["gua"] else "") + "）：" + stripTags(r["rule"] ?: ""))
        val why = stripTags(r["why"] ?: "")
        if (why.isNotEmpty()) l.add("    依据：" + why)
    }
    l.add("")
    return l.joinToString("\n")
}

/** 旧 reviewBlock()：最近 6 条带过标记的，只作校准 */
internal fun reviewBlock(): String {
    val a = RecStore.load().filter { (it["fb"] ?: "").isNotEmpty() }
    if (a.isEmpty()) return ""
    val l = mutableListOf("【过往复盘 · 只作校准，不要拿它当规则往新卦上套】")
    a.takeLast(6).forEach { r ->
        l.add(
            "· " + (r["t"] ?: "") + "　" + (r["gua"] ?: "") + "　问「" + (r["q"] ?: "") + "」　→ " +
                (if (r["fb"] == "错") "我上次断错了" else "上次断对了") +
                (if ((r["note"] ?: "").isNotEmpty()) "；实际情况：" + r["note"] else "")
        )
    }
    l.add("复盘里凡是我上次断错的地方，这次要留意别再犯；但每卦仍以本卦自己的判据为准。")
    l.add("")
    return l.joinToString("\n")
}

private fun stripTags(s: String): String = s.replace(Regex("<[^>]+>"), "")

/** 从记录把那一卦的 Context 复原出来 —— 复盘/追问都要跟解卦时用**同一份判据**。
 *  记录里只存了「天宫 · 地宫 · 人宫　午时」+ zi + src + catKey，够重建了。 */
internal fun ctxOf(rec: Rec): com.xlr.k.judge.Context? {
    val gua = rec["gua"] ?: return null
    val seg = gua.split("　")
    val g = seg[0].split("·").map { it.trim() }.filter { it.isNotEmpty() }
    if (g.size < 3) return null
    val zi = rec["zi"]?.toIntOrNull() ?: return null
    val cast = com.xlr.k.judge.Cast(
        steps = emptyList(),
        zi = zi,
        gi = emptyList(),
        g = g.take(3),
        ziName = if (seg.size > 1) seg[1].trim() else "",
        mode = "clock",
        src = rec["src"] ?: "",
    )
    return com.xlr.k.judge.buildContext(
        rec["q"] ?: "", rec["bg"] ?: "", rec["catKey"] ?: "general", cast,
    )
}

/** 那一卦的判据整段（跟主断送给模型的是同一份：buildBlocks + plainAll） */
internal fun judgeTextOf(rec: Rec): String {
    val ctx = ctxOf(rec) ?: return ""
    return com.xlr.k.judge.plainAll(ctx, com.xlr.k.judge.buildBlocks(ctx))
}

/** 旧 reviewCase() 里拼的那段 usr，逐字照抄 */
internal fun rvUser(rec: Rec): String {
    val q = rec["q"] ?: ""
    val bg = rec["bg"] ?: ""
    val gua = rec["gua"] ?: ""
    val cat = rec["cat"] ?: ""
    val duanci = rec["duanci"] ?: ""
    val note = rec["note"] ?: ""
    val sb = StringBuilder()
    sb.append("请在这一卦的体系内复盘：\n\n")
    /* 所问/背景/卦/事类 已在判据整段开头（plainAll 自带），不重复 */
    /* ★ 判据整段带上——
       以前只给三宫名和断辞，模型手里没有本门材料，就会从外面搬「旺相休囚死」那一套来凑。 */
    val jt = judgeTextOf(rec)
    if (jt.isNotEmpty()) sb.append("【这一卦的判据 · 与解卦时同一份】\n").append(jt).append("\n")
    if (duanci.isNotEmpty()) sb.append("【我当时给的断辞】\n").append(duanci).append("\n")
    else sb.append("【我当时给的断辞】（未记）\n")
    sb.append("【实际结果·卦主确认】").append(if (note.isNotEmpty()) note else "（卦主未写实际情况）").append("\n\n")
    sb.append(reviewBlock())
    sb.append(ruleBlock())
    sb.append("请按格式复盘。")
    return sb.toString()
}

/* ---------- 复盘主体 ---------- */

/** 复盘结果：展示正文 / 依据（进准则用）/ 那一行 RULE（进准则用） */
internal class RvOut(val show: String, val why: String, val rule: String)


private fun sysBase(): String =
    com.xlr.k.judge.SYS

/** 跑一次复盘（流式）。返回解析好的三段；调用方负责 RecStore.setRv */
internal suspend fun reviewCase(rec: Rec, onText: (String) -> Unit): RvOut {
    val cfg = LlmStore.load()
    val acc = askModelStream(cfg, sysBase() + RV_SYS + RV_GUARD, rvUser(rec)) { t -> onText(rvFmtPlain(t)) }
    var rule = ""
    Regex("RULE[:：]\\s*(.+)").find(acc)?.let { rule = it.groupValues[1].trim() }
    var why = ""
    Regex("【依据】([\\s\\S]*?)(?=\\n【|$)").find(acc)?.let { why = it.groupValues[1].trim() }
    val show = rvFmtPlain(acc).replace(Regex("\\s*RULE[:：][\\s\\S]*$"), "")
    return RvOut(show, why, rule)
}

/** 旧 rvFmt() 的纯文本版：空行留空、【小节】单独起一行 */
internal fun rvFmtPlain(t: String): String {
    val sb = StringBuilder()
    t.split("\n").forEachIndexed { i, ln ->
        val x = ln.trim()
        if (i > 0) sb.append("\n")
        if (x.isEmpty()) return@forEachIndexed
        val m = Regex("^【(.+?)】\\s*(.*)$").find(x)
        if (m != null) {
            sb.append("【").append(m.groupValues[1]).append("】")
            val rest = m.groupValues[2]
            if (rest.isNotEmpty()) sb.append("\n").append(rest)
        } else sb.append(x)
    }
    return sb.toString()
}

/* ---------- 接着问（还是这一卦） ---------- */

internal fun followUpUser(rec: Rec, q: String): String {
    val sb = StringBuilder()
    sb.append("请接着这一卦继续答（接前卦：同一卦内追问，不另起卦）。\n\n")
    /* 判据放最前（规格 v2 §5）：先给材料，再给上下文 */
    val jtUp = judgeTextOf(rec)
    if (jtUp.isNotEmpty()) sb.append("【这一卦的判据 · 与解卦时同一份】\n").append(jtUp).append("\n\n")
    /* 所问/背景/卦/事类 已在判据整段开头（plainAll 自带），不重复 */
    val d = rec["duanci"] ?: ""
    if (d.isNotEmpty()) sb.append("【当时的断辞】\n").append(d).append("\n")
    val th = threadOf(rec)
    sb.append("\n【这一卦已经答过的】\n")
    if (th.isEmpty()) sb.append("（这是第一次追问）\n")
    th.forEachIndexed { i, it ->
        sb.append("问").append(i + 1).append("：").append(it.first).append("\n")
        sb.append("答").append(i + 1).append("：").append(it.second).append("\n")
    }
    sb.append("\n").append(ruleBlock())
    sb.append("【这次要问的】\n").append(q).append("\n\n")
    sb.append("【续问规矩】\n")
    sb.append("① 只用这一卦：不另起、不重起，答案要从这一卦里得出；\n")
    sb.append("② 落宫为主：先看这一问在这卦里对应到哪座宫、什么关系，再答（问什么，是什么）；\n")
    sb.append("③ 每处判断括注依据（哪宫／哪关系／哪条判据）；\n")
    sb.append("④ 这一问若不在这一卦里（问的是旁人别的事、或无端之问），直接说「这不在这一卦里」并讲清原因，不要硬答；\n")
    sb.append("⑤ 不要主动再问、不要邀着继续问、不展开没问到的东西；\n")
    sb.append("⑥ 格式同主断辞（【结论】【断辞】…），宁短勿长、够用即止。\n")
    return sb.toString()
}

/** thread 存成「问\u0001答\u0001问\u0001答」这种一维串（记录是扁平字符串表） */
internal fun threadOf(rec: Rec): List<Pair<String, String>> {
    val raw = rec["thread"] ?: ""
    if (raw.isEmpty()) return emptyList()
    val parts = raw.split("\u0001")
    val out = mutableListOf<Pair<String, String>>()
    var i = 0
    while (i + 1 < parts.size) {
        out.add(parts[i] to parts[i + 1])
        i += 2
    }
    return out
}

internal fun appendThread(id: String, q: String, a: String) {
    val all = RecStore.loadRaw()
    for (r in all) if (r["id"] == id) {
        val old = r["thread"] ?: ""
        r["thread"] = if (old.isEmpty()) q + "\u0001" + a else old + "\u0001" + q + "\u0001" + a
    }
    RecStore.save(all.toList())
}

/** 复制断辞用：把断辞拍成纯文字（旧 copyDuanci 的口径：去标记、留段落） */
internal fun plainDuanci(t: String): String {
    var s = t.replace("**", "").replace("__", "")
    s = s.replace(Regex("<br\\s*/?>"), "\n")
    s = s.replace(Regex("<[^>]+>"), "")
    s = s.replace(Regex("[ \\t]+"), " ")
    s = s.split("\n").joinToString("\n") { it.trim() }
    return s.trim()
}
