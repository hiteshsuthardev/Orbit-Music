package com.example.orbitmusic.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.orbitmusic.R
import com.example.orbitmusic.domain.model.QueueItem
import com.example.orbitmusic.domain.player.QueueState
import com.example.orbitmusic.ui.components.OrbitArtwork
import com.example.orbitmusic.ui.components.songSubtitle
import com.example.orbitmusic.ui.screens.playlist.formatLongDuration
import com.example.orbitmusic.ui.theme.orbit.BrandColors
import com.example.orbitmusic.ui.theme.orbit.OnColor
import com.example.orbitmusic.ui.theme.orbit.OrbitDimens
import com.example.orbitmusic.ui.theme.orbit.OrbitShapes
import com.example.orbitmusic.ui.theme.orbit.OrbitSheet
import com.example.orbitmusic.ui.theme.orbit.OrbitTheme
import com.example.orbitmusic.ui.theme.orbit.floatingShadow

private const val QUEUE_TINT_AMOUNT = 0.34f
private const val QUEUE_TINT_FADE_END = 0.4f
private const val DRAG_TILT_DEGREES = 1.5f

/** Lazy-list items before the "Next up" rows: now-playing header, current row, next-up header. */
private const val HEADER_ITEMS_WITH_CURRENT = 3
private const val HEADER_ITEMS_WITHOUT_CURRENT = 1

/**
 * The play queue on an artwork-tinted sheet: "Now playing" (accent title + equalizer), then
 * "Next up" with count and total time. Drag a handle to reorder (the row lifts and tilts),
 * swipe left to reveal a red Remove area, tap to jump. Reorder/remove are also TalkBack
 * actions. Songs already played aren't listed; Previous still goes back to them.
 */
@Composable
fun QueueSheet(
    queue: QueueState,
    onDismiss: () -> Unit,
    onSkipTo: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    isPlaying: Boolean = true,
) {
    val colors = OrbitTheme.colors
    val current = queue.current
    val tint = rememberArtworkTint(current?.song?.thumbnailUrl)
    val topColor = lerp(colors.background, tint, QUEUE_TINT_AMOUNT)

    OrbitSheet(onDismissRequest = onDismiss, containerColor = topColor) {
        Column(
            Modifier.drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to topColor,
                        QUEUE_TINT_FADE_END to colors.background,
                        1f to colors.background,
                    ),
                )
            },
        ) {
            QueueContent(queue, isPlaying, onSkipTo, onRemove, onMove)
        }
    }
}

@Composable
internal fun ColumnScope.QueueContent(
    queue: QueueState,
    isPlaying: Boolean,
    onSkipTo: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    maxListHeight: Dp = 560.dp,
) {
    val colors = OrbitTheme.colors
    val listState = rememberLazyListState()
    val current = queue.current
    val upNext = queue.upNext
    val firstUpNextQueueIndex = if (current != null) queue.currentIndex + 1 else 0
    val headerItems = if (current != null) HEADER_ITEMS_WITH_CURRENT else HEADER_ITEMS_WITHOUT_CURRENT
    val upNextKeys = upNext.map { it.queueId }.toSet()
    val reorder = rememberReorderState(
        listState = listState,
        canDropOn = { key -> key in upNextKeys },
        onMove = { from, to -> onMove(from - headerItems + firstUpNextQueueIndex, to - headerItems + firstUpNextQueueIndex) },
    )
    val totalMs = upNext.sumOf { it.song.durationMs }
    val dragTilt = if (OrbitTheme.reduceMotion) 0f else DRAG_TILT_DEGREES

    Text(
        stringResource(R.string.queue_title),
        style = OrbitTheme.type.sectionTitle,
        color = colors.textPrimary,
        modifier = Modifier
            .padding(horizontal = OrbitDimens.ScreenPadding, vertical = 8.dp)
            .semantics { heading() },
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxListHeight),
    ) {
        if (current != null) {
            item(key = "nowPlayingHeader") { QueueSectionLabel(stringResource(R.string.queue_now_playing)) }
            item(key = current.queueId) {
                QueueRow(
                    item = current,
                    isCurrent = true,
                    isPlaying = isPlaying,
                    onClick = {},
                    trailing = {},
                )
            }
        }
        item(key = "nextUpHeader") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = OrbitDimens.ScreenPadding, end = OrbitDimens.ScreenPadding, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.queue_next_up),
                    style = OrbitTheme.type.sectionTitle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (upNext.isNotEmpty()) {
                    val count = pluralStringResource(R.plurals.track_count, upNext.size, upNext.size)
                    Text(
                        text = if (totalMs > 0) stringResource(R.string.playlist_meta, count, formatLongDuration(totalMs)) else count,
                        style = OrbitTheme.type.smallLabel,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (upNext.isEmpty()) {
            item(key = "nextUpEmpty") {
                Text(
                    stringResource(R.string.queue_next_up_empty),
                    style = OrbitTheme.type.secondary,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 12.dp),
                )
            }
        }
        itemsIndexed(upNext, key = { _, item -> item.queueId }) { index, item ->
            val queueIndex = firstUpNextQueueIndex + index
            val dragging = reorder.draggingKey == item.queueId
            val rowModifier = if (dragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = reorder.dragOffset
                        scaleX = 1.03f
                        scaleY = 1.03f
                        rotationZ = dragTilt
                    }
            } else {
                Modifier.animateItem()
            }
            SwipeToRemove(onRemove = { onRemove(item.queueId) }, modifier = rowModifier) {
                QueueRow(
                    item = item,
                    isCurrent = false,
                    isPlaying = false,
                    dragging = dragging,
                    opaque = true,
                    onClick = { onSkipTo(item.queueId) },
                    accessibilityActions = queueActions(
                        index = queueIndex,
                        firstMovable = firstUpNextQueueIndex,
                        lastIndex = queue.items.lastIndex,
                        onMove = onMove,
                        onRemove = { onRemove(item.queueId) },
                    ),
                    trailing = {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.cd_reorder),
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(OrbitDimens.MinTouchTarget)
                                .padding(12.dp)
                                .pointerInput(item.queueId) {
                                    detectDragGestures(
                                        onDragStart = { reorder.start(item.queueId) },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            reorder.drag(amount.y)
                                        },
                                        onDragEnd = reorder::end,
                                        onDragCancel = reorder::end,
                                    )
                                },
                        )
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun queueActions(
    index: Int,
    firstMovable: Int,
    lastIndex: Int,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
): List<CustomAccessibilityAction> {
    val moveUp = stringResource(R.string.queue_action_move_up)
    val moveDown = stringResource(R.string.queue_action_move_down)
    val remove = stringResource(R.string.queue_action_remove)
    return buildList {
        if (index > firstMovable) add(CustomAccessibilityAction(moveUp) { onMove(index, index - 1); true })
        if (index < lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(index, index + 1); true })
        add(CustomAccessibilityAction(remove) { onRemove(); true })
    }
}

@Composable
private fun QueueSectionLabel(text: String) {
    Text(
        text = text,
        style = OrbitTheme.type.smallLabel,
        color = OrbitTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = OrbitDimens.ScreenPadding, vertical = 4.dp),
    )
}

/** Swiping left reveals a red "Remove" area; letting go past the threshold removes the row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) currentOnRemove()
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(BrandColors.Destructive)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = OnColor.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.queue_remove), style = OrbitTheme.type.smallLabel, color = OnColor.Primary)
            }
        },
    ) { content() }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    dragging: Boolean = false,
    opaque: Boolean = false,
    accessibilityActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .floatingShadow(if (dragging) 16.dp else 0.dp, OrbitShapes.SmallArtwork, alpha = 0.5f)
            // Swipeable rows are opaque so the red Remove area only shows while swiping.
            .then(
                when {
                    dragging -> Modifier.background(colors.surfaceRaised)
                    opaque -> Modifier.background(colors.background)
                    else -> Modifier
                },
            )
            .clickable(enabled = !isCurrent, onClick = onClick)
            .semantics { if (accessibilityActions.isNotEmpty()) customActions = accessibilityActions }
            .heightIn(min = 64.dp)
            .padding(start = OrbitDimens.ScreenPadding, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitArtwork(
            url = item.song.thumbnailUrl,
            contentDescription = null,
            placeholderKey = item.song.videoId,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.song.title,
                style = OrbitTheme.type.songTitle,
                color = if (isCurrent) colors.accentText else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                songSubtitle(item.song),
                style = OrbitTheme.type.secondary,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Equalizer(animating = isPlaying, modifier = Modifier.padding(horizontal = 14.dp))
        }
        trailing()
    }
}

/** Three accent bars bouncing while playing; still when paused or animations are off. */
@Composable
private fun Equalizer(animating: Boolean, modifier: Modifier = Modifier) {
    val color = OrbitTheme.colors.accentText
    val animate = animating && !OrbitTheme.reduceMotion
    val description = stringResource(R.string.cd_now_playing)
    Row(
        modifier = modifier
            .height(16.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (animate) {
            val transition = rememberInfiniteTransition(label = "equalizer")
            listOf(0, 180, 360).forEach { delay ->
                val level by transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse, StartOffset(delay)),
                    label = "bar",
                )
                EqualizerBar(level, color)
            }
        } else {
            listOf(0.5f, 0.9f, 0.65f).forEach { EqualizerBar(it, color) }
        }
    }
}

@Composable
private fun EqualizerBar(level: Float, color: Color) {
    Box(
        Modifier
            .width(3.dp)
            .fillMaxHeight(level)
            .clip(OrbitShapes.Pill)
            .background(color),
    )
}

/**
 * Minimal drag-to-reorder over lazy-list indices. Items swap live while dragging (only onto
 * keys accepted by [canDropOn]); the dragged row follows the finger via [dragOffset].
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val canDropOn: (Any) -> Boolean,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var draggingIndex = -1

    fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        draggingIndex = info.index
        dragOffset = 0f
    }

    fun drag(delta: Float) {
        if (draggingKey == null) return
        dragOffset += delta
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.index == draggingIndex } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = visible.firstOrNull {
            it.index != draggingIndex && canDropOn(it.key) && center >= it.offset && center <= it.offset + it.size
        } ?: return
        onMove(draggingIndex, target.index)
        // Keep the row under the finger after it changes slot.
        dragOffset += current.offset - target.offset
        draggingIndex = target.index
    }

    fun end() {
        draggingKey = null
        dragOffset = 0f
        draggingIndex = -1
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canDropOn: (Any) -> Boolean,
    onMove: (Int, Int) -> Unit,
): ReorderState {
    val currentCanDrop by rememberUpdatedState(canDropOn)
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) {
        ReorderState(listState, { key -> currentCanDrop(key) }) { from, to -> currentOnMove(from, to) }
    }
}
