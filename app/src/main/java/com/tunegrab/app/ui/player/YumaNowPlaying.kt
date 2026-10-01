package com.tunegrab.app.ui.player

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

/**
 * Now Playing in the YumaPlayer-inspired glass style:
 *
 * - Full-bleed blurred artwork background under gradient scrims.
 * - Top bar: glass collapse button, "NOW PLAYING" label, glass more button.
 * - Big bold title, artist, heart on the right.
 * - Thin rounded seek bar with tap/drag, elapsed/total times.
 * - Centered glass pill with the audio quality line.
 * - Glass transport capsule (prev, big white play, next) with shuffle and
 *   repeat flanking it.
 * - Bottom row: lyrics button (left), queue button (right).
 *
 * Gestures on the artwork surface: horizontal swipe changes tracks;
 * swipe up on the left half opens lyrics, on the right half the queue.
 */
@Composable
fun YumaNowPlaying(
    controller: PlayerUiController,
    audioInfoLine: String?,
    isFavorite: Boolean,
    showFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit,
    onOpenLyrics: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by controller.current.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .playerSwipeGestures(
                onNext = { controller.next() },
                onPrevious = { controller.previous() },
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = onQueueClick,
            ),
    ) {
        // Full-bleed artwork, scaled past the blur edges.
        ArtworkImage(
            artworkKey = current?.artworkKey,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(10.dp)
                    } else {
                        Modifier
                    },
                ),
            contentDescription = null,
        )
        // Scrims: top protection for the bar, heavy bottom for legibility.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.28f to Color.Black.copy(alpha = 0.05f),
                        0.55f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GlassCircleButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    description = "Close player",
                    onClick = onClose,
                )
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 3.sp,
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                )
                GlassCircleButton(
                    icon = Icons.Filled.MoreVert,
                    description = "More options",
                    onClick = onMoreClick,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        current?.artist.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showFavorite) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite
                            else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Unlike" else "Like",
                            tint = if (isFavorite) Color(0xFFFF6B81) else Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ThinSeekBar(
                positionMs = position,
                durationMs = duration,
                onSeekTo = { controller.seekTo(it) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDurationMs(position),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    formatDurationMs(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (audioInfoLine != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(
                            0.5.dp,
                            Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        audioInfoLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { controller.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffle) Color.White
                        else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(26.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(48.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(
                            0.5.dp,
                            Color.White.copy(alpha = 0.28f),
                            RoundedCornerShape(48.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    IconButton(onClick = { controller.previous() }) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Surface(
                        onClick = { controller.togglePlayPause() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(68.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    IconButton(onClick = { controller.next() }) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(onClick = { controller.cycleRepeat() }) {
                    val (icon, desc) = when (repeatMode) {
                        2 -> Icons.Filled.RepeatOne to "Repeat one"
                        1 -> Icons.Filled.Repeat to "Repeat all"
                        else -> Icons.Filled.Repeat to "Repeat off"
                    }
                    Icon(
                        icon,
                        contentDescription = desc,
                        tint = if (repeatMode == 0) Color.White.copy(alpha = 0.45f)
                        else Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    icon = Icons.Filled.MusicNote,
                    description = "Lyrics",
                    onClick = onOpenLyrics,
                )
                GlassCircleButton(
                    icon = Icons.Filled.QueueMusic,
                    description = "Queue",
                    onClick = onQueueClick,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Translucent circle button with a hairline border, for glass surfaces. */
@Composable
internal fun GlassCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.28f)),
        modifier = modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Thin rounded seek bar (4dp track) with tap-to-seek and drag scrubbing.
 * Drawn by hand so it matches the glass player; not a Material slider.
 */
@Composable
internal fun ThinSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.28f),
    progressColor: Color = Color.White,
) {
    val range = max(1L, durationMs)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val baseFraction = (positionMs.toFloat() / range).coerceIn(0f, 1f)
    val fraction = (if (dragging) dragFraction else baseFraction).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(widthPx, range) {
                detectTapGestures(
                    onTap = { offset ->
                        if (widthPx > 0) {
                            onSeekTo(
                                ((offset.x / widthPx) * range).toLong()
                                    .coerceIn(0L, range),
                            )
                        }
                    },
                )
            }
            .pointerInput(widthPx, range) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        if (widthPx > 0) {
                            dragFraction =
                                (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onDragCancel = { dragging = false },
                    onDragEnd = {
                        onSeekTo((dragFraction * range).toLong().coerceIn(0L, range))
                        dragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (widthPx > 0) {
                            dragFraction = (change.position.x / widthPx.toFloat())
                                .coerceIn(0f, 1f)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(progressColor),
            )
        }
    }
}

/**
 * Artwork-surface gestures: horizontal swipe changes tracks, swipe up on
 * the left half opens lyrics, swipe up on the right half opens the queue.
 */
private fun Modifier.playerSwipeGestures(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
): Modifier = pointerInput(onNext, onPrevious, onOpenLyrics, onOpenQueue) {
    var total = Offset.Zero
    var startX = 0f
    val thresholdPx = 64.dp.toPx()
    val widthPx = size.width.toFloat()
    detectDragGestures(
        onDragStart = { offset ->
            total = Offset.Zero
            startX = offset.x
        },
        onDragCancel = { total = Offset.Zero },
        onDragEnd = {
            when {
                total.y < -thresholdPx && abs(total.y) > abs(total.x) ->
                    if (startX < widthPx / 2) onOpenLyrics() else onOpenQueue()
                total.x <= -thresholdPx -> onNext()
                total.x >= thresholdPx -> onPrevious()
            }
            total = Offset.Zero
        },
        onDrag = { change, dragAmount ->
            change.consume()
            total += dragAmount
        },
    )
}
