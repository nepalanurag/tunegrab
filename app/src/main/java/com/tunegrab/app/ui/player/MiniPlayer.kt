package com.tunegrab.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mini player: a 72dp bar pinned directly above the bottom navigation,
 * in the same visual language as the rest of the app. 48dp rounded
 * artwork, single-line title/artist, play/pause + next, and a thin
 * progress line along the bottom edge.
 *
 * Tapping the bar opens the full player; swiping horizontally changes
 * tracks (left -> next, right -> previous) with a spring-back animation.
 */
@Composable
fun MiniPlayer(
    controller: PlayerUiController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by controller.current.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()

    val track = current ?: return
    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val maxDragPx = with(density) { 110.dp.toPx() }
    val thresholdPx = with(density) { 56.dp.toPx() }

    // Re-center if the track changes underneath a drag.
    LaunchedEffect(track.queueKey) { dragOffset = 0f }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragOffset = (dragOffset + delta).coerceIn(-maxDragPx, maxDragPx)
                },
                onDragStopped = {
                    scope.launch {
                        when {
                            dragOffset < -thresholdPx -> controller.next()
                            dragOffset > thresholdPx -> controller.previous()
                        }
                        Animatable(dragOffset).animateTo(
                            0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) { dragOffset = value }
                    }
                },
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(72.dp)
                    .padding(horizontal = 12.dp)
                    .clickable(onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(
                    artworkKey = track.artworkKey,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentDescription = "Album art",
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { controller.togglePlayPause() }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = { controller.next() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            val progress =
                if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f)
                else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

/** Hairline divider so the bar reads as separate from the nav below it. */
@Composable
fun MiniPlayerDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Preview(showBackground = true)
@Composable
private fun MiniPlayerPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Box(Modifier.padding(8.dp)) {
            MiniPlayer(controller = PreviewPlayerUiController(), onExpand = {})
        }
    }
}
