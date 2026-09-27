package com.kelele.manliu

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

internal data class DragDrop(val movedId: Long, val orderedIds: List<Long>)

/** Keeps a dragged order visible until Room emits the saved order. */
internal class DragReorderState(internal val listState: LazyListState) {
    private var order by mutableStateOf<List<Long>?>(null)
    var draggingId by mutableStateOf<Long?>(null)
        private set
    private var dragTop by mutableFloatStateOf(0f)
    private var draggedHeight = 0
    private var originalOrder = emptyList<Long>()

    fun <T> arranged(source: List<T>, id: (T) -> Long): List<T> {
        val ids = order ?: return source
        if (ids.size != source.size) return source
        val byId = source.associateBy(id)
        if (byId.size != ids.size) return source
        return ids.mapNotNull(byId::get).takeIf { it.size == source.size } ?: source
    }

    fun sourceChanged(ids: List<Long>) {
        val pending = order ?: return
        if (draggingId != null) return
        if (ids == pending || ids.size != pending.size || ids.toSet() != pending.toSet()) {
            order = null
        }
    }

    fun start(id: Long, displayedIds: List<Long>) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        if (displayedIds.size < 2) return
        originalOrder = displayedIds
        order = displayedIds
        draggingId = id
        draggedHeight = item.size
        dragTop = item.offset.toFloat()
    }

    fun drag(deltaY: Float) {
        if (draggingId == null) return
        dragTop += deltaY
        moveAcrossItems()
    }

    fun offsetFor(id: Long): Int {
        if (draggingId != id) return 0
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return 0
        return (dragTop - item.offset).toInt()
    }

    private fun moveAcrossItems() {
        val id = draggingId ?: return
        val ids = order ?: return
        val middle = dragTop + draggedHeight / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && middle >= it.offset && middle < it.offset + it.size
        } ?: return
        val from = ids.indexOf(id)
        val to = ids.indexOf(target.key as? Long ?: return)
        if (from < 0 || to < 0 || from == to) return
        order = ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    suspend fun autoScroll(edgePx: Float) {
        while (draggingId != null) {
            val layout = listState.layoutInfo
            val topGap = dragTop - layout.viewportStartOffset
            val bottomGap = layout.viewportEndOffset - (dragTop + draggedHeight)
            val speed = when {
                topGap < edgePx -> -max(3f, (edgePx - topGap) / edgePx * 26f)
                bottomGap < edgePx -> max(3f, (edgePx - bottomGap) / edgePx * 26f)
                else -> 0f
            }
            if (speed != 0f) {
                listState.scrollBy(speed)
                moveAcrossItems()
            }
            delay(16)
        }
    }

    fun finish(): DragDrop? {
        val id = draggingId ?: return null
        val result = order?.takeIf { it != originalOrder }
        draggingId = null
        dragTop = 0f
        if (result == null) order = null
        return result?.let { DragDrop(id, it) }
    }

    fun cancel() {
        draggingId = null
        dragTop = 0f
        order = null
    }
}

@Composable
internal fun Modifier.dragReorder(
    state: DragReorderState,
    displayedIds: List<Long>,
    enabled: Boolean,
    onDrop: (DragDrop) -> Unit,
): Modifier {
    val ids = rememberUpdatedState(displayedIds)
    val drop = rememberUpdatedState(onDrop)
    val edgePx = with(LocalDensity.current) { 80.dp.toPx() }
    val handlePx = with(LocalDensity.current) { 84.dp.toPx() }
    LaunchedEffect(state.draggingId, enabled) {
        if (!enabled) state.cancel()
        else if (state.draggingId != null) state.autoScroll(edgePx)
    }
    if (!enabled) return this
    return pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { position ->
                if (position.x >= size.width - handlePx) {
                    val item = state.listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        position.y >= it.offset && position.y < it.offset + it.size
                    }
                    (item?.key as? Long)?.let { state.start(it, ids.value) }
                }
            },
            onDrag = { change, amount ->
                if (state.draggingId != null) {
                    change.consume()
                    state.drag(amount.y)
                }
            },
            onDragEnd = { state.finish()?.let { drop.value(it) } },
            onDragCancel = { state.cancel() },
        )
    }
}

@Composable
internal fun DragHandle() {
    Box(
        Modifier.size(48.dp).semantics { contentDescription = "长按拖动排序" },
        contentAlignment = Alignment.Center,
    ) {
        Text("≡", style = MaterialTheme.typography.headlineSmall)
    }
}
