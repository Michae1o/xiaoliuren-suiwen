package com.xlr.k.judge

import kotlinx.datetime.LocalDateTime

/* 本机速断（quickReadCore.js 的 Kotlin 版，逐支对照搬家）。
   口径：先给答案，再一句理由；不铺依据、不列类象、不碰体用。
   ⚠ 措辞与分支顺序必须跟旧 JS 一致，否则同一卦会说出不一样的话。 */

private fun speak(s: String): String =
    s.replace("——", "，").replace("，，", "，").replace("「", "").replace("」", "")

/** 落宫性质白话：取 xing 的前两段（先切掉 —— 之后的解释） */
private fun tag(g: String): String {
    val x = (G[g]?.xing ?: "").replace(Regex("——.*$"), "")
    val ps = x.split(Regex("[、，]")).filter { it.isNotBlank() }
    return if (ps.size >= 2) ps.take(2).joinToString("、") else x
}

private fun pairRel(a: String, b: String): String {
    val wa = G[a]!!.wx
    val wb = G[b]!!.wx
    if (wa == wb) return "比和"
    if (SHENG[wa] == wb) return "生"
    if (KE[wa] == wb) return "克"
    if (SHENG[wb] == wa) return "被生"
    return "受制"
}

private val T2D = mapOf(
    "生" to "接得上，一路往下",
    "克" to "这里别住了",
    "被生" to "中间回头照应起头",
    "受制" to "中间压着起头",
    "比和" to "两段同气",
)

private val D2R = mapOf(
    "生" to "结果是顺着中间这一步过来的",
    "克" to "中间压着结果，这一步是硬拿",
    "被生" to "结果反过来照应中间，越往后越费劲",
    "受制" to "结果压住中间，前面铺的路到后面用不上",
    "比和" to "中间和结果同气，顺",
)

/* 应期：季节 → 月份区间 */
private fun seasonOf(g: String): Pair<IntArray, String> {
    val ji = G[g]?.ji ?: ""
    val table = listOf(
        Triple("长夏", intArrayOf(5, 7), "长夏"),
        Triple("冬春", intArrayOf(2, 4), "冬春之交"),
        Triple("辰巳|四季土", intArrayOf(4, 6), "春末夏初"),
        Triple("春", intArrayOf(2, 4), "春天"),
        Triple("秋", intArrayOf(8, 10), "秋天"),
        Triple("冬", intArrayOf(11, 1), "冬天"),
    )
    for ((re, span, name) in table) if (Regex(re).containsMatchIn(ji)) return span to name
    return intArrayOf(3, 5) to "这一季"
}

private fun whenPoint(g: String, now: LocalDateTime): String {
    val (span, name) = seasonOf(g)
    val near = (G[g]?.jx ?: "").endsWith("吉")
    var off = if (near) 0 else 1
    val m = now.monthNumber
    val crossed = if (span[0] <= span[1]) m > span[1] else (m > span[1] && m < span[0])
    if (crossed) off += 1
    val whenTxt = if (off == 0) "今年" else if (off == 1) "明年" else "后年"
    return whenTxt + name + "，" + span[0] + " 到 " + span[1] + " 月"
}

private fun durationOf(g: String): String {
    val san = G[g]?.san ?: ""
    val n = san.split(Regex("[·\\s]+")).firstOrNull()?.toIntOrNull() ?: 3
    val a = G[g]?.act ?: ""
    val unit = when (a) {
        "快" -> listOf("天", "周")
        "动" -> listOf("周", "个月")
        else -> listOf("个月", "季")
    }
    return "$n ${unit[0]}到 $n ${unit[1]}"
}

/* 是非问句的判定词：跟问法走 */
private val VD = listOf(
    "会不会" to "会", "能不能" to "能", "可不可以" to "可以", "可否" to "可以",
    "有没有" to "有", "行不行" to "行", "该不该" to "该", "是不是" to "是", "是否" to "是",
    "顺利" to "顺", "成功" to "成", "成不成" to "成",
)

/* ---------- 事类落宫断法压白话（取 c.cat.luo[c.g3]，≤20字） ---------- */
private fun luoVerdict(ctx: Context, ans: String, negQ: Boolean, good: Boolean): String? {
    val catKey = ctx.catKey
    val g1 = ctx.g1
    val g2 = ctx.g2
    val g3 = ctx.g3
    val q = ctx.q

    /* 疾病类：由三宫格局统一接管，不凭落宫单论 */
    if (catKey == "ill") {
        val hasKw = (g1 == "空亡" || g2 == "空亡")
        val hasCk = (g1 == "赤口" || g2 == "赤口")
        val isMove = (g3 == "速喜" || g3 == "小吉")
        val isAskOp = Regex("手术|开刀|切除").containsMatchIn(q)
        val isAskFine = Regex("好吗|能好|能康复|能痊愈").containsMatchIn(q)

        if (isAskOp) {
            return if (hasCk || g3 == "赤口") {
                if (isMove) "宜手术，见急病或手术之象且有转机" else "宜手术，见急病或手术之象须主动"
            } else {
                if (isMove) "可暂缓手术，有转机宜尽快就医" else "防有变数，须主动就医遵医嘱"
            }
        }

        if (hasKw) {
            /* 天地两宫出现空亡：一律不得输出「不严重」「没事」，必须带「有危险/凶」字样 */
            return if (isAskFine) {
                if (isMove) "有转机但防凶险，空亡断病有两路" else "有危险防两路落空，务必主动就医"
            } else {
                if (isMove) "有危险但有转机，必须尽快送医" else "有危险且易耽搁，务必主动就医不要等"
            }
        }

        if (hasCk) {
            /* 天地两宫出现赤口：带「急病或手术之象」 */
            return if (isMove) "急病或手术之象，有转机必须尽快就医" else "急病或手术之象，人要动才吉不要等"
        }

        if (g3 == "大安" || g1 == "大安" || g2 == "大安") {
            /* 大安：不要因为大安是吉就放心 */
            return if (isMove) "不可因吉就放心，有转机必须尽快就医" else "不可因吉就放心，人要动才吉不要等"
        }

        if (isAskFine) {
            return if (isMove) "有转机，必须尽快就医调治" else "人要动才吉，务必主动就医不要等"
        }

        return if (isMove) "有转机，必须尽快就医" else "人要动才吉，务必主动就医不要等"
    }

    return when (catKey) {
        "marriage" -> when (g3) {
            "大安" -> if (q.contains("复合")) "身未动，女问好，男问一般、缺新鲜感" else "身未动，明明是单身"
            "速喜" -> "主快、为吉，初交为热恋、来得快"
            "赤口" -> "争斗，感情纷争多，或女方身体不好"
            "小吉" -> "主动，主付出多回报少，本来没有感情的"
            "空亡" -> "落空，争执多，且有他人介入"
            else -> null
        }
        "work" -> when (g3) {
            "大安" -> "身未动＝工作不换"
            "留连" -> "拖着、定不下来"
            "速喜" -> "来得快，面试、offer出得快"
            "赤口" -> "口舌是非、竞争激烈、易生纠纷"
            "小吉" -> "动，会动、会换、会走"
            "空亡" -> "落空，这事成不了，或去了也没有"
            else -> null
        }
        "money" -> when (g3) {
            "大安" -> "主静，身未动，大安虽吉但身未动＝财不动"
            "留连" -> "拖、缠，款项迟迟不到位，来回拉扯"
            "速喜" -> "来得快，火克金为收入"
            "赤口" -> "还款能力差、口舌是非"
            "小吉" -> "会花钱、也主进项流动"
            "空亡" -> "起卦见空亡＝没积蓄"
            else -> null
        }
        "law" -> when (g3) {
            "大安" -> "吉但身未动，局面不动、事情推不动"
            "留连" -> "拖，官司缠身、久拖不决"
            "速喜" -> "快，事情出结果快"
            "赤口" -> "断凶之宫：口舌、是非、伤害"
            "小吉" -> if (q.contains("取保")) "能取保，有转机且有人帮忙走动" else "动，有转机、有人帮忙走动"
            "空亡" -> "落空、不成，案子可能不了了之，或诉求落空"
            else -> null
        }
        "exam" -> when (g3) {
            "大安" -> "吉、稳，按部就班准备的人占优"
            "留连" -> "拖、暧昧，分数悬、结果拖"
            "速喜" -> "快、亮，当场发挥型"
            "赤口" -> "凶，口舌、失误、判罚争议"
            "小吉" -> "动、有水，临场灵活、超常发挥"
            "空亡" -> "落空，不成、没结果"
            else -> null
        }
        "travel" -> when (g3) {
            "大安" -> "身未动，还没走"
            "留连" -> "拖延，动身拖着、路上耽误"
            "速喜" -> "马上到，说到就到"
            "赤口" -> "不想来了、或来不成了"
            "小吉" -> "在路上，已经在动了"
            "空亡" -> "不会来了、来不成了"
            else -> null
        }
        "preg" -> when (g3) {
            "大安" -> "大安、赤口、空亡三宫落宫＝无孕"
            "留连" -> "留连、速喜、小吉三宫落宫＝有孕"
            "速喜" -> "落宫速喜＝有孕"
            "赤口" -> "落宫赤口＝无孕"
            "小吉" -> "落宫小吉＝有孕"
            "空亡" -> "落宫空亡＝未怀上"
            else -> null
        }
        "lost" -> when (g3) {
            "大安" -> "身未动，东西没走远，还在原处附近，不动"
            "留连" -> "停滞，东西还在原处，没被移动"
            "速喜" -> "传得快，要尽快"
            "赤口" -> "凶，多半找不回，或已经被损毁、被人拿走"
            "小吉" -> "动，东西被移动了、或有人在帮忙"
            "空亡" -> "落空，找不回来了、事不成"
            else -> null
        }
        else -> null
    }
}

/* ---------- 文本压缩辅助（照 quickReadCore.js 109-170 逐行搬） ---------- */

private fun cleanExtraText(t: String?): String {
    if (t.isNullOrEmpty()) return ""
    return t
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("（\\s*\\d+[^）]*）"), "")
        .replace(Regex("\\(\\s*\\d+[^)]*\\)"), "")
        .replace(Regex("[「」『』]"), "")
        .replace(Regex("[\u2014\u2013\u2014\u2014]+"), "，")
        .replace(Regex("体用决定要不要主动找[：:]?"), "")
        .replace(Regex("以天为体看天气、以人为体看吉凶[。，]?"), "")
        .replace(Regex("体用|用生体|体生用|用克体|体克用|比助|比劫|比和"), "")
        .replace(Regex("依据|判据|卦例|原文说"), "")
        .replace(Regex("[0-9]{3}\\s*(行[0-9]+)?"), "")
        .replace("天克人／人克天", "生克")
        .replace(Regex("\\s+"), "")
        /* 删词后可能只剩开头标点（如「用生体——…」删掉「用生体」余「，…」），去掉才成句 */
        .replace(Regex("^[，,、；;：:]+"), "")
}

private fun compressReason(raw: String?): String {
    val s = cleanExtraText(raw)
    if (s.isEmpty()) return ""
    val sentences = s.split(Regex("[；。]")).filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return ""
    var res = ""
    for (sent in sentences) {
        if (sent.isEmpty()) continue
        val candidate = if (res.isEmpty()) sent else "$res；$sent"
        if ((candidate + "。").length <= 60) res = candidate else break
    }
    if (res.isEmpty()) res = sentences[0].take(58)
    return if (res.isEmpty()) "" else "$res。"
}

private fun compressLuoVerdict(raw: String?): String {
    val stripped = (raw ?: "")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("（[^）]*）"), "")
        .replace(Regex("\\([^)]*\\)"), "")
        .replace(Regex("[「」『』]"), "")
        .replace("——", "，")
        .replace(Regex("\\s+"), "")
    val first = (stripped.split(Regex("[；。]")).firstOrNull() ?: "").trim()
    if (first.isEmpty()) return ""
    if (first.length <= 20) return first
    val parts = first.split(Regex("[，、]"))
    var acc = ""
    for (p in parts) {
        if (p.isEmpty()) continue
        val c = if (acc.isEmpty()) p else "$acc，$p"
        if (c.length <= 20) acc = c else break
    }
    return if (acc.isNotEmpty() && acc.length >= 4) acc else first.take(20)
}

/* 从 extrasOf 里按 lb 取条（对应 JS 的 findItem） */
private fun findItemInExtra(ctx: Context, labels: List<String>): BlockItem? {
    val ex = try {
        extrasOf(ctx.catKey, ctx)
    } catch (e: Throwable) {
        emptyList()
    }
    for (lb in labels) {
        for (it in ex) if (it.lb == lb && it.tx.isNotEmpty()) return it
    }
    return null
}

/* ---------- 理由句优先取自 extrasOf 最相关条（≤60字），不合格才走兜底 ---------- */
private fun pickExtraReason(ctx: Context): String {
    val catKey = ctx.catKey
    val q = ctx.q
    val g3 = ctx.g3

    return when (catKey) {
        "ill" -> {
            val it = findItemInExtra(ctx, listOf("关键"))
            val isMove = (g3 == "速喜" || g3 == "小吉")
            var base = if (it != null) compressReason(it.tx) else ""
            if (base.isEmpty() || base.length > 40) {
                base = if (isMove) "人宫属动宜尽快就医，送医院则吉。" else "人要动才吉，务必主动就医不要等。"
            }
            val combined = "老人、急病不能只凭落宫。" + base
            if (combined.length <= 60) combined else combined.take(59) + "。"
        }
        "lost" -> {
            val isWhere = Regex("哪|何处|什么地方|何方|位置|方位").containsMatchIn(q)
            val it = findItemInExtra(ctx, if (isWhere) listOf("方位", "要不要找") else listOf("要不要找", "方位"))
            val r = if (it != null) compressReason(it.tx) else ""
            if (r.isNotEmpty() && r.length >= 8 && !Regex("，[。；]").containsMatchIn(r)) r
            else fallbackPickExtraReason(ctx)
        }
        "weather" -> {
            val it = findItemInExtra(ctx, listOf("怎么读"))
            val r = if (it != null) compressReason(it.tx) else ""
            if (r.isNotEmpty() && r.length >= 8) r else fallbackPickExtraReason(ctx)
        }
        "law" -> {
            val isBail = Regex("取保|保人|看守|被抓").containsMatchIn(q)
            val it = findItemInExtra(ctx, if (isBail) listOf("取保", "格局") else listOf("格局", "取保"))
            val r = if (it != null) compressReason(it.tx) else ""
            if (r.isNotEmpty() && r.length >= 8) r else fallbackPickExtraReason(ctx)
        }
        "marriage" -> {
            val isChange = Regex("复合|分手").containsMatchIn(q) || g3 == "留连"
            val it = findItemInExtra(ctx, if (isChange) listOf("变数", "喜忌") else listOf("喜忌", "变数"))
            val r = if (it != null) compressReason(it.tx) else ""
            if (r.isNotEmpty() && r.length >= 8) r else fallbackPickExtraReason(ctx)
        }
        "work" -> {
            val it = findItemInExtra(ctx, listOf("行业", "多选项"))
            val r = if (it != null) compressReason(it.tx) else ""
            if (r.isNotEmpty() && r.length >= 8) r else fallbackPickExtraReason(ctx)
        }
        "money" -> {
            val it = findItemInExtra(ctx, listOf("财库", "取角色"))
            val r = if (it != null) compressReason(it.tx) else ""
            if (r.isNotEmpty() && r.length >= 8) r else fallbackPickExtraReason(ctx)
        }
        else -> fallbackPickExtraReason(ctx)
    }
}

/* ---------- 兜底表（照 quickReadCore.js 312-365 逐字搬，不许改文案） ---------- */
private fun fallbackPickExtraReason(ctx: Context): String {
    val catKey = ctx.catKey
    val q = ctx.q
    val g3 = ctx.g3
    val gg = G[g3] ?: return ""

    return when (catKey) {
        "ill" -> {
            val move = (g3 == "小吉" || g3 == "速喜")
            if (move) "老人、急病不能只凭落宫。人宫属动宜尽快就医，送医院则吉。"
            else "老人、急病不能只凭落宫。人要动才吉，务必主动就医不要等。"
        }
        "lost" -> {
            val lg = gg.wx
            val zw = ZWX[(ctx.zi - 1).coerceIn(0, 11)]
            when {
                KE[lg] == zw -> "主动找很可能找到，不要拖。"
                SHENG[zw] == lg -> "有外力帮忙，别人可能替你找到。"
                SHENG[lg] == zw -> "越拖越不利，但现在动手还来得及。"
                KE[zw] == lg -> "找起来费劲、阻力大。"
                else -> "以你现在住的地方为中心，往落宫所克方向找。"
            }
        }
        "weather" -> "问下不下是把天气问成了吉凶题，要按当地实际看，正问是天气如何。"
        "law" -> if (Regex("取保|保人|看守|被抓").containsMatchIn(q)) {
            "牢狱之象取勾陈空亡宫，据此转太极后看顺逆。"
        } else {
            "赤口主官非争讼，天宫赤口为谎言，原告被告看主客生克。"
        }
        "marriage" -> if (Regex("复合|分手").containsMatchIn(q) || g3 == "留连") {
            "感情卦里留连与空亡都要留活口，小吉主暧昧要注意第三方介入。"
        } else {
            "生你的就是喜欢你的，你喜欢的难成，但会有喜欢你的。"
        }
        "work" -> "赤口偏竞争、留连偏拖延、小吉主轻松贵人、大安主稳定。"
        "money" -> "落宫与地宫生克看财库，主动去拿才有着落，防暗中消耗。"
        "exam" -> "三吉卦偏胜、三凶卦偏阻，留连有平局可能，考场考官看天地。"
        "travel" -> "大安催则来，速喜马上到，小吉在路上，留连防耽搁，赤口防受阻。"
        "preg" -> "大安小吉空亡偏男，留连速喜赤口偏女，双胎看三宫重叠。"
        "general" -> "三宫相承前因、经过、结果，天地两宫生克纯属关系状态。"
        else -> ""
    }
}



/** 主函数：返回「**结论**\n\n一句理由」（** 是加粗标记，界面层负责吃掉） */
internal fun quickRead(ctx: Context, now: LocalDateTime): String {
    if (!G.containsKey(ctx.g3)) return ""
    val g1 = ctx.g1
    val g2 = ctx.g2
    val g3 = ctx.g3
    val gg = G[g3]!!
    val q = ctx.q
    val catKey = ctx.catKey

    val isWhere = Regex("哪|何处|什么地方|何方|位置|方位").containsMatchIn(q)
    val isPoint = Regex("什么时候|何时|几时|哪天|几月|哪年").containsMatchIn(q)
    val isDur = Regex("还有多久|多久能|多长时间|几天|几个月|多久").containsMatchIn(q)
    val isSuit = Regex("适合|该不该|要不要嫁|要不要娶|值得").containsMatchIn(q)
    val isYn = Regex("吗|嘛|是否|能否|会不会|能不能|可不可以|有没有|行不行|该不该").containsMatchIn(q) ||
        Regex("(.)不\\1").containsMatchIn(q)

    var head = ""
    val why = mutableListOf<String>()

    if (isWhere) {
        val keWx = KE[gg.wx]
        val keGong = GONG.firstOrNull { G[it]?.wx == keWx }
        head = "**往" + (keGong?.let { G[it]?.fw } ?: gg.fw) + "那边找**"
        val r = pickExtraReason(ctx)
        if (r.isNotEmpty()) why.add(r)
        else why.add("方向看的是落宫克的那一宫：它在哪边，就往哪边找，以你现在住的地方为中心。")
    } else if (isPoint) {
        head = "**" + whenPoint(g3, now) + "**"
        why.add("时间按这一宫管的季节推，吉的近一点，不吉的远一点。想钉到哪一年，得先知道你的属相。")
    } else if (isDur) {
        head = "**大概 " + durationOf(g3) + "**"
        why.add("这一宫自带三个数 " + (gg.san) + "，问\"还有多久\"就拿它当个量。数只是参考，不是日期。")
    } else if (isSuit && catKey == "marriage") {
        val ok = gg.jx.endsWith("吉")
        head = "**" + (if (ok) "适合" else "眼下还不适合") + "**"
        val s = (if (ok) MARRIAGE_SUIT_GOOD else MARRIAGE_SUIT_BAD)
            .replace(Regex("^适合。|^目前还不适合。"), "").trim()
        why.add(if (s.isNotEmpty()) s else ("这一宫偏" + tag(g3) + "，吉凶是" + gg.jx + "，现在不是能定下来的状态。"))
    } else if (catKey == "weather") {
        val d1 = WEATHER_BY_PALACE[g1] ?: ""
        val d2 = WEATHER_BY_PALACE[g2] ?: ""
        val d3 = WEATHER_BY_PALACE[g3] ?: ""
        val askRain = Regex("会下雨|会下雪|下不下|下雨吗|下雪吗|会不会下|要下吗|下不下雨").containsMatchIn(q)
        if (askRain) {
            head = "**" + (if (d3.contains("雨") || d3.contains("雪")) "会" else "不会") + "**"
            val r = pickExtraReason(ctx)
            if (r.isNotEmpty()) why.add(r)
            else why.add("先看落宫：落到" + g3 + "，天象是" + d3 + "，这就是结果；天宫" + g1 + "是" + d1 + "（气）、地宫" + g2 + "是" + d2 + "（地理气温），这两宫是形成它的条件。")
        } else {
            head = "**" + d3 + "**"
            why.add("先定落宫：落到" + g3 + "，天象是" + d3 + "——这就是天气本身；天宫" + g1 + "与地宫" + g2 + "是形成它的条件。")
        }
    } else if (isYn) {
        if (head.isEmpty()) {
            val lost = if (catKey == "lost") LOST_FIND[g3] else null
            if (lost != null) {
                head = "**" + lost.replace(Regex("。$"), "") + "**"
                val r = pickExtraReason(ctx)
                if (r.isNotEmpty()) why.add(r)
            } else {
                var verb = ""
                for ((k, v) in VD) if (q.contains(k)) {
                    verb = v
                    break
                }
                if (verb.isEmpty()) verb = if (q.contains("会")) "会" else if (q.contains("能")) "能" else "是"
                val yes = gg.jx.endsWith("吉")
                val negQ = Regex("严重|要紧|危险|厉害|更糟|恶化|有没有问题|有没有事|难吗").containsMatchIn(q)
                val good = if (negQ) !yes else yes
                val bian = (gg.xing).contains("变数")
                if (catKey == "ill") {
                    val ans = if (good) verb else if (verb == "有") "没有" else "不$verb"
                    val lv = luoVerdict(ctx, ans, negQ, good)
                    head = "**" + (lv ?: ans) + "**"
                    val r = pickExtraReason(ctx)
                    if (r.isNotEmpty()) why.add(r)
                } else if (bian) {
                    head = "**还没定，眼下还拖着**"
                    val r = pickExtraReason(ctx)
                    if (r.isNotEmpty()) why.add(r)
                    else why.add("落宫是留连：六宫里只有它自带变数，不是成了，也不是没成，是还缠着。")
                } else {
                    val ans = if (good) verb else if (verb == "有") "没有" else "不$verb"
                    val lv = luoVerdict(ctx, ans, negQ, good)
                    if (lv != null) {
                        head = "**$lv**"
                    } else {
                        head = "**$ans**"
                    }
                    val r = pickExtraReason(ctx)
                    if (r.isNotEmpty()) why.add(r)
                }
            }
        }
    } else {
        head = "**" + tag(g3) + "**"
        val r = pickExtraReason(ctx)
        if (r.isNotEmpty()) {
            why.add(r)
        } else {
            why.add(
                "你没问吉凶，那先看这一宫是什么脾气（" +
                    (if (gg.jx == g3) g3 else g3 + "，" + gg.jx) + "）。"
            )
        }
    }

    val isWeather = catKey == "weather"
    if (why.isEmpty() && !isWeather) {
        val k12 = pairRel(g1, g2)
        val k23 = pairRel(g2, g3)
        why.add(
            if (g1 == g2) "三宫 $g1→$g2→$g3：起头和中间同气，" + D2R[k23] + "。"
            else "三宫 $g1→$g2→$g3：" + T2D[k12] + "；" + D2R[k23] + "。"
        )
    }
    if (why.isEmpty()) why.add("最后落在" + g3 + "，" + gg.xing.replace(Regex("[。；\\s]+$"), "") + "。")

    return speak(head) + "\n\n" + why.take(1).joinToString("\n") { speak(it) }
}

/** 自检：跟旧 JS 的实跑结果逐条对 */
fun quickSelfCheck(): String {
    val now = LocalDateTime(2026, 10, 28, 13, 30)
    var bad = 0
    val detail = StringBuilder()
    for (cs in QUICK_CASES) {
        val cast = Cast(
            steps = emptyList(), zi = cs.zi,
            gi = listOf(GONG.indexOf(cs.g[0]), GONG.indexOf(cs.g[1]), GONG.indexOf(cs.g[2])),
            g = cs.g, ziName = ZHI[cs.zi - 1], mode = "", src = "",
        )
        val ctx = buildContext(q = cs.q, bg = "", catKey = autoCat(cs.q), cast = cast)
        val got = quickRead(ctx, now)
        if (got != cs.expect) {
            bad++
            if (bad <= 3) detail.append("\n[").append(cs.name).append("] 期望 ").append(cs.expect).append(" / 实得 ").append(got)
        }
    }
    return if (bad == 0) "QUICK PASS ${QUICK_CASES.size}/${QUICK_CASES.size}" else "QUICK FAIL $bad/${QUICK_CASES.size}$detail"
}
