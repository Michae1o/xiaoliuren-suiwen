package com.xlr.k.pages

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.math.floor
import kotlin.math.sin

/** Same visual-only palette and HKO 2024–2028 term minute table as desktop V7, blob d190a08.
 * Outside that range, apparent solar longitude is approximate. Never feeds record counts or divination. */
internal data class RecallSeason(
    val term: String, val tagline: String, val mood: String,
    val ink: Long, val accent: Long, val second: Long, val glow: Long,
)
private val recallSeasons = listOf(
    RecallSeason("春分", "昼夜平分", "ink", 0xff132b26L, 0xffbce6acL, 0xff69a78bL, 0xff366753L),
    RecallSeason("清明", "万物清明", "dew", 0xff122925L, 0xffbed7bdL, 0xff64a499L, 0xff396960L),
    RecallSeason("谷雨", "雨生百谷", "rain", 0xff142824L, 0xffcbd59dL, 0xff60aba6L, 0xff42684bL),
    RecallSeason("立夏", "万物并秀", "ink", 0xff14291eL, 0xffd2e7a7L, 0xff84ad63L, 0xff335d40L),
    RecallSeason("小满", "青穗初盈", "dew", 0xff222c18L, 0xffece497L, 0xff9db064L, 0xff576d36L),
    RecallSeason("芒种", "风吹麦浪", "grain", 0xff302a17L, 0xfff0cd83L, 0xffc5a45fL, 0xff76653aL),
    RecallSeason("夏至", "昼长至极", "sun", 0xff302019L, 0xffffe08bL, 0xffe2995dL, 0xff8d5034L),
    RecallSeason("小暑", "温风初至", "sun", 0xff30201cL, 0xffffd294L, 0xffdf8a67L, 0xff8b453aL),
    RecallSeason("大暑", "流火盛时", "sun", 0xff311d1eL, 0xffffc47fL, 0xffe77760L, 0xff923b36L),
    RecallSeason("立秋", "一叶知秋", "grain", 0xff2d271bL, 0xffefc68aL, 0xff9aa371L, 0xff655c3bL),
    RecallSeason("处暑", "暑气渐收", "dew", 0xff242b26L, 0xffded0a4L, 0xff8fafa3L, 0xff4d6451L),
    RecallSeason("白露", "露凝为白", "dew", 0xff1c2b2cL, 0xffd4e3d8L, 0xff91b6b0L, 0xff435e5bL),
    RecallSeason("秋分", "秋色平分", "grain", 0xff2b2622L, 0xffdfc996L, 0xffb3886aL, 0xff705642L),
    RecallSeason("寒露", "清露微寒", "dew", 0xff202731L, 0xffdce2d4L, 0xff94a8bcL, 0xff414f70L),
    RecallSeason("霜降", "霜色入林", "frost", 0xff28252fL, 0xffe9dedaL, 0xffa8a9ccL, 0xff615368L),
    RecallSeason("立冬", "万物渐藏", "ink", 0xff172431L, 0xffc8ddd9L, 0xff819eadL, 0xff38536aL),
    RecallSeason("小雪", "雪意初临", "frost", 0xff141d30L, 0xffdce5fbL, 0xff93b6e3L, 0xff47628fL),
    RecallSeason("大雪", "雪满长空", "frost", 0xff1b2231L, 0xffedf0ffL, 0xffa4b4d2L, 0xff576782L),
    RecallSeason("冬至", "长夜向明", "sun", 0xff262432L, 0xfff6dfabL, 0xff928badL, 0xff6a5278L),
    RecallSeason("小寒", "霜月初深", "frost", 0xff1a2536L, 0xffe0e7f2L, 0xff97b7c5L, 0xff425a7cL),
    RecallSeason("大寒", "岁寒将尽", "frost", 0xff20283aL, 0xffecedf4L, 0xffa5a3c4L, 0xff52597bL),
    RecallSeason("立春", "草木知春", "ink", 0xff1e3027L, 0xffc8dba5L, 0xff82b596L, 0xff486d55L),
    RecallSeason("雨水", "春雨润物", "rain", 0xff192b2eL, 0xffc7e0ccL, 0xff74acb2L, 0xff43706dL),
    RecallSeason("惊蛰", "春雷始鸣", "rain", 0xff212839L, 0xffd8d8b7L, 0xffa39dc4L, 0xff545e7eL),
)
private val hko = mapOf(
    2024 to "01060449 01202207 02041627 02191213 03051023 03201106 04041502 04192200 05050810 05202100 06051210 06210451 07062220 07221544 08070809 08222255 09071111 09222044 10080300 10230615 11070620 11220356 12062317 12211721",
    2025 to "01051033 01200400 02032210 02181807 03051607 03201701 04042049 04200356 05051357 05210255 06051757 06211042 07070405 07222129 08071352 08230434 09071652 09230219 10080841 10231151 11071204 11220936 12070505 12212303",
    2026 to "01051623 01200945 02040402 02182352 03052159 03202246 04050240 04200939 05051949 05210837 06052348 06211625 07070957 07230313 08071943 08231019 09072241 09230805 10081429 10231738 11071752 11221523 12071053 12220450",
    2027 to "01052210 01201530 02040946 02190533 03060340 03210425 04050817 04201518 05060125 05211418 06060526 06212211 07071537 07230905 08080127 08231614 09080428 09231402 10082017 10232333 11072339 11222116 12071638 12221042",
    2028 to "01060355 01202122 02041531 02191126 03050925 03201017 04041403 04192109 05050712 05202010 06051116 06210402 07062130 07221454 08070721 08222201 09071022 09221945 10080209 10230513 11070527 11220254 12062225 12211620",
)
private val recallTermTimes by lazy {
    hko.flatMap { (year, row) ->
        row.split(" ").mapIndexed { i, stamp ->
            fun part(from: Int) = stamp.substring(from, from + 2).toInt()
            LocalDateTime(year, part(0), part(2), part(4), part(6)).toInstant(UtcOffset(hours = 8)) to ((i + 19) % 24)
        }
    }.sortedBy { it.first }
}
internal fun recallSeason(instant: Instant): RecallSeason {
    val time = instant.toEpochMilliseconds()
    if (time >= 1704038400000L && time < 1861891200000L) {
        val index = recallTermTimes.lastOrNull { it.first <= instant }?.second ?: 18
        return recallSeasons[index]
    }
    val t = (time / 86400000.0 + 2440587.5 - 2451545) / 36525
    val mean = 280.46646 + t * (36000.76983 + .0003032 * t)
    val anomaly = 357.52911 + t * (35999.05029 - .0001537 * t)
    fun sinDeg(d: Double) = sin(d * kotlin.math.PI / 180)
    val center = (1.914602 - t * (.004817 + .000014 * t)) * sinDeg(anomaly) +
        (.019993 - .000101 * t) * sinDeg(anomaly * 2) + .000289 * sinDeg(anomaly * 3)
    val ang = mean + center - .00569 - .00478 * sinDeg(125.04 - 1934.136 * t)
    return recallSeasons[floor(((ang % 360 + 360) % 360) / 15).toInt()]
}
