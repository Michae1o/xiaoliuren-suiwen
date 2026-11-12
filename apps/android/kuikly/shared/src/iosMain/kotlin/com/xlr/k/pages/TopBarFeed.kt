package com.xlr.k.pages

/**
 * 顶栏原生化的状态源（iOS）。
 *
 * 顶栏画在 Kuikly 页面之外的原生层里 —— 页面被键盘整体上移时它不动，这是「钉住」的唯一解法。
 * Kotlin 只出四个值（步进字 / 标签 / 明暗 / 隐藏），颜色交给系统材质。
 */
internal object TopBarFeed {
    /** 原生顶栏点了哪颗（menu / skin / daynight），由 RootPage 的 TopBar 挂上处理 */
    var onAction: ((String) -> Unit)? = null

    fun push(step: String, label: String, dark: Boolean, hidden: Boolean) {
        Platform.pushTopBar?.invoke(step, label, dark, hidden)
    }
}
