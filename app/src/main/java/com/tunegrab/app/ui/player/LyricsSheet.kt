package com.tunegrab.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.library.SongUi
import com.tunegrab.app.player.LyricsRepository
import com.tunegrab.app.player.SongLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data object NotFound : LyricsUiState
    data class Found(val lyrics: SongLyrics) : LyricsUiState
}

/**
 * Karaoke-style lyrics sheet for the current track, fetched from LRCLIB.
 * The current line renders large and bright, past lines fade down, and
 * upcoming lines sit at half opacity; the list auto-scrolls to keep the
 * active line near the top third. Tapping a line seeks to it.
 * Unsynced lyrics fall back to plain scrolling text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    track: SongUi?,
    positionMs: StateFlow<Long>,
    onDismiss: () -> Unit,
    /** Jump playback to a timestamp (ms); wired to tap-a-line-to-seek. */
    onSeekTo: (Long) -> Unit = {},
) {
    var state by remember { mutableStateOf<LyricsUiState>(LyricsUiState.Loading) }

    LaunchedEffect(track?.queueKey) {
        state = LyricsUiState.Loading
        val t = track
        state = if (t == null) {
            LyricsUiState.NotFound
        } else {
            val found = withContext(Dispatchers.IO) {
                runCatching { LyricsRepository.get(t.artist, t.title) }.getOrNull()
            }
            if (found != null) LyricsUiState.Found(found) else LyricsUiState.NotFound
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .fillMaxWidth(),
        ) {
            val headerTrack = track
            if (headerTrack != null) {
                Text(
                    headerTrack.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    headerTrack.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                )
            }
            when (val s = state) {
                LyricsUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                LyricsUiState.NotFound -> Text(
                    "No lyrics found for this song.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                )

                is LyricsUiState.Found -> if (s.lyrics.synced) {
                    KaraokeLyrics(
                        lines = s.lyrics.lines,
                        positionMs = positionMs,
                        onSeekTo = onSeekTo,
                    )
                } else {
                    Text(
                        s.lyrics.plainText.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5,
                        ),
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokeLyrics(
    lines: List<com.tunegrab.app.player.LyricLine>,
    positionMs: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
) {
    val pos by positionMs.collectAsState()
    val listState = rememberLazyListState()
    val currentIndex = lines.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)

    LaunchedEffect(currentIndex) {
        runCatching {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 64.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            val isPast = index < currentIndex
            val targetAlpha = when {
                isCurrent -> 1f
                isPast -> 0.38f
                else -> 0.6f
            }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "lyricAlpha",
            )
            val color by animateColorAsState(
                targetValue = if (isCurrent) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "lyricColor",
            )
            Text(
                line.text,
                style = if (isCurrent) {
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                    )
                },
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .clickable { onSeekTo(line.timeMs) }
                    .padding(horizontal = 24.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
