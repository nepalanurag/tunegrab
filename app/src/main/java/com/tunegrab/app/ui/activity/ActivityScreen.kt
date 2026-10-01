package com.tunegrab.app.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tunegrab.app.library.ListeningStatsStore
import com.tunegrab.app.ui.components.ArtistRow
import com.tunegrab.app.ui.components.EmptyState
import com.tunegrab.app.ui.components.SectionHeader
import com.tunegrab.app.ui.components.SongRow
import com.tunegrab.app.ui.components.toSongUi
import com.tunegrab.app.ui.library.LibraryDataSource
import com.tunegrab.app.ui.library.SongUi

/**
 * Activity screen: the app's memory. Listening stats, recently played,
 * top songs and artists, plus the direct build's download queue and
 * history (injected via [downloadSections] as lazy list sections).
 */
@Composable
fun ActivityScreen(
    dataSource: LibraryDataSource,
    statsStore: ListeningStatsStore,
    mediaGranted: Boolean,
    downloadSections: @Composable () -> Unit = {},
    onPlaySong: (SongUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats by statsStore.stats.collectAsState()
    var songCount by remember { mutableStateOf(0L) }

    LaunchedEffect(mediaGranted) {
        songCount = if (mediaGranted) {
            runCatching { dataSource.songCount() }.getOrDefault(0L)
        } else 0L
    }

    val recent = remember(stats) { statsStore.recentlyPlayed(10) }
    val topSongs = remember(stats) { statsStore.topSongs(10) }
    val topArtists = remember(stats) { statsStore.topArtists(10) }
    val totalPlays = remember(stats) { stats.sumOf { it.playCount } }
    val tracksPlayed = remember(stats) { stats.count { it.playCount > 0 } }
    val hasStats = stats.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        // Stat cards: real numbers, never placeholders.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    label = "Songs",
                    value = songCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Plays",
                    value = totalPlays.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Played",
                    value = tracksPlayed.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Flavor hook: direct build renders the live queue + history here.
        item {
            downloadSections()
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader(title = "Recently played") }
            itemsIndexed(
                recent,
                key = { _, s -> s.key },
            ) { _, stat ->
                val song = remember(stat) { stat.toSongUi() }
                SongRow(song = song, onClick = { onPlaySong(song) })
            }
        }

        if (topSongs.isNotEmpty()) {
            item { SectionHeader(title = "Top songs") }
            itemsIndexed(
                topSongs,
                key = { _, s -> s.key },
            ) { index, stat ->
                val song = remember(stat) { stat.toSongUi() }
                SongRow(
                    song = song,
                    onClick = { onPlaySong(song) },
                    leading = {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp),
                        )
                    },
                )
            }
        }

        if (topArtists.isNotEmpty()) {
            item { SectionHeader(title = "Top artists") }
            itemsIndexed(
                topArtists,
                key = { _, a -> a.artist },
            ) { _, artist ->
                ArtistRow(
                    name = artist.artist,
                    subtitle = "${artist.playCount} plays",
                    artworkKey = artist.artworkKey,
                    onClick = {},
                )
            }
        }

        if (!hasStats) {
            item {
                EmptyState(
                    icon = Icons.Filled.BarChart,
                    title = "Nothing here yet",
                    body = "Play some music and your listening activity " +
                        "will show up here.",
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
