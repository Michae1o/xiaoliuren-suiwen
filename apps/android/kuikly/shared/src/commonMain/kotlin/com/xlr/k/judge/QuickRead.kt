package com.xlr.k.judge

import kotlinx.datetime.LocalDateTime

/* 本机速断（与 apps/desktop-web/src/data/quickReadCore.js 同构同义）。
 * 判据取本仓 corpus/00–08（开源判据）；本文件只放逻辑，断辞一律来自 CATS[*].luo 与 G。
 * 体系准则（随开源判据一并成立）：
 *   1. 三宫＝起因·经过·结果；吉凶只在人宫（落宫），天地两宫不论吉凶，只辨不足与太过。
 *   2. 宫与宫只用五行生、克与同气呼应（041、076、094）：生克只是事物纽带关系与趋势，不推能量强弱。
 *   3. 六神是理，象随所问而伸，不把个案事物写成固定死表。
 *   4. 问吉凶断吉凶，不问吉凶只答事实结果；问数量先论吉凶趋势再落数；不讲方法条款，不碰体用。 */

private val QR_AUSPICIOUS = setOf("大安", "速喜", "小吉")

private fun qrWxOf(g: String): String {
    val w = G[g]?.wx ?: return ""
    return if (w.isEmpty()) "" else w.substring(0, 1)
}

private fun qrJxLabel(g: String): String {
    val gg = G[g] ?: return g
    return if (gg.jx == g) g else "${gg.jx} · $g"
}

private fun qrCleanLuo(raw: String?): String = (raw ?: "")
    .trim()
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("^(大吉|中吉|小吉|大凶|中凶|小凶|吉|凶)[，,；;：:]\\s*(但)?"), "")
    .replace(Regex("[。；，]+$"), "")
    .replace(Regex("\\s+"), " ")

private fun qrRelToLuo(fromG: String, toG: String): String {
    val wa = qrWxOf(fromG)
    val wb = qrWxOf(toG)
    if (wa.isEmpty() || wb.isEmpty()) return "none"
    return when {
        wa == wb -> "same"
        SHENG[wa] == wb -> "gen_in"
        SHENG[wb] == wa -> "gen_out"
        KE[wa] == wb -> "ke_in"
        KE[wb] == wa -> "ke_out"
        else -> "none"
    }
}

private val QR_DEFECT_TRAIT = mapOf(
    "大安" to "求稳观望偏过、主动推进不足",
    "留连" to "拖延纠缠太久、决断推进不足",
    "速喜" to "起势急躁偏冲、后续沉稳不足",
    "赤口" to "口舌摩擦或条件卡扣太过",
    "小吉" to "枝节走动偏散、聚焦抓手不足",
    "空亡" to "实在条件悬虚、落地把握不足",
)

private val QR_POS_STATE = mapOf(
    "大安" to "稳守原步",
    "留连" to "拖延反复",
    "速喜" to "起势快热",
    "赤口" to "摩擦较劲",
    "小吉" to "循序走动",
    "空亡" to "条件悬虚",
)

private fun qrBadClause(where: String, g: String, g3: String, rel: String, isMarriage: Boolean): String {
    val wA = qrWxOf(g)
    val w3 = qrWxOf(g3)
    if (isMarriage && where == "中间" && g == "空亡") {
        return "中间似隔着长辈传话或一重隔断，沟通未落到实处"
    }
    val trait = QR_DEFECT_TRAIT[g] ?: ""
    return when (rel) {
        "ke_in" -> "$where$g（${wA}克${w3}）制约着结果，$trait"
        "gen_out" -> "落点反去顺应配合$where$g（${w3}生${wA}），重心偏到应付这一头，且$trait"
        "same" -> "$where${g}与${g3}同属${w3}气相牵，$trait"
        "ke_out" -> "虽有意主动去推动$where$g（${w3}克${wA}），但$where$trait"
        "gen_in" -> if (g3 == "赤口" || g3 == "空亡") {
            "$where$g（${wA}生${w3}）因$trait，反把事态推向$g3"
        } else {
            "$where$g（${wA}生${w3}）虽呈相生助力，但${where}仍显$trait"
        }
        else -> "$where$trait"
    }
}

private fun qrGoodClause(where: String, g: String, g3: String, rel: String, isMarriage: Boolean): String {
    val wA = qrWxOf(g)
    val w3 = qrWxOf(g3)
    val st = QR_POS_STATE[g] ?: g
    if (isMarriage && where == "中间" && g == "空亡") {
        return "中间虽似经转介或隔了一层（${g}），大局仍归顺"
    }
    return when (rel) {
        "gen_in" -> "$where$g（$st，${wA}生${w3}）生入结果、带来助力"
        "gen_out" -> "$where$g（$st）由落点顺势配合（${w3}生${wA}）"
        "same" -> "$where${g}与${g3}同属${w3}气，步调相协"
        "ke_out" -> "$where${g}虽呈$st，落点${g3}主动去克着推进（${w3}克${wA}），主导权在己"
        "ke_in" -> "$where$g（$st，${wA}克${w3}）虽在条件上略有制约，结果仍守得住"
        else -> "${where}呈${st}之象"
    }
}

private fun qrBuildFlow(g1: String, g2: String, g3: String, isBad: Boolean, catKey: String): String {
    val r1 = qrRelToLuo(g1, g3)
    val r2 = qrRelToLuo(g2, g3)
    val isM = catKey == "marriage"

    if (isBad) {
        if (r1 == "gen_in" && r2 == "gen_in" && g1 in QR_AUSPICIOUS && g2 in QR_AUSPICIOUS) {
            return "前因$g1、经过${g2}在关系上虽皆呈生助之势，奈何前两宫改不了落宫定局，落到${g3}本身还是未齐。"
        }
        val c1 = if (r1 == "gen_in" && r2 != "gen_in" && g1 in QR_AUSPICIOUS) {
            "起头$g1（${QR_POS_STATE[g1]}，${qrWxOf(g1)}生${qrWxOf(g3)}）本呈顺势条件"
        } else {
            qrBadClause("起头", g1, g3, r1, isM)
        }
        val c2 = if (r2 == "gen_in" && r1 != "gen_in" && g2 in QR_AUSPICIOUS) {
            "中间$g2（${QR_POS_STATE[g2]}，${qrWxOf(g2)}生${qrWxOf(g3)}）本有顺进条件"
        } else {
            qrBadClause("中间", g2, g3, r2, isM)
        }
        return "从三宫来由看，$c1，$c2。"
    }

    val c1 = qrGoodClause("起头", g1, g3, r1, isM)
    val c2 = qrGoodClause("中间", g2, g3, r2, isM)
    return "从前后来由看，$c1；$c2，终究落在$g3。"
}

private data class AskInfo(
    val isLostWhere: Boolean, val isLostYN: Boolean, val isLostCan: Boolean,
    val isWeatherCat: Boolean, val isWeatherYN: Boolean,
    val askQty: Boolean, val isScore: Boolean, val isMoneyNum: Boolean, val isDur: Boolean,
    val isPoint: Boolean, val hasFixedTime: Boolean,
    val isPregSex: Boolean,
    val isSuit: Boolean, val isCome: Boolean, val isBadConcern: Boolean, val isYN: Boolean,
)

private fun parseAsk(q: String, catKey: String): AskInfo {
    val s = q.trim()
    val isLostWhere = catKey == "lost" && Regex("在哪|哪儿|哪里|何处|丢哪|放哪|什么地方|什么位置").containsMatchIn(s)
    val isLostYN = catKey == "lost" && !isLostWhere && Regex("是不是丢|有没有丢|丢没丢|是不是没了|还在不在|真的丢了|丢了吗|丢了没").containsMatchIn(s)
    val isLostCan = catKey == "lost" && !isLostWhere && !isLostYN

    val isWeatherCat = catKey == "weather" || Regex("天气|下雨|下雪|晴|刮风|台风|暴雨|气温|冷不冷|热不热").containsMatchIn(s)
    val isWeatherYN = isWeatherCat && Regex("会下|会不会下|下不下|能晴|会晴|吗\$|嘛\$|么\$").containsMatchIn(s) && !Regex("如何|怎样|怎么样").containsMatchIn(s)

    val isScore = Regex("多少分|考多少|考几分|几分|第几名|排第几|名次|排名").containsMatchIn(s)
    val isMoneyNum = Regex("多少钱|花多少|赔多少|赚多少|亏多少|值多少|几块钱|几万").containsMatchIn(s)
    val isDur = Regex("多久|多长时间|几天|几个月|几年|多少天|几周").containsMatchIn(s)
    val isCount = Regex("几岁|几个|多少个|几人|几个人|几次|几号|几点|几日").containsMatchIn(s)
    val askQty = isScore || isMoneyNum || isDur || isCount

    val isPoint = !askQty && Regex("什么时候|何时|哪天|哪日|几月|哪一年|哪年|几时").containsMatchIn(s)
    val hasFixedTime = Regex("今天|今日|明天|明日|后天|昨晚|今晚|这周|本月|今年|这次|这趟|当下|眼下").containsMatchIn(s) && !isPoint && !isDur

    val isPregSex = catKey == "preg" && Regex("男|女|性别|儿子|闺女|女儿|男孩|女孩").containsMatchIn(s)
    val isSuit = Regex("适不适合|合不适合|合适吗|匹配吗|配不配").containsMatchIn(s)
    val isCome = Regex("会来|还来|不会来|来找|来吗|回来|到不到|几时到").containsMatchIn(s)
    val isBadConcern = Regex("要紧|严重|危险|出事|流产|分手|离婚|被骗|亏|赔|输|坏了|后遗症|病变|有没有事|有没有问题|有没有风险|有事吗|麻烦吗").containsMatchIn(s)

    val isOpen = Regex("怎么样|怎样|如何|什么心思|怎么看|为何|为什么|在哪|哪里|哪儿|多少|什么时候|多久|猜一个|射覆").containsMatchIn(s)
    val isYN = !askQty && !isPoint && !isOpen && Regex("吗|嘛|否|吧|好不好|行不行|能不能|会不会|是不是|有没有|可不可以|成不成|赢不赢|过不过|顺不顺|要紧|严重|危险|合不合|来不来").containsMatchIn(s)

    return AskInfo(
        isLostWhere, isLostYN, isLostCan,
        isWeatherCat, isWeatherYN,
        askQty, isScore, isMoneyNum, isDur,
        isPoint, hasFixedTime,
        isPregSex,
        isSuit, isCome, isBadConcern, isYN,
    )
}

private val QR_LOST_PLACE_SENSE = mapOf(
    "大安" to "稳当台面或常待原处（正东）",
    "留连" to "拐角、夹缝或抽屉袋底（东南偏角）",
    "速喜" to "明亮显眼或暖源人多处（正南）",
    "赤口" to "金属边角、门道窗台或经手交涉处（正西）",
    "小吉" to "走动途中、近水或饮食往来处（正北）",
    "空亡" to "包袋深层、盒内或视野盲区",
)

private val QR_LOST_STATE = mapOf(
    "大安" to "物件静在原位未乱动",
    "留连" to "物品停搁在角落缝隙里未走远",
    "速喜" to "物品落在显眼亮处或曾被人看见",
    "赤口" to "物件眼下略带阻隔、一时没声响",
    "小吉" to "物品已被人移动或随走动带离开过",
    "空亡" to "物件眼下落在盲区暗隙里",
)

private val QR_LOST_DIR_TITLE = mapOf(
    "大安" to "正东",
    "留连" to "东南偏角",
    "速喜" to "正南",
    "赤口" to "正西",
    "小吉" to "正北",
    "空亡" to "中暗隐蔽处",
)

private fun qrHandleLost(ask: AskInfo, g1: String, g2: String, g3: String): String {
    val r1 = qrRelToLuo(g1, g3)
    val r2 = qrRelToLuo(g2, g3)
    val nearHint = if (r2 == "ke_out" || r2 == "gen_in" || r2 == "same") {
        "多半还在家里或熟悉的近处范围，找起来相对顺手"
    } else {
        "多半带出了外头或落在隐蔽偏处，翻找会更费周折"
    }

    if (ask.isLostWhere) {
        val dir = QR_LOST_DIR_TITLE[g2] ?: g2
        val head = "**多在${dir}一带、偏${g2}之处**"
        return "$head\n\n${QR_LOST_STATE[g3]}。起初多半掉在${QR_LOST_PLACE_SENSE[g1]}，随后转向${QR_LOST_PLACE_SENSE[g2]}，$nearHint。"
    }

    if (ask.isLostYN) {
        val stayed = (g3 == "大安" || g3 == "留连") && (r2 == "gen_in" || r2 == "same" || r2 == "ke_out")
        val gone = (g3 == "空亡" || g3 == "赤口") && (r2 == "gen_out" || r2 == "ke_in" || (r2 == "gen_in" && g3 == "空亡"))
        if (stayed) {
            return "**多半还没真丢，仍在原处附近**（$g3）\n\n起初在${QR_LOST_PLACE_SENSE[g1]}，中间落到${QR_LOST_PLACE_SENSE[g2]}，目前呈静滞未散之象。先别当成丢没了，回刚才待过的边角和手边收纳处细摸即有。"
        }
        if (gone || g3 == "空亡" || g3 == "赤口") {
            val side = if (g3 == "空亡") "踪迹偏虚、大概率真落失或滑入盲区" else "中间带阻隔或被人动移、一时难见回应"
            return "**倾向已经落失，不在手边原处**（$g3）\n\n从${QR_LOST_PLACE_SENSE[g1]}经${QR_LOST_PLACE_SENSE[g2]}落到$g3，$side。不过随身袋包夹层和刚经手的显眼处仍值一翻，翻不到便是丢在外了。"
        }
        val hard = r1 == "ke_in" || r2 == "ke_in" || r1 == "gen_out" || r2 == "gen_out"
        return if (hard) {
            "**未必丢透，但已被挪动、快找才保得住**（$g3）\n\n先落于${QR_LOST_PLACE_SENSE[g1]}，后转往${QR_LOST_PLACE_SENSE[g2]}（过程与物件呈克抑或外移之势）。眼下快去沿途翻找打听还有望追回，久拖就易真失。"
        } else {
            "**暂不算丢透，找回把握较大**（$g3）\n\n起初在${QR_LOST_PLACE_SENSE[g1]}，后转往${QR_LOST_PLACE_SENSE[g2]}，${QR_LOST_STATE[g3]}，$nearHint。"
        }
    }

    val isGood = g3 in QR_AUSPICIOUS
    val head = if (isGood) {
        if (g3 == "小吉") "**有望找回，但要费些工夫**（小吉）" else "**有望找回**（${qrJxLabel(g3)}）"
    } else {
        if (g3 == "留连") "**眼下难立刻见到，尚可再寻**（小凶 · 留连）" else "**找回难度偏大，多半寻不见**（${qrJxLabel(g3)}）"
    }
    val catTx = qrCleanLuo(CATS["lost"]?.luo?.get(g3) ?: G[g3]?.xing)
    val extra = if (isGood) {
        "起初掉在${QR_LOST_PLACE_SENSE[g1]}，经过轨迹往${QR_LOST_PLACE_SENSE[g2]}，$nearHint。"
    } else {
        val midDesc = if (r2 == "gen_in" || r2 == "same") "好在内里活动轨迹未彻底散远，抓紧在近处细问细找" else "活动轨迹拉远或过程卡隔偏多，寻回希望低"
        "起头在${QR_LOST_PLACE_SENSE[g1]}、途经${QR_LOST_PLACE_SENSE[g2]}；$midDesc。"
    }
    return "$head\n\n$catTx。$extra"
}

private val QR_WEATHER_HEAD = mapOf(
    "大安" to "多半转晴或阴晴相间",
    "留连" to "多云转阴，带雾或细雨",
    "速喜" to "天晴见太阳",
    "赤口" to "有急雨或大雨",
    "小吉" to "有雨",
    "空亡" to "阴天少日头",
)

private val QR_WEATHER_AIR = mapOf(
    "大安" to "云气稳而半晴半阴",
    "留连" to "水汽郁滞散得慢",
    "速喜" to "暖阳燥热易发散",
    "赤口" to "冷热急冲酿骤雨",
    "小吉" to "水汽流动偏足",
    "空亡" to "云天暗沉而不透实",
)

private fun qrWeatherRel(gA: String, gB: String, labelA: String, labelB: String): String {
    val wa = qrWxOf(gA)
    val wb = qrWxOf(gB)
    return when {
        wa == wb -> "${labelA}与${labelB}同属${wb}气相协"
        SHENG[wa] == wb -> "$labelA（$wa）生起$labelB（$wb）"
        SHENG[wb] == wa -> "$labelB（$wb）生往$labelA（$wa）而发散"
        KE[wa] == wb -> "$labelA（$wa）制约$labelB（$wb）"
        KE[wb] == wa -> "$labelB（$wb）与$labelA（$wa）相克抵触"
        else -> ""
    }
}

private fun qrHandleWeather(ask: AskInfo, q: String, g1: String, g2: String, g3: String): String {
    val skyDesc = qrCleanLuo(CATS["weather"]?.luo?.get(g3) ?: QR_WEATHER_HEAD[g3])
    val l1 = qrWeatherRel(g1, g3, "先天气", "落象")
    val l2 = qrWeatherRel(g2, g3, "地面条件", "落象")
    val condBase = "先天气象${QR_WEATHER_AIR[g1]}，地面温湿${QR_WEATHER_AIR[g2]}（$l1，$l2）"

    if (ask.isWeatherYN) {
        if (Regex("雨|雪|冰雹|雷").containsMatchIn(q)) {
            return when (g3) {
                "赤口" -> "**会下，且雨势偏急偏大**（赤口）\n\n正主急雨大雨（春夏防雷、天寒防杂雹），既会降雨，对出行办事也添阻滞。$condBase。"
                "小吉" -> "**会有雨，雨势较为平缓**（小吉）\n\n主有雨落下但多不暴烈，外出带伞即安。$condBase。"
                "留连" -> "**多半阴湿带细雨或薄雾（寒天夹雪）**（留连）\n\n湿云滞住难散，易飘毛毛雨或湿冷小雪，注意防滑添衣。$condBase。"
                "速喜" -> "**多半不会下，天晴见太阳**（速喜）\n\n晴明见日，雨气聚不起来。$condBase。"
                "大安" -> "**不易下成雨，多半阴晴转稳**（大安）\n\n云气偏稳或雨止转晴，难见明显降水。$condBase。"
                else -> "**多半只是阴天，真下大雨的可能不高**（空亡）\n\n天色阴沉少太阳，湿气散虚难成实雨；只是天闷略扫兴。$condBase。"
            }
        }
        val sunny = g3 == "速喜" || g3 == "大安"
        val head = "${if (sunny) "**会放晴**" else "**难见大晴天**"}（${QR_WEATHER_HEAD[g3]} · $g3）"
        return "$head\n\n$condBase，终化为$skyDesc。"
    }
    return "**${QR_WEATHER_HEAD[g3]}**（$g3）\n\n$condBase，最终化为$skyDesc。"
}

private val QR_YQ_POINT = mapOf(
    "大安" to "寅卯月日或春季",
    "留连" to "辰巳或丑辰未戌月日（并留意卯木克应之时）",
    "速喜" to "巳午月日或夏季",
    "赤口" to "申酉月日或秋季",
    "小吉" to "亥子月日或冬季",
    "空亡" to "丑寅冬春交接之时（前提先把条件落稳）",
)

private fun qrHandleQuantity(ask: AskInfo, g1: String, g2: String, g3: String, catKey: String, isNight: Boolean): String {
    val gg = G[g3]!!
    val nums = gg.san
    val isGood = g3 in QR_AUSPICIOUS
    val flow = qrBuildFlow(g1, g2, g3, !isGood, catKey)

    if (ask.isScore) {
        val scoreTone = when (g3) {
            "大安" -> "发挥稳妥能过线，分数多看 $nums 里的数"
            "留连" -> "成绩在及格线上下晃荡，数看 $nums"
            "速喜" -> "发挥敏快、通过把握大，分数可看 $nums"
            "赤口" -> "分数吃紧防失误过不了线，数落 $nums"
            "小吉" -> "可望顺过但不算拔尖，数取 $nums"
            else -> "容易落空失分、难达预期，数参考 $nums"
        }
        val head = if (isGood) "**过关可期，数看 $nums**" else "**分数偏悬，数看 $nums**"
        return "$head\n\n先论吉凶趋势：$scoreTone。$flow"
    }

    if (ask.isMoneyNum) {
        val tone = when (g3) {
            "大安" -> "账面平稳、花用守规矩不过格"
            "留连" -> "账款偏慢或花在拖延细项上"
            "速喜" -> "来去都快，花得爽快也散得快"
            "赤口" -> "须防账目拉扯或额外破费一笔"
            "小吉" -> "整体可控、零碎走动开销不断"
            else -> "账目容易落空打水漂，恐不抵预期"
        }
        val head = if (isGood) "**花销尚算可控，数落 $nums**" else "**这笔账偏卡顿破费，数落 $nums**"
        return "$head\n\n先看事体：$tone。${flow}数额按实际体量对应 $nums（十、百、千等）来估。"
    }

    if (ask.isDur) {
        if (g3 == "空亡") {
            return "**一时难定准期，事恐有落空之虞**（${qrJxLabel(g3)}）\n\n目前条件悬虚、未见实着，先补缺口再谈日程。${flow}若勉强推进，期数只作后段参考（$nums）。"
        }
        val pace = when (g3) {
            "大安" -> "进展稳而偏慢，可按 $nums 计期"
            "留连" -> "战线容易拖长反复，期数参考 $nums${if (isNight) "（夜占仍有改动余量）" else ""}"
            "速喜" -> "转折快、很快见分晓，宜偏近取 $nums 计期"
            "赤口" -> "中间易卡顿争执，理顺后再看 $nums 为期"
            else -> "渐进见效不猛，期数按 $nums 往后顺推"
        }
        val head = if (isGood) "**约在 $nums 的周期里见着落**（${qrJxLabel(g3)}）" else "**周期偏拖带波折，数参考 $nums**（${qrJxLabel(g3)}）"
        return "$head\n\n$pace。$flow"
    }

    val catTx = qrCleanLuo(CATS[catKey]?.luo?.get(g3) ?: gg.xing)
    val head = if (isGood) "**顺向可期，数看 $nums**（${qrJxLabel(g3)}）" else "**偏滞或带波折，数看 $nums**（${qrJxLabel(g3)}）"
    return "$head\n\n先定事势：$catTx。${flow}数字可自 $nums 中顺实情取用。"
}

private fun qrHandleFengshui(g1: String, g2: String, g3: String): String {
    val isBad = g3 !in QR_AUSPICIOUS
    val head = when (g3) {
        "大安" -> "**宅气安稳平和**（大吉 · 大安）"
        "留连" -> "**宅气滞闷难舒**（小凶 · 留连）"
        "速喜" -> "**采光通透，但气场偏燥**（中吉 · 速喜）"
        "赤口" -> "**宅气偏冲，不够聚气安稳**（中凶 · 赤口）"
        "小吉" -> "**宅气活泛，需防聚财不厚**（小吉）"
        else -> "**气口偏虚，易散不聚**（大凶 · 空亡）"
    }
    val luoAir = when (g3) {
        "大安" -> "门路入气稳顺、家里少无谓风波，只是偏静乏活气"
        "留连" -> "入气处略显堵滞、拐角易憋闷潮沉，住久人易心累拖沓"
        "速喜" -> "门庭明朗、采光暖气足，唯火气偏燥，平时言行忌急躁"
        "赤口" -> "门路入气带有硬冲之象，外口易见尖角、噪声或路冲，家里亦防口舌损耗"
        "小吉" -> "宅气带着活泛走动之势、旁助尚可，只是水气溜走快，留财不厚、人偏常往外走"
        else -> "入气散虚、门户容易漏气难聚财安神，内里少踏实依靠感"
    }
    val r1 = qrRelToLuo(g1, g3)
    val r2 = qrRelToLuo(g2, g3)
    val segs = mutableListOf("以${g1}为向、${g2}为坐底、${g3}为入气位")
    if (g1 == g2) segs.add("向与坐底同宫端正")
    when (r1) {
        "ke_in", "ke_out" -> segs.add("向与入气相克、家里易有口角不和")
        "gen_out" -> segs.add("入气生往朝向（气往门外走，不易聚财留气）")
        "gen_in" -> segs.add("朝向生入气位、外气明光顺进")
        "same" -> segs.add("向与入气同气相顺")
    }
    when (r2) {
        "ke_in" -> segs.add("坐底克入气、宅居条件略呈制约之态")
        "gen_out" -> segs.add("入气生往坐底、气往宅底流散")
        "gen_in" -> segs.add("坐底生入气、屋基与门户相顺")
        "same" -> segs.add("坐底与门气同气相协")
        "ke_out" -> segs.add("入气克坐底、日常略需打理整顺")
    }
    val tip = if (isBad) "，宜先清理门道滞塞并避开直冲尖角。" else "，守好门路明洁通畅即安。"
    return "$head\n\n$luoAir。${segs.joinToString("，")}$tip"
}

private fun qrHandleLaw(g1: String, g2: String, g3: String, isNight: Boolean): String {
    val isBad = g3 !in QR_AUSPICIOUS
    val head = when (g3) {
        "大安" -> "**有理据可依，赢面偏稳但不快**（大吉 · 大安）"
        "留连" -> "**案情拖延拉锯，一时难胜**（小凶 · 留连）"
        "速喜" -> "**走势利快，抓紧据理有望占先**（中吉 · 速喜）"
        "赤口" -> "**交锋激烈易有损耗，很难占全便宜**（中凶 · 赤口）"
        "小吉" -> "**略向有利处缓走，调解折衷比硬拼稳**（小吉）"
        else -> "**诉求容易落空，赢面甚低**（大凶 · 空亡）"
    }
    val detail = when (g3) {
        "大安" -> "结果落在大安，己方理据站得稳、不致吃大亏，只是流程走得慢，重在守实证据。"
        "留连" -> "手续取证与对方拉扯没完没了，短期拿不下痛快结果${if (isNight) "（夜问更防横生变数）" else ""}。"
        "速喜" -> "利在证据备齐、果断上庭，趁热能见好音，切忌拖过时机。"
        "赤口" -> "犯官非口舌正冲，双方条款硬顶、缠斗破费重，难讨好。"
        "小吉" -> "事向顺处挪动但收获有限，多奔走调处反而容易落袋为安。"
        else -> "核心理据难落到实处，防诉求落空或吃暗亏。"
    }
    return "$head\n\n$detail${qrBuildFlow(g1, g2, g3, isBad, "law")}"
}

private fun qrHandleIntent(g1: String, g2: String, g3: String, isNight: Boolean): String {
    val isBad = g3 !in QR_AUSPICIOUS
    val head = when (g3) {
        "大安" -> "**心思稳重踏实，但态度偏被动**（大安）"
        "留连" -> "**还在犹豫观望，拿不定主意**（留连）"
        "速喜" -> "**眼下热度高，带着主动好感**（速喜）"
        "赤口" -> "**心里带着防备火气，略显挑剔**（赤口）"
        "小吉" -> "**有点好感与走动意愿，但保留偏多**（小吉）"
        else -> "**当下心思没落到实处，近乎放空**（空亡）"
    }
    val luoTxt = when (g3) {
        "大安" -> "对你并不排斥，图的是安稳踏实，只是性格慢热、不太会主动跨大步。"
        "留连" -> "心里反复权衡、拖着没给准信${if (isNight) "（夜里心思多变，更拿不稳）" else ""}，短时间难痛快挑明。"
        "速喜" -> "此刻对你注意集中、话热积极，只是这股热乎劲易散得快，长久还要后看。"
        "赤口" -> "心头带气或竖着防线，此时凑近容易呛声拌嘴，先别硬顶。"
        "小吉" -> "不排斥继续往来接触，但分寸算得稳、不肯轻易一下子交底。"
        else -> "心里其实空着、没怎么往深处打算，眼下别单方面投得太重。"
    }
    return "$head\n\n$luoTxt${qrBuildFlow(g1, g2, g3, isBad, "intent")}"
}

private fun qrHandleShefu(g1: String, g2: String, g3: String): String {
    val mat = mapOf(
        "大安" to "木纸纤维、规整素雅之物（青绿、原木色）",
        "留连" to "陶土织布、厚实私藏旧物（暗黄、棕褐色）",
        "速喜" to "发光发热、电子文书或亮饰（红暖、鲜艳色）",
        "赤口" to "金属石玉、带口器械或硬壳物（白银、冷灰色）",
        "小吉" to "盛水器皿、饮品液体或随身小物（黑蓝、深冷色）",
        "空亡" to "中空囊袋、盒壳或虚轻隐蔽物（素灰、暗土色）",
    )
    val use = mapOf(
        "大安" to "稳置常用或正位护持之物",
        "留连" to "收纳、角落搁置或贴身留存之物",
        "速喜" to "传讯、照明、发声或文书展示之物",
        "赤口" to "开启、切割、卡扣或五金工具类",
        "小吉" to "随行携带、盛水饮食或流通用品",
        "空亡" to "中空承载、遮盖掩藏之器",
    )
    val baseLine = if (g1 == g3) "表里材质前后一体偏同类，" else "质地源头略带${mat[g1]}的影子，"
    return "**物象偏向${qrWxOf(g3)}性、${G[g3]?.fw}方位特征之物**（$g3）\n\n外观呈现倾向${mat[g3]}。${baseLine}平时多作「${use[g2]}」之用，可按材质与用途交集来收窄范围。"
}

private fun qrHandlePreg(ask: AskInfo, g1: String, g2: String, g3: String): String {
    if (ask.isPregSex) {
        val isBoy = g3 == "大安" || g3 == "小吉" || g3 == "空亡"
        val head = if (isBoy) "**偏向男孩**（$g3）" else "**偏向女孩**（$g3）"
        val isBad = g3 !in QR_AUSPICIOUS
        val care = if (isBad) "平素多留心安心调养与按时产检。" else "目前胎气与大局大致平稳，温和保养即好。"
        return "$head\n\n按六宫阴阳归属，$g3${if (isBoy) "偏阳丁，男孩倾向高" else "偏阴秀，女孩倾向高"}。$care${qrBuildFlow(g1, g2, g3, isBad, "preg")}"
    }
    val r1 = qrRelToLuo(g1, g3)
    if (g3 == "大安" && (g1 == "小吉" || g1 == "速喜") && r1 == "gen_in") {
        return "**已有受孕之喜，且胎象安稳**（大安）\n\n单看大安似未动，但起因${g1}动气直接生入大安，正是动而生孕、平安着床之兆。${qrBuildFlow(g1, g2, g3, false, "preg")}"
    }
    val hasPreg = g3 == "留连" || g3 == "速喜" || g3 == "小吉"
    val head = if (hasPreg) "**偏向有孕**（$g3）" else "**目前偏向未孕**（$g3）"
    val luoTxt = qrCleanLuo(CATS["preg"]?.luo?.get(g3) ?: G[g3]?.xing)
    return "$head\n\n$luoTxt。${qrBuildFlow(g1, g2, g3, !hasPreg, "preg")}"
}

private fun qrHandleTravel(ask: AskInfo, g1: String, g2: String, g3: String, isNight: Boolean): String {
    val isBad = g3 !in QR_AUSPICIOUS
    if (ask.isCome) {
        val (h, desc) = when (g3) {
            "大安" -> "**人还没动身，但有来的希望**（大安）" to "对方还在原地没走动，打个电话催一声多半就会启程。"
            "留连" -> "**多半要拖延晚到**（留连）" to "半路或手头事绊住了脚步${if (isNight) "（夜占更有改期之变）" else ""}，一时按点到不了。"
            "速喜" -> "**很快就要到了**（速喜）" to "人已起行且消息近了，催问一句马上就能碰面。"
            "赤口" -> "**多半不想来或来不成**（赤口）" to "心里有抵触或半道遇阻隔口角，此刻盼不到人来。"
            "小吉" -> "**已经在路上了**（小吉）" to "虽然一路步子慢、略费周折，人是顺顺当当赶过来的。"
            else -> "**不会来了，大概率落空**（空亡）" to "这趟行程多半取消或未起行，别在原地干等。"
        }
        return "$h\n\n$desc${qrBuildFlow(g1, g2, g3, isBad, "travel")}"
    }
    val tHead = when (g3) {
        "大安" -> "**身未动，暂时不太顺启程**（大吉 · 大安）"
        "留连" -> "**途中易延误滞留，行期生变**（小凶 · 留连）"
        "速喜" -> "**起行迅捷顺畅，宜早定早走**（中吉 · 速喜）"
        "赤口" -> "**出行防磕碰意外与路途口角**（中凶 · 赤口）"
        "小吉" -> "**可以成行，平顺略费脚力**（小吉）"
        else -> "**行程易落空改期，一时走不成**（大凶 · 空亡）"
    }
    val catTx = qrCleanLuo(CATS["travel"]?.luo?.get(g3) ?: G[g3]?.xing)
    return "$tHead\n\n$catTx。${qrBuildFlow(g1, g2, g3, isBad, "travel")}"
}

private fun qrHandleGeneralCat(
    ask: AskInfo, q: String, g1: String, g2: String, g3: String, catKey: String, isNight: Boolean,
): String {
    val gg = G[g3]!!
    val isGood = g3 in QR_AUSPICIOUS
    val catTx = qrCleanLuo(CATS[catKey]?.luo?.get(g3) ?: gg.xing)

    if (catKey == "marriage" && ask.isCome) {
        val (h, desc) = when (g3) {
            "大安" -> "**暂不会主动过来，但还没绝来意**（大吉 · 大安）" to "对方稳在原处未动，你先轻轻递个话往往能催动。"
            "留连" -> "**还在犹豫拉扯，一时难干脆来找你**（小凶 · 留连）" to "心里纠结反复、拖着未定${if (isNight) "（夜占尤多摇摆）" else ""}。"
            "速喜" -> "**很快会主动来找你**（中吉 · 速喜）" to "念头起得热快，顺势接着别拖凉了。"
            "赤口" -> "**多半不会主动来找你**（中凶 · 赤口）" to "心里憋着火气或别扭劲，硬碰还容易吵僵。"
            "小吉" -> "**会慢慢主动走动联系**（小吉）" to "心里有点意思但步伐稳小，顺着接应能越走越近。"
            else -> "**大概率不会再主动来了**（大凶 · 空亡）" to "这头联系已显得悬空，早作安排别空等。"
        }
        return "$h\n\n$desc${qrBuildFlow(g1, g2, g3, !isGood, catKey)}"
    }

    if (ask.isSuit) {
        val (h, desc) = when (g3) {
            "大安" -> "**适合，安稳踏实**（大吉 · 大安）" to "相处或处境心里有底，虽欠点波澜刺激，长久是相宜的。"
            "留连" -> "**当下不太适合，纠葛偏多**（小凶 · 留连）" to "现实或心结还缠着，勉强凑一块容易反复烦心。"
            "速喜" -> "**当下合拍投缘，适合尝试**（中吉 · 速喜）" to "眼下气味相投、互动爽快，长远相处再落到日常稳住。"
            "赤口" -> "**目前不太适合，容易硬碰生隙**（中凶 · 赤口）" to "棱角与脾气易冲撞，走近反倒累心伤和气。"
            "小吉" -> "**大体合适，需要多些磨合**（小吉）" to "底色向好，只是步子慢，彼此多付出几分就能顺起来。"
            else -> "**眼下并不适合，心里难落实**（大凶 · 空亡）" to "条件悬着没着落，硬合难以长久。"
        }
        return "$h\n\n$desc${qrBuildFlow(g1, g2, g3, !isGood, catKey)}"
    }

    val illOrigin = if (catKey == "ill") {
        when (g1) {
            "大安" -> "病源多涉积劳或肝木筋骨失养，"
            "留连" -> "病源多偏脾胃滞积或缠绵旧症，"
            "速喜" -> "起头多带虚火炎症或心血急热，"
            "赤口" -> "起头多涉外伤痛点或金石阻闭，"
            "小吉" -> "起头多与受寒湿气或外感奔波相关，"
            "空亡" -> "起头常见气虚痰滞或心神郁结，"
            else -> ""
        }
    } else ""

    if (catKey == "ill" && g3 == "空亡") {
        val head = "**若有实病则险象重，查无实症或属虚惊**（大凶 · 空亡）"
        return "$head\n\n$illOrigin$catTx。${qrBuildFlow(g1, g2, g3, true, catKey)}"
    }

    if (catKey == "work" && g3 == "大安" && Regex("面试|求职|找工作|入职|转正").containsMatchIn(q)) {
        return "**有望稳妥通过，但流程偏慢**（大吉 · 大安）\n\n结果安稳占吉，只是大安性静步缓，通知与手续急不来。${qrBuildFlow(g1, g2, g3, false, catKey)}"
    }

    if (catKey == "gamble") {
        val head = when (g3) {
            "大安" -> "**手气平稳，略赢即收**（大吉 · 大安）"
            "留连" -> "**局势胶着，平局微输**（小凶 · 留连）"
            "速喜" -> "**短玩可赢，切忌贪久**（中吉 · 速喜）"
            "赤口" -> "**防大输破财，趁早收手**（中凶 · 赤口）"
            "小吉" -> "**慢进微赢，忌重注久坐**（小吉）"
            else -> "**多半落空大输或白忙一场**（大凶 · 空亡）"
        }
        return "$head\n\n$catTx。${qrBuildFlow(g1, g2, g3, !isGood, catKey)}"
    }

    val head = if (ask.isYN) {
        if (ask.isBadConcern) {
            if (isGood) {
                val safe = when (catKey) {
                    "ill" -> if (g3 == "小吉") "不算急重，但不可轻视" else "不大要紧，可保平安"
                    "marriage" -> "不至于散局，关系还稳"
                    "money" -> "不至于真亏，尚有余地"
                    "exam" -> "不必太慌，过关有望"
                    else -> "不大要紧，险情可控"
                }
                "**$safe**（${qrJxLabel(g3)}）"
            } else {
                val risk = when (catKey) {
                    "ill" -> if (g3 == "留连") "不算急危，但拖延缠人须早治" else "确实不能轻视，隐患偏重"
                    "marriage" -> if (g3 == "赤口") "防口角闹僵，确实有风险" else "问题悬着未解，风险较高"
                    "money" -> if (g3 == "赤口") "防真破财惹纠纷，得当心" else "确实有亏损落空之险"
                    else -> "确实存在阻滞风险，需要留意"
                }
                "**$risk**（${qrJxLabel(g3)}）"
            }
        } else {
            val verb = when {
                q.contains("过") -> if (isGood) "能过" else "较悬，难过"
                q.contains("赢") -> if (isGood) "赢面大" else "赢面小"
                q.contains("成") -> if (isGood) "可成" else "眼下难成"
                q.contains("顺") -> if (isGood) "顺畅向好" else "阻滞偏多"
                q.contains("合") -> if (isGood) "合适可处" else "暂不太合"
                q.contains("行") -> if (isGood) "可以成行" else "暂不太行"
                q.contains("可") -> if (isGood) "可以" else "暂不建议"
                q.contains("会") -> if (isGood) "会往好里走" else "多半难如愿"
                else -> if (isGood) "可成向好" else "眼下偏悬难成"
            }
            val caveat = if (g3 == "留连" && isNight) "夜占留连仍有变数" else qrJxLabel(g3)
            "**$verb**（$caveat）"
        }
    } else {
        val brief = when (g3) {
            "大安" -> "稳妥安顺，宜守当下"
            "留连" -> "拖延卡顿，一时难定"
            "速喜" -> "来得迅快，宜早落定"
            "赤口" -> "多阻隔口舌，防破损"
            "小吉" -> "缓步向好，须多出力"
            else -> "容易落空，暂难落地"
        }
        "**$brief**（${qrJxLabel(g3)}）"
    }

    val nightNote = if (g3 == "留连" && isNight) "夜占留连仍有变数。" else ""
    return "$head\n\n$illOrigin$catTx。$nightNote${qrBuildFlow(g1, g2, g3, !isGood, catKey)}"
}

internal fun quickRead(ctx: Context, now: LocalDateTime): String {
    if (!G.containsKey(ctx.g3)) return ""
    val q = ctx.q.trim()
    val catKey = if (ctx.catKey.isNotEmpty() && CATS.containsKey(ctx.catKey)) ctx.catKey else autoCat(q)
    val g1 = ctx.g1.ifEmpty { "大安" }
    val g2 = ctx.g2.ifEmpty { "大安" }
    val g3 = ctx.g3
    val zi = ctx.zi
    val isNight = if (zi > 0) (zi >= 11 || zi <= 2) else (now.hour >= 19 || now.hour < 5)

    val ask = parseAsk(q, catKey)
    var res = when {
        catKey == "lost" -> qrHandleLost(ask, g1, g2, g3)
        ask.isWeatherCat -> qrHandleWeather(ask, q, g1, g2, g3)
        ask.askQty -> qrHandleQuantity(ask, g1, g2, g3, catKey, isNight)
        catKey == "fengshui" -> qrHandleFengshui(g1, g2, g3)
        catKey == "law" -> qrHandleLaw(g1, g2, g3, isNight)
        catKey == "intent" -> qrHandleIntent(g1, g2, g3, isNight)
        catKey == "shefu" -> qrHandleShefu(g1, g2, g3)
        catKey == "preg" -> qrHandlePreg(ask, g1, g2, g3)
        catKey == "travel" -> qrHandleTravel(ask, g1, g2, g3, isNight)
        else -> qrHandleGeneralCat(ask, q, g1, g2, g3, catKey, isNight)
    }

    if (ask.isPoint && !ask.hasFixedTime && catKey != "lost" && !ask.isWeatherCat) {
        val isBad = g3 !in QR_AUSPICIOUS
        res += when {
            g3 == "空亡" -> "\n\n眼下条件未落稳，硬定日期前先补实缺口；若论节候参考${QR_YQ_POINT[g3]}。"
            isBad && Regex("好|转|解决|出院|结束|离|恢复").containsMatchIn(q) -> "\n\n转机多留意${QR_YQ_POINT[g3]}，或生入落宫的月日时。"
            else -> "\n\n时机重点看${QR_YQ_POINT[g3]}。"
        }
    }

    return res
}

/** 自检（形态闸门）：每条必须非空、以 ** 开头、不含出戏词/禁用术语 */
private val QUICK_BAD_WORDS = listOf(
    "依据", "判据", "卦例", "原文说", "体用", "比助", "比劫", "妻财", "落宫所克", "行业属性",
    "材料", "本类", "未载", "宫性", "原文", "J-", "⛔",
)

fun quickSelfCheck(): String {
    val now = LocalDateTime(2026, 10, 28, 13, 30)
    val bad = mutableListOf<String>()
    for (cs in QUICK_CASES) {
        val cast = Cast(
            steps = emptyList(), zi = cs.zi,
            gi = listOf(GONG.indexOf(cs.g[0]), GONG.indexOf(cs.g[1]), GONG.indexOf(cs.g[2])),
            g = cs.g, ziName = ZHI[cs.zi - 1], mode = "", src = "",
        )
        val ctx = buildContext(q = cs.q, bg = "", catKey = autoCat(cs.q), cast = cast)
        val got = quickRead(ctx, now)
        when {
            got.isEmpty() -> bad.add(cs.name + ": 空输出")
            !got.startsWith("**") -> bad.add(cs.name + ": 结论未加粗")
            else -> {
                val hit = QUICK_BAD_WORDS.firstOrNull { got.contains(it) }
                if (hit != null) bad.add(cs.name + ": 含禁用词「" + hit + "」")
            }
        }
    }
    return if (bad.isEmpty()) {
        "QUICK OK " + QUICK_CASES.size + "/" + QUICK_CASES.size + "（非空·结论加粗·无术语）"
    } else {
        "QUICK FAIL " + bad.size + "/" + QUICK_CASES.size + "\n" + bad.take(6).joinToString("\n")
    }
}
