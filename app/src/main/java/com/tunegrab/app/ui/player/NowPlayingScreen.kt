package com.tunegrab.app.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tunegrab.app.BuildConfig
import com.tunegrab.app.library.FavoritesStore
import com.tunegrab.app.ui.components.ProBadge
import com.tunegrab.app.ui.library.SongUi
import com.tunegrab.app.ui.theme.TuneGrabTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Full-screen Now Playing, in one of three themes:
 * - [NpTheme.CLEAN]: Yuma-style full-bleed blurred-art player with glass
 *   transport capsule, quality pill, and dual up-swipe gestures.
 * - [NpTheme.BLUR_ARTWORK]: full-bleed blurred art background (Pro).
 * - [NpTheme.MINIMAL]: text-focused with a thin progress line (Pro).
 *
 * The theme is gated through [themeFor]: non-Pro users always see CLEAN
 * regardless of [theme]. All themes share the seek slider, transport
 * controls, shuffle/repeat toggles, and the swipe-to-remove queue sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    controller: PlayerUiController,
    theme: NpTheme,
    isPro: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** e.g. "FLAC · 1565 kb/s · 48.0 kHz"; shown under the seekbar. */
    audioInfoLine: String? = null,
    onShowDetails: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onDeleteCurrent: () -> Unit = {},
    onGoToArtist: () -> Unit = {},
    /** Saves the current queue (e.g. a Radio run) as a playlist. */
    onSaveRadio: () -> Unit = {},
    /** Downloads a streaming track; no-op for local tracks. */
    onDownloadSong: (SongUi) -> Unit = {},
    /** Backs the heart button; null hides it (e.g. previews). */
    favoritesStore: FavoritesStore? = null,
) {
    val effectiveTheme = themeFor(isPro, theme)
    var queueOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    val sleepUntilMs by controller.sleepUntilMs.collectAsState()
    val current by controller.current.collectAsState()
    val scope = rememberCoroutineScope()
    val favorites by if (favoritesStore != null) {
        favoritesStore.favorites.collectAsState()
    } else {
        remember { mutableStateOf(emptySet()) }
    }
    val remoteFavorites by if (favoritesStore != null) {
        favoritesStore.remoteFavorites.collectAsState()
    } else {
        remember { mutableStateOf(emptySet()) }
    }
    val track = current
    val isFavorite = track?.let { t ->
        t.remoteVideoId?.let { it in remoteFavorites } ?: (t.id in favorites)
    } == true
    val remoteLoading by controller.remoteLoading.collectAsState()
    val isRadio by controller.isRadio.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            // Explicit theme background: without this the Activity's light
            // window background shows through and dark-mode (light) content
            // colors become unreadable.
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (remoteLoading) {
            // The stream's audio URL is being resolved.
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )
        }
        when (effectiveTheme) {
            NpTheme.CLEAN -> MaroonNowPlaying(
                controller = controller,
                audioInfoLine = audioInfoLine,
                isFavorite = isFavorite,
                showFavorite = favoritesStore != null && current != null,
                onToggleFavorite = {
                    current?.let { t ->
                        scope.launch {
                            val videoId = t.remoteVideoId
                            if (videoId != null) favoritesStore?.toggleRemote(
                                videoId = videoId,
                                title = t.title,
                                artist = t.artist,
                                artworkKey = t.artworkKey,
                            )
                            else favoritesStore?.toggle(t.id)
                        }
                    }
                },
                onQueueClick = { queueOpen = true },
                onMoreClick = { moreOpen = true },
                onOpenLyrics = { lyricsOpen = true },
                onClose = onClose,
            )
            NpTheme.BLUR_ARTWORK -> BlurNowPlaying(
                controller,
                onQueueClick = { queueOpen = true },
                onMoreClick = { moreOpen = true },
                audioInfoLine = audioInfoLine,
            )
            NpTheme.MINIMAL -> MinimalNowPlaying(
                controller,
                onQueueClick = { queueOpen = true },
                onMoreClick = { moreOpen = true },
                audioInfoLine = audioInfoLine,
            )
        }
        if (queueOpen) {
            QueueSheet(
                controller = controller,
                onDismiss = { queueOpen = false },
                onDownload = onDownloadSong,
            )
        }
        if (moreOpen) {
            PlayerMoreSheet(
                track = current,
                isRemote = current?.isRemote == true,
                onDismiss = { moreOpen = false },
                onDetails = { moreOpen = false; onShowDetails() },
                onAddToPlaylist = { moreOpen = false; onAddToPlaylist() },
                onDelete = { moreOpen = false; onDeleteCurrent() },
                onLyrics = { moreOpen = false; lyricsOpen = true },
                onGoToArtist = { moreOpen = false; onGoToArtist() },
                isRadio = isRadio,
                onStartRadio = {
                    moreOpen = false
                    current?.let { controller.startRadio(it) }
                },
                onStopRadio = { moreOpen = false; controller.stopRadio() },
                onSaveRadio = { moreOpen = false; onSaveRadio() },
                onSleepTimer = { moreOpen = false; sleepDialogOpen = true },
                onDownload = { moreOpen = false; current?.let(onDownloadSong) },
                sleepUntilMs = sleepUntilMs,
            )
        }
        if (lyricsOpen) {
            LyricsSheet(
                track = current,
                positionMs = controller.positionMs,
                onDismiss = { lyricsOpen = false },
                onSeekTo = { controller.seekTo(it) },
            )
        }
        if (sleepDialogOpen) {
            SleepTimerDialog(
                sleepUntilMs = sleepUntilMs,
                onDismiss = { sleepDialogOpen = false },
                onSelect = { minutes ->
                    controller.setSleepTimer(minutes)
                    sleepDialogOpen = false
                },
            )
        }
    }
}

/**
 * Horizontal swipe on the artwork (or the player surface) skips tracks:
 * swipe left -> next, swipe right -> previous. Simple threshold version,
 * kept for the alternate (non-default) themes.
 */
private fun Modifier.trackSwipeable(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier = pointerInput(onNext, onPrevious) {
    val thresholdPx = with(density) { 64.dp.toPx() }
    var totalX = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalX = 0f },
        onDragCancel = { totalX = 0f },
        onDragEnd = {
            when {
                totalX <= -thresholdPx -> onNext()
                totalX >= thresholdPx -> onPrevious()
            }
        },
        onHorizontalDrag = { _, dragAmount -> totalX += dragAmount },
    )
}

// ------------------------------------------------------------------- BLUR ---

@Composable
private fun BlurNowPlaying(
    controller: PlayerUiController,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit,
    audioInfoLine: String?,
) {
    val current by controller.current.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .trackSwipeable(
                onNext = { controller.next() },
                onPrevious = { controller.previous() },
            )
    ) {
        // Full-bleed art, scaled up past the blur edges, under a scrim.
        ArtworkImage(
            artworkKey = current?.artworkKey,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.2f
                    scaleY = 1.2f
                    alpha = 0.9f
                },
            contentDescription = null,
        )
        Box(
            Modifier
                .fillMaxSize()
                .backgroundScrim(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TrackTitleBlock(current, light = true)
            Spacer(Modifier.height(16.dp))
            TransportBlock(
                controller,
                onQueueClick,
                light = true,
                onMoreClick = onMoreClick,
                infoLine = audioInfoLine,
            )
        }
    }
}

@Composable
private fun Modifier.backgroundScrim(): Modifier =
    this.then(background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)))

// ---------------------------------------------------------------- MINIMAL ---

@Composable
private fun MinimalNowPlaying(
    controller: PlayerUiController,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit,
    audioInfoLine: String?,
) {
    val current by controller.current.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .trackSwipeable(
                onNext = { controller.next() },
                onPrevious = { controller.previous() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            current?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            current?.artist.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDurationMs(position), style = MaterialTheme.typography.bodySmall)
            Text(formatDurationMs(duration), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        TransportBlock(
            controller,
            onQueueClick,
            onMoreClick = onMoreClick,
            infoLine = audioInfoLine,
        )
    }
}

// ----------------------------------------------------------------- Shared ---

@Composable
private fun TrackTitleBlock(track: SongUi?, light: Boolean = false) {
    val titleColor = if (light) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val subColor = if (light) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            track?.title ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            color = titleColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(track?.artist, track?.album).joinToString(" • ").ifEmpty { "" },
            style = MaterialTheme.typography.bodyLarge,
            color = subColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TransportBlock(
    controller: PlayerUiController,
    onQueueClick: () -> Unit,
    light: Boolean = false,
    onMoreClick: () -> Unit = {},
    /** e.g. "FLAC · 1565 kb/s · 48.0 kHz"; shown under the seekbar. */
    infoLine: String? = null,
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val position by controller.positionMs.collectAsState()
    val duration by controller.durationMs.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val range = max(1f, duration.toFloat())
    val sliderValue = (if (dragging) dragValue else position.toFloat()).coerceIn(0f, range)

    val tint = if (light) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDurationMs(if (dragging) dragValue.toLong() else position),
                style = MaterialTheme.typography.bodySmall,
                color = tint,
                modifier = Modifier.width(44.dp),
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                valueRange = 0f..range,
                onValueChangeFinished = {
                    controller.seekTo(dragValue.toLong())
                    dragging = false
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                formatDurationMs(duration),
                style = MaterialTheme.typography.bodySmall,
                color = tint,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffle) MaterialTheme.colorScheme.primary else tint,
                )
            }
            IconButton(onClick = { controller.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = tint, modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = { controller.togglePlayPause() },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = tint,
                    modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = { controller.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = tint, modifier = Modifier.size(36.dp))
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
                    tint = if (repeatMode == 0) tint.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (infoLine != null) {
                Text(
                    infoLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                )
            }
            IconButton(onClick = onQueueClick) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = tint)
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = tint)
            }
        }
    }
}

// ------------------------------------------------------------------ Queue ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMoreSheet(
    track: SongUi?,
    isRemote: Boolean = false,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onLyrics: () -> Unit,
    onGoToArtist: () -> Unit = {},
    isRadio: Boolean = false,
    onStartRadio: () -> Unit = {},
    onStopRadio: () -> Unit = {},
    onSaveRadio: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onDownload: () -> Unit = {},
    /** Active sleep timer deadline, if any; shown as remaining time on its row. */
    sleepUntilMs: Long? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            track?.let {
                ListItem(
                    headlineContent = {
                        Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            listOf(it.artist, it.album)
                                .filter { s -> s.isNotBlank() }
                                .joinToString(" • "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        ArtworkImage(
                            artworkKey = it.artworkKey,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentDescription = "Album art",
                        )
                    },
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column {
                    // Streaming tracks can be downloaded for offline
                    // listening; local tracks are already on the device.
                    // Direct build only: the Play build has no downloader.
                    if (BuildConfig.INCLUDE_DOWNLOADER && isRemote && track?.localContentUri == null) {
                        MoreRow(Icons.Filled.Download, "Download", onDownload)
                    }
                    // Streaming tracks have no local file to detail or delete.
                    if (!isRemote) {
                        MoreRow(Icons.Filled.Info, "Details", onDetails)
                    }
                    MoreRow(Icons.Filled.MusicNote, "Lyrics", onLyrics)
                    MoreRow(Icons.Filled.PlaylistAdd, "Add to playlist", onAddToPlaylist)
                    // Direct build only: sleep timer and Radio are Pro
                    // features, and the Play build has no Pro tier.
                    if (BuildConfig.INCLUDE_DOWNLOADER) {
                        MoreRow(
                            Icons.Filled.NightsStay,
                            "Sleep timer",
                            onSleepTimer,
                            trailingText = sleepUntilMs?.let { deadline ->
                                val remainingMs =
                                    (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                                if (remainingMs < 60_000L) "<1 min" else "${remainingMs / 60_000L} min"
                            },
                        )
                    }
                    if (BuildConfig.INCLUDE_DOWNLOADER) {
                        if (isRadio) {
                            MoreRow(Icons.Filled.Stop, "Stop radio", onStopRadio)
                            MoreRow(Icons.Filled.Save, "Save as playlist", onSaveRadio)
                        } else {
                            MoreRow(Icons.Filled.Radio, "Start radio", onStartRadio)
                        }
                    } else {
                        // Play build: Radio is a locked Pro teaser here.
                        // Tapping it fires onStartRadio, which shows the
                        // Pro upsell dialog instead of starting radio.
                        MoreRow(
                            Icons.Filled.Radio,
                            "Start radio",
                            onClick = onStartRadio,
                            showProBadge = true,
                        )
                    }
                    if (track?.artist?.isNotBlank() == true) {
                        MoreRow(
                            Icons.Filled.Person,
                            "Go to artist",
                            onGoToArtist,
                            // Play build: the online artist screen is a
                            // locked Pro teaser here.
                            showProBadge = !BuildConfig.INCLUDE_DOWNLOADER,
                        )
                    }
                    if (!isRemote) {
                        MoreRow(
                            Icons.Filled.Delete,
                            "Delete from device",
                            onDelete,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    trailingText: String? = null,
    showProBadge: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(
                label,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingContent = when {
            showProBadge -> { { ProBadge() } }
            trailingText != null -> {
                {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            else -> null
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    controller: PlayerUiController,
    onDismiss: () -> Unit,
    onDownload: (SongUi) -> Unit = {},
) {
    val queue by controller.queue.collectAsState()
    val current by controller.current.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val isRadio by controller.isRadio.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Queue",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                "${queue.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isRadio) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Radio is on. Similar songs will keep playing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { controller.stopRadio() }) {
                    Text("Stop")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // queueKey qualified with the index: the same song can appear
            // twice, and Lazy needs unique keys.
            itemsIndexed(queue, key = { index, s -> "${s.queueKey}@$index" }) { index, song ->
                // NOTE: this project's material3 has the 1.4-style API: no
                // onDismiss param on SwipeToDismissBox; dismissal goes through
                // confirmValueChange on the state. The song queue key (not the
                // list index) is captured so the removal stays correct even
                // if the queue shifts under the swipe.
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            val idx = controller.queue.value
                                .indexOfFirst { it.queueKey == song.queueKey }
                            if (idx >= 0) controller.removeFromQueue(idx)
                        }
                        true
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        // The swipe-away delete affordance may only exist while the row is
                        // off its rest position. QueueRow is transparent, so an always-on
                        // background icon shows through underneath the drag handle at rest,
                        // looking like a second delete button overlapping it.
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                            dismissState.targetValue != SwipeToDismissBoxValue.Settled
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                ) {
                    QueueRow(
                        song = song,
                        isCurrent = song.queueKey == current?.queueKey,
                        isPlaying = isPlaying,
                        onClick = { controller.playQueueIndex(index) },
                        onDownload = { onDownload(song) },
                        onRemove = {
                            val idx = controller.queue.value
                                .indexOfFirst { it.queueKey == song.queueKey }
                            if (idx >= 0) controller.removeFromQueue(idx)
                        },
                        onMove = { delta ->
                            val to = (index + delta).coerceIn(0, queue.size - 1)
                            if (to != index) controller.moveInQueue(index, to)
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/**
 * Yuma-style queue row: 52dp rounded artwork, title/artist/duration, an
 * optional download button for streams, a remove button, and a drag
 * handle on the right. Long-press the handle and drag vertically to
 * reorder (one row height per step). The playing row gets a soft highlight
 * with animated equalizer bars over its artwork.
 */
@Composable
private fun QueueRow(
    song: SongUi,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit = {},
    onRemove: () -> Unit = {},
    onMove: (delta: Int) -> Unit,
) {
    var accumulated by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkImage(
                artworkKey = song.artworkKey,
                modifier = Modifier.fillMaxSize(),
                contentDescription = null,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPlaying) EqBars(tint = Color.White)
                    else Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} • ${formatDurationMs(song.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Row actions sit side by side in the Row so they can never
        // overlap: download (streams only), remove, then the drag handle.
        if (song.isRemote && song.localContentUri == null) {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove from queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Drag handle: long-press and drag vertically to reorder.
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
                .pointerInput(density) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { accumulated = 0f },
                        onDragCancel = { accumulated = 0f },
                        onDragEnd = { accumulated = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount.y
                            val stepPx = with(density) { 64.dp.toPx() }
                            when {
                                accumulated > stepPx / 2 -> {
                                    onMove(1)
                                    accumulated = 0f
                                }
                                accumulated < -stepPx / 2 -> {
                                    onMove(-1)
                                    accumulated = 0f
                                }
                            }
                        },
                    )
                },
        )
    }
}

/** Three animated equalizer bars, drawn on a canvas. */
@Composable
private fun EqBars(tint: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val f1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq1",
    )
    val f2 by transition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(430, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq2",
    )
    val f3 by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(610, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eq3",
    )
    Canvas(modifier = modifier.size(22.dp)) {
        val barW = size.width / 5f
        val gap = size.width / 10f
        val fracs = listOf(f1, f2, f3)
        fracs.forEachIndexed { i, f ->
            val h = size.height * f
            drawRoundRect(
                color = tint,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

// ---------------------------------------------------------------- Preview ---

/** Sleep timer picker: off or 5/10/15/30/45/60 minutes. */
@Composable
private fun SleepTimerDialog(
    sleepUntilMs: Long?,
    onDismiss: () -> Unit,
    onSelect: (minutes: Int) -> Unit,
) {
    val options = listOf(0, 5, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (sleepUntilMs != null) {
                    // Live countdown: ticks once a second while the dialog is open.
                    var nowMs by remember(sleepUntilMs) {
                        mutableStateOf(System.currentTimeMillis())
                    }
                    LaunchedEffect(sleepUntilMs) {
                        while (true) {
                            delay(1_000L)
                            nowMs = System.currentTimeMillis()
                        }
                    }
                    val remainingMs = (sleepUntilMs - nowMs).coerceAtLeast(0L)
                    Text(
                        if (remainingMs < 60_000L) "Less than a minute left"
                        else "${remainingMs / 60_000L} min left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                options.forEach { minutes ->
                    Text(
                        text = if (minutes == 0) "Off" else "$minutes minutes",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** Cheap in-memory fake of [PlayerUiController] so previews work. */
internal class PreviewPlayerUiController : PlayerUiController {
    private val sample = List(4) {
        SongUi(
            id = it.toLong(),
            title = "Preview Track ${it + 1}",
            artist = "Preview Artist",
            album = "Preview Album",
            durationMs = 200_000L,
            artworkKey = null,
        )
    }
    private val _current = MutableStateFlow<SongUi?>(sample[0])
    private val _isPlaying = MutableStateFlow(true)
    private val _position = MutableStateFlow(74_000L)
    private val _duration = MutableStateFlow(200_000L)
    private val _queue = MutableStateFlow(sample)
    private val _shuffle = MutableStateFlow(false)
    private val _repeat = MutableStateFlow(0)
    private val _remoteLoading = MutableStateFlow(false)
    private val _isRadio = MutableStateFlow(false)

    override val current: StateFlow<SongUi?> = _current.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val positionMs: StateFlow<Long> = _position.asStateFlow()
    override val durationMs: StateFlow<Long> = _duration.asStateFlow()
    override val queue: StateFlow<List<SongUi>> = _queue.asStateFlow()
    override val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    override val repeatMode: StateFlow<Int> = _repeat.asStateFlow()
    override val remoteLoading: StateFlow<Boolean> = _remoteLoading.asStateFlow()
    override val isRadio: StateFlow<Boolean> = _isRadio.asStateFlow()
    private val _sleepUntilMs = MutableStateFlow<Long?>(null)
    override val sleepUntilMs: StateFlow<Long?> = _sleepUntilMs.asStateFlow()
    override fun setSleepTimer(minutes: Int) {
        _sleepUntilMs.value =
            if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else null
    }
    override fun startRadio(song: SongUi) { _isRadio.value = true }
    override fun stopRadio() { _isRadio.value = false }

    override fun togglePlayPause() { _isPlaying.value = !_isPlaying.value }
    override fun seekTo(ms: Long) { _position.value = ms }
    override fun next() { _position.value = 0L }
    override fun previous() { _position.value = 0L }
    override fun toggleShuffle() { _shuffle.value = !_shuffle.value }
    override fun cycleRepeat() { _repeat.value = (_repeat.value + 1) % 3 }
    override fun playQueue(songs: List<SongUi>, index: Int) {
        _queue.value = songs
        _current.value = songs.getOrNull(index)
    }
    override fun playQueueIndex(index: Int) {
        _current.value = _queue.value.getOrNull(index)
    }
    override fun updateTrackArtist(queueKey: String, artist: String) {
        _queue.value = _queue.value.map {
            if (it.queueKey == queueKey) it.copy(artist = artist) else it
        }
        if (_current.value?.queueKey == queueKey) {
            _current.value = _current.value?.copy(artist = artist)
        }
    }
    override fun enqueue(songs: List<SongUi>) {
        _queue.value = _queue.value + songs
    }
    override fun playNext(songs: List<SongUi>) {
        val q = _queue.value.toMutableList()
        val cur = q.indexOf(_current.value).coerceAtLeast(0)
        q.addAll(if (q.isEmpty()) 0 else cur + 1, songs)
        _queue.value = q
    }
    override fun removeFromQueue(index: Int) {
        _queue.value = _queue.value.filterIndexed { i, _ -> i != index }
    }
    override fun moveInQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from in q.indices && to in q.indices) {
            q.add(to, q.removeAt(from))
            _queue.value = q
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NowPlayingCleanPreview() {
    TuneGrabTheme {
        NowPlayingScreen(
            controller = PreviewPlayerUiController(),
            theme = NpTheme.CLEAN,
            isPro = false,
            onClose = {},
        )
    }
}
