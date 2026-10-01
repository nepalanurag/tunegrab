package com.tunegrab.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.components.ExpressiveSlider
import com.tunegrab.app.ui.components.SquirclePlayButton
import com.tunegrab.app.ui.components.TonalIconButton
import kotlin.math.abs

/**
 * Now Playing, rebuilt on the maroon theme:
 *
 * - Solid theme background with only a restrained darkened-artwork tint
 *   behind the top bar (no full-bleed blur).
 * - Large rounded artwork, codec badge (FLAC/MP3), expressive seek bar
 *   with a drag bubble, and a dominant squircle play button.
 * - Queue and lyrics open from the player; the sleep timer lives in the
 *   overflow sheet. Artwork gestures (swipe for tracks, swipe up for
 *   lyrics/queue) are kept.
 * - Landscape gets a side-by-side layout so the player fits the device.
 */
@Composable
fun MaroonNowPlaying(
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
    val sleepUntilMs by controller.sleepUntilMs.collectAsState()

    val codec = remember(audioInfoLine) { codecBadge(audioInfoLine) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .playerSwipeGestures(
                onNext = { controller.next() },
                onPrevious = { controller.previous() },
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = onQueueClick,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Solid theme background throughout: no artwork wash behind the
        // header (the tinted strip let the cover's embedded text show
        // through behind "NOW PLAYING" and read as overlapping art).
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        artworkKey = current?.artworkKey,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp)),
                        contentDescription = "Album art",
                    )
                    Spacer(Modifier.width(32.dp))
                    ControlsColumn(
                        controller = controller,
                        audioInfoLine = audioInfoLine,
                        codec = codec,
                        isFavorite = isFavorite,
                        showFavorite = showFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onQueueClick = onQueueClick,
                        onMoreClick = onMoreClick,
                        onOpenLyrics = onOpenLyrics,
                        onClose = onClose,
                        sleepActive = sleepUntilMs != null,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    TopBar(
                        onClose = onClose,
                        onMoreClick = onMoreClick,
                        sleepActive = sleepUntilMs != null,
                    )
                    Spacer(Modifier.height(8.dp))
                    ArtworkImage(
                        artworkKey = current?.artworkKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .aspectRatio(1f)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(28.dp)),
                        contentDescription = "Album art",
                    )
                    Spacer(Modifier.height(20.dp))
                    ControlsColumn(
                        controller = controller,
                        audioInfoLine = audioInfoLine,
                        codec = codec,
                        isFavorite = isFavorite,
                        showFavorite = showFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onQueueClick = onQueueClick,
                        onMoreClick = onMoreClick,
                        onOpenLyrics = onOpenLyrics,
                        onClose = onClose,
                        sleepActive = sleepUntilMs != null,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        compact = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onClose: () -> Unit,
    onMoreClick: () -> Unit,
    sleepActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Close player",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (sleepActive) {
            Icon(
                Icons.Filled.NightsStay,
                contentDescription = "Sleep timer on",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ControlsColumn(
    controller: PlayerUiController,
    audioInfoLine: String?,
    codec: String?,
    isFavorite: Boolean,
    showFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit,
    onOpenLyrics: () -> Unit,
    onClose: () -> Unit,
    sleepActive: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val current by controller.current.collectAsState()
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    val fraction = if (duration > 0) position.toFloat() / duration else 0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title row with heart.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = current?.artist ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Codec badge + quality line. The badge already names the codec,
        // so the line drops its leading codec word ("FLAC · 1576 kb/s"
        // shows as badge FLAC + "1576 kb/s", not "FLAC FLAC · 1576 kb/s").
        val qualityRemainder = remember(audioInfoLine, codec) {
            infoLineWithoutCodec(audioInfoLine, codec)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (codec != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = codec,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                    )
                }
            }
            if (qualityRemainder != null) {
                Text(
                    text = qualityRemainder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Seek bar with drag bubble.
        ExpressiveSlider(
            value = if (dragFraction.isNaN()) fraction else dragFraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                if (duration > 0 && !dragFraction.isNaN()) {
                    controller.seekTo((dragFraction * duration).toLong())
                }
                dragFraction = Float.NaN
            },
            formatTime = { f -> formatDurationMs((f * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMs(
                    if (dragFraction.isNaN()) position
                    else (dragFraction * duration).toLong()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDurationMs(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

        // Transport: shuffle, prev, hero play, next, repeat.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TonalIconButton(
                onClick = { controller.toggleShuffle() },
                icon = Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                selected = shuffle,
            )
            IconButton(onClick = { controller.previous() }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }
            SquirclePlayButton(
                playing = isPlaying,
                onClick = { controller.togglePlayPause() },
                size = 80.dp,
            )
            IconButton(onClick = { controller.next() }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
            }
            val (repeatIcon, repeatDesc) = when (repeatMode) {
                2 -> Icons.Filled.RepeatOne to "Repeat one"
                1 -> Icons.Filled.Repeat to "Repeat all"
                else -> Icons.Filled.Repeat to "Repeat off"
            }
            TonalIconButton(
                onClick = { controller.cycleRepeat() },
                icon = repeatIcon,
                contentDescription = repeatDesc,
                selected = repeatMode != 0,
            )
        }

        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

        // Lyrics + queue.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TonalIconButton(
                onClick = onOpenLyrics,
                icon = Icons.Filled.Lyrics,
                contentDescription = "Lyrics",
            )
            TonalIconButton(
                onClick = onQueueClick,
                icon = Icons.Filled.QueueMusic,
                contentDescription = "Queue",
            )
        }
    }
}

/** Codec badge from the audio info line, e.g. "FLAC · 1565 kb/s". */
private fun codecBadge(audioInfoLine: String?): String? {
    val first = audioInfoLine
        ?.split("·", " ", "|")
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.uppercase()
        ?: return null
    return when {
        first == "FLAC" -> "FLAC"
        first.contains("MP3") -> "MP3"
        first.contains("AAC") || first == "M4A" -> "AAC"
        first.contains("OPUS") -> "OPUS"
        first.contains("WAV") -> "WAV"
        first.contains("OGG") -> "OGG"
        else -> null
    }
}

/**
 * Audio info line with the leading codec word removed, since the codec
 * badge already shows it. "FLAC · 1576 kb/s · 48 kHz" with badge "FLAC"
 * becomes "1576 kb/s · 48 kHz". Returns null when nothing is left, or
 * when the line does not start with the badge's codec.
 */
private fun infoLineWithoutCodec(audioInfoLine: String?, codec: String?): String? {
    val line = audioInfoLine?.trim().orEmpty()
    if (line.isEmpty()) return null
    if (codec == null) return line
    val tokens = line.split("·", "|", "-")
    val first = tokens.firstOrNull()?.trim().orEmpty()
    val matches = first.equals(codec, ignoreCase = true) ||
        first.uppercase().contains(codec)
    if (!matches) return line
    return tokens.drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { null }
}

/**
 * Artwork-surface gestures: horizontal swipe changes tracks, swipe up on
 * the left half opens lyrics, on the right half the queue.
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
