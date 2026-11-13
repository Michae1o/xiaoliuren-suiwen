package com.xlr.k.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.gestures.detectTapGestures
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.BoxScope
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.offset
import com.tencent.kuikly.compose.ui.platform.LocalDensity
import com.tencent.kuikly.compose.ui.geometry.Offset
import com.tencent.kuikly.compose.ui.layout.boundsInRoot
import com.tencent.kuikly.compose.ui.layout.onGloballyPositioned
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.text.SelectionContainer
import com.tencent.kuikly.compose.text.TextSelectionType
import com.tencent.kuikly.compose.text.rememberSelectionContainerState
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.input.pointer.pointerInput
import com.tencent.kuikly.compose.ui.unit.dp

/** Native selection handles must win over page/drawer horizontal drags. */
internal object CardSelection {
    private var owner: Any? by mutableStateOf(null)
    val active: Boolean get() = owner != null
    private var cancelOwner: (() -> Unit)? = null
    fun begin(token: Any, clear: () -> Unit) {
        if (owner !== token) cancel()
        owner = token
        cancelOwner = clear
    }
    fun end(token: Any) {
        if (owner === token) { owner = null; cancelOwner = null }
    }
    fun cancel() {
        val action = cancelOwner
        owner = null
        cancelOwner = null
        action?.invoke()
    }
}

/** Kuikly 2.30 exposes native selection, but leaves the copy action to the app.
 * Keep controls outside the selectable subtree. Changing text invalidates old offsets.
 */
@Composable
internal fun SelectableCardText(
    textKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberSelectionContainerState()
    val token = remember { Any() }
    val density = LocalDensity.current.density
    var selected by remember { mutableStateOf(false) }
    var visibleTop by remember { mutableStateOf(0f) }
    var visibleBottom by remember { mutableStateOf(0f) }
    var menuHeight by remember { mutableStateOf(44f * density) }
    fun clear() {
        state.clearSelection()
        selected = false
        CardSelection.end(token)
    }
    LaunchedEffect(textKey) { clear() }
    DisposableEffect(Unit) { onDispose { clear() } }
    Box(modifier = modifier.onGloballyPositioned { coordinates ->
        // boundsInRoot intersects ancestor clips, including the scrolling viewport.
        val clipped = coordinates.boundsInRoot()
        val origin = coordinates.localToRoot(Offset.Zero)
        visibleTop = (clipped.top - origin.y).coerceAtLeast(0f)
        visibleBottom = (clipped.bottom - origin.y).coerceAtLeast(visibleTop)
    }) {
        SelectionContainer(
            state = state,
            modifier = Modifier.pointerInput(state) {
                detectTapGestures(onLongPress = { point ->
                    state.createSelection(point.x, point.y, TextSelectionType.WORD)
                })
            },
            onSelectStart = { CardSelection.begin(token, ::clear); selected = true },
            onSelectChange = { CardSelection.begin(token, ::clear); selected = true },
            onSelectCancel = { selected = false; CardSelection.end(token) },
            content = content,
        )
        if (selected && visibleBottom > visibleTop) {
            val menuY = state.selectionFrame.y.coerceIn(
                visibleTop, (visibleBottom - menuHeight).coerceAtLeast(visibleTop))
            Row(modifier = Modifier.align(Alignment.TopEnd)
                .offset(y = (menuY / density).dp)
                .onGloballyPositioned { menuHeight = it.size.height.toFloat() }
                .background(Skin.def.surface.copy(alpha = 1f), RoundedCornerShape(8.dp)).padding(4.dp)) {
                Text_("复制", fontSize = 12f, color = KY_INK,
                    modifier = Modifier.tapNoIndication {
                        state.getSelection { result ->
                            val text = result.joinToString("\n")
                            if (text.isNotEmpty()) {
                                Platform.copy?.invoke(text)
                                Session.say("已复制")
                            }
                            clear()
                        }
                    }.padding(8.dp))
                Text_("取消", fontSize = 12f, color = KY_DIM,
                    modifier = Modifier.tapNoIndication { clear() }.padding(8.dp))
            }
        }
    }
}
