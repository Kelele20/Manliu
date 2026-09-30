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

/**
 * 拖拽排序状态机（防抖动与平滑无级过渡版）
 * 消除项交换时因单帧基准 offset 突变引起的抽搐，并保证 Room 数据库落盘前画面无回弹闪烁。
 */
internal class DragReorderState(internal val listState: LazyListState) {
    private var order by mutableStateOf<List<Long>?>(null)
    var draggingId by mutableStateOf<Long?>(null)
        private set
    val waitingForSave: Boolean get() = order != null && draggingId == null

    // 手指相对于当前卡片初始锚点的实时位移
    private var draggingOffset by mutableFloatStateOf(0f)
    private var draggedItemHeight = 0
    private var initialItemOffset = 0
    private var originalOrder = emptyList<Long>()

    fun <T> arranged(source: List<T>, id: (T) -> Long): List<T> {
        val ids = order ?: return source
        if (ids.size != source.size) return source
        val byId = source.associateBy(id)
        if (byId.size != ids.size) return source
        return ids.mapNotNull(byId::get).takeIf { it.size == source.size } ?: source
    }

    /** 只有当外部真实数据流完全与保存结果一致时才解冻，彻底避免松手回弹闪烁 */
    fun sourceChanged(ids: List<Long>) {
        val pending = order ?: return
        if (draggingId != null) return
        if (ids == pending) {
            order = null
        } else if (ids.size != pending.size || ids.toSet() != pending.toSet()) {
            // 列表内容发生了外部增删，不得不重置
            order = null
        }
    }

    fun start(id: Long, displayedIds: List<Long>) {
        if (waitingForSave) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        if (displayedIds.size < 2) return
        originalOrder = displayedIds
        order = displayedIds
        draggingId = id
        draggedItemHeight = item.size
        initialItemOffset = item.offset
        draggingOffset = 0f
    }

    fun drag(deltaY: Float) {
        if (draggingId == null) return
        draggingOffset += deltaY
        moveAcrossItems()
    }

    /** 当前被拖拽项的平滑相对偏移（像素） */
    fun offsetFor(id: Long): Int {
        if (draggingId != id) return 0
        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return 0
        // 将手指位移与底层 item.offset 的实际变动结合，保证绝对坐标严格连续，消除单帧跳跃
        val currentItemOffset = currentItem.offset
        val visualTop = initialItemOffset + draggingOffset
        return (visualTop - currentItemOffset).toInt()
    }

    private fun moveAcrossItems() {
        val id = draggingId ?: return
        val ids = order ?: return
        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val from = ids.indexOf(id)
        if (from < 0) return

        val currentVisualCenter = initialItemOffset + draggingOffset + draggedItemHeight / 2f
        val items = listState.layoutInfo.visibleItemsInfo

        val target = items.firstOrNull {
            it.key != id && currentVisualCenter >= it.offset && currentVisualCenter < it.offset + it.size
        } ?: return

        val to = ids.indexOf(target.key as? Long ?: return)
        if (to < 0 || from == to) return

        // 发生项交换
        order = ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    suspend fun autoScroll(edgePx: Float) {
        while (draggingId != null) {
            val layout = listState.layoutInfo
            val visualTop = initialItemOffset + draggingOffset
            val topGap = visualTop - layout.viewportStartOffset
            val bottomGap = layout.viewportEndOffset - (visualTop + draggedItemHeight)
            val speed = when {
                topGap < edgePx -> -max(3f, (edgePx - topGap) / edgePx * 24f)
                bottomGap < edgePx -> max(3f, (edgePx - bottomGap) / edgePx * 24f)
                else -> 0f
            }
            if (speed != 0f) {
                val scrolled = listState.scrollBy(speed)
                if (scrolled != 0f) {
                    // offsetFor already compensates the item's changing layout offset.
                    // Keep the finger's viewport anchor fixed while the list scrolls underneath it.
                    moveAcrossItems()
                }
            }
            delay(16)
        }
    }

    fun finish(): DragDrop? {
        val id = draggingId ?: return null
        val result = order?.takeIf { it != originalOrder }
        draggingId = null
        draggingOffset = 0f
        if (result == null) order = null
        return result?.let { DragDrop(id, it) }
    }

    fun cancel() {
        draggingId = null
        draggingOffset = 0f
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
        if (!enabled && state.draggingId != null) state.cancel()
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
