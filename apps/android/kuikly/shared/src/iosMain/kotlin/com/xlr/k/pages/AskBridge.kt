package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tencent.kuikly.compose.ui.graphics.Color
import com.xlr.k.judge.ZHI

/**
 * 原生起卦卡（iOS）的桥。
 *
 * 分工：
 *   · Kuikly 这侧仍然是**唯一的状态源**（Session），负责起卦、判卦、存记录；
 *   · 卡片的外观/动画/输入框交给 Swift（NativeAskCard.swift）—— 高度动画走 Core Animation，
 *     主线程不参与每帧重排，这样详情展开才是原生手感；
 *   · 这里把状态整体推过去（值变了才推），Swift 改完再推回来（applyAskEdit）。
 *
 * ⚠ 只在 iOS 且桥装好之后才生效（Platform.nativeAsk）；否则 Kuikly 照旧画自己的卡，
 *   等于天然回退，不会出现「两边都不画、屏幕空一块」。
 */
@Composable
internal fun NativeAskBridge(onCast: () -> Unit) {
    if (!iosOnly() || !Platform.nativeAsk) return
    Platform.askCast = onCast

    val modeIdx = when (Session.mode) {
        "time" -> 1
        "num" -> 2
        else -> 0
    }
    val nums = Session.nums()
    val zi = ziNow(Session.mode)
    val cat = CAT_OPTS.firstOrNull { it.first == Session.catSel }?.second ?: "事类·自动"
    val st = mapOf(
        "q" to Session.q,
        "bg" to Session.bg,
        "mode" to modeIdx.toString(),
        "n0" to nums.getOrElse(0) { "" },
        "n1" to nums.getOrElse(1) { "" },
        "n2" to nums.getOrElse(2) { "" },
        "year" to Session.dtYear.toString(),
        "month" to Session.dtMonth.toString(),
        "day" to Session.dtDay.toString(),
        "zi" to zi.toString(),
        "ziName" to ZHI[zi - 1],
        "catName" to cat,
        "moreOpen" to if (Session.moreOpen) "1" else "0",
        "bgOpen" to if (Session.bgOpen) "1" else "0",
        "castLabel" to (CAST_LB[Session.mode] ?: ""),
        "hint" to miniText(Session.mode),
        /* 问候语也搬进原生那一摞：它跟卡片同一个 VStack，键盘抬起/详情展开时
           由同一条 Core Animation 带着走，不再一卡一卡。 */
        "greet1" to greetPair().first,
        "greet2" to greetPair().second,
        /* 校验错误（起卦前不合法时 Kotlin 写的 Session.err）：原生卡下面那条红字 */
        "err" to (if (Session.errOn) Session.err else ""),
        /* 开屏在不在场。只有开屏那颗球要卡片矩形（Session.cardRect 只有 SplashPage 读），
           带上这个 Swift 就能在开屏之后不再逐帧上报 —— 翻页/键盘位移时那是每帧一条通知。 */
        "splash" to (if (Session.splashOn) "1" else "0"),
    )
    LaunchedEffect(st) { Platform.pushAskState?.invoke(st) }

    val skin = mapOf(
        "ink" to hex(KY_INK),
        "dim" to hex(KY_DIM),
        "dim2" to hex(KY_DIM2),
        "line" to hex(KY_LINE),
        "face" to hex(glassFill),
        "edge" to hex(glassEdge),
        "ctl" to hex(glassFill),
        "track" to hex(veil(0.26f)),
        "pill" to hex(veil(0.96f)),
        "primary" to hex(KY_INK),
        "onPrimary" to hex(KY_BG),
        "accent" to hex(KY_ACCENT),
        /* 判定色（断辞里判词那一档）：原生详情要按 DocView 那套排版，颜色得是这一颗，
           ⛔ 别拿 accent 顶替 —— 两个色不是一个用途 */
        "jyc" to hex(KY_JYC),
        /* 抽屉面板用的实底：Kuikly 那版 drawer=true 就是 surface 全不透明 */
        "surface" to hex(Skin.def.surface),
        "rCard" to (if (Skin.cur == "bru") "0" else "24"),
        "dark" to (if (Skin.dark) "1" else "0"),
    )
    LaunchedEffect(skin) { Platform.pushAskSkin?.invoke(skin) }
}

/** Swift 推回来的编辑。键名见 NativeAskCardModel.push(...)。 */
internal fun applyAskEdit(k: String, v: String) {
    when {
        k == "q" -> Session.q = v
        k == "bg" -> Session.bg = v
        k == "mode" -> setAskMode(
            when (v) {
                "1" -> "time"
                "2" -> "num"
                else -> "clock"
            }
        )
        k.startsWith("num") -> {
            val i = k.removePrefix("num").toIntOrNull() ?: return
            if (i !in 0..2) return
            val next = Session.nums().toMutableList()
            next[i] = v.filter { it.isDigit() }.take(2)
            Session.setNums(next)
            if (Session.mode == "clock") Session.numManual = true
        }
        k == "moreOpen" -> {
            if (v == "1") {
                dismissIme()
                Session.detailOpen = true
                Session.moreOpen = true
            } else {
                Session.moreOpen = false
                Session.detailOpen = false
            }
        }
        k == "bgOpen" -> Session.bgOpen = (v == "1")
        k == "cast" -> Platform.askCast?.invoke()
        k.startsWith("pick_") -> applyPickerValue(k.removePrefix("pick_"), v)
        k.startsWith("rect") -> {
            /* 原生卡在屏幕上的矩形（x,y,w,h 逗号分隔）：开屏那颗球要落到它上面。
               卡改成原生之后没人量了 ⇒ 小球没落点，整段开屏动画错乱。 */
            val p = v.split(",").mapNotNull { it.toFloatOrNull() }
            if (p.size == 4) Session.cardRect = floatArrayOf(p[0], p[1], p[2], p[3])
        }
    }
}

/** Compose Color → "#RRGGBBAA"（Swift 侧解析 8 位带透明度）。 */
private fun hex(c: Color): String {
    fun ch(x: Float): String {
        val n = (x.coerceIn(0f, 1f) * 255f).toInt()
        return n.toString(16).padStart(2, '0')
    }
    return "#" + ch(c.red) + ch(c.green) + ch(c.blue) + ch(c.alpha)
}
