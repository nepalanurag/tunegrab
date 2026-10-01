package com.tunegrab.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunegrab.app.library.ListeningStatsStore
import com.tunegrab.app.BuildConfig
import com.tunegrab.app.ui.components.AlbumCard
import com.tunegrab.app.ui.components.EmptyState
import com.tunegrab.app.ui.components.SectionHeader
import com.tunegrab.app.ui.components.SongRow
import com.tunegrab.app.ui.components.toSongUi
import com.tunegrab.app.ui.player.ArtworkImage
import com.tunegrab.app.ui.library.AlbumUi
import com.tunegrab.app.library.ArtistStat
import com.tunegrab.app.ui.library.GenreUi
import com.tunegrab.app.ui.library.LibraryDataSource
import com.tunegrab.app.ui.library.SongUi
import com.tunegrab.app.ui.library.SortOption
import java.util.Calendar

/**
 * Home: greeting, quick picks, mood chips backed by real genres, and
 * content shelves. Direct-only pieces (paste card, settings gear) are
 * gated by flags so the Play build stays clean.
 */
@Composable
fun HomeScreen(
    dataSource: LibraryDataSource,
    statsStore: ListeningStatsStore,
    onPlaySong: (SongUi) -> Unit,
    onPlaySongs: (List<SongUi>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    showPasteCard: Boolean,
    onPasteLink: (String) -> Unit,
    pasteCardExtra: @Composable () -> Unit,
    showSettingsGear: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats by statsStore.stats.collectAsState()
    var songs by remember { mutableStateOf<List<SongUi>>(emptyList()) }
    var genres by remember { mutableStateOf<List<GenreUi>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumUi>>(emptyList()) }
    var selectedGenre by remember { mutableStateOf<GenreUi?>(null) }
    var genreSongs by remember { mutableStateOf<List<SongUi>>(emptyList()) }

    LaunchedEffect(Unit) {
        songs = runCatching {
            dataSource.songs(0, 200, SortOption.TITLE_ASC, "")
        }.getOrDefault(emptyList())
        genres = runCatching { dataSource.genres() }.getOrDefault(emptyList())
        albums = runCatching {
            dataSource.albums(0, 12, SortOption.TITLE_ASC, "")
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(selectedGenre) {
        val genre = selectedGenre
        genreSongs = if (genre == null) {
            emptyList()
        } else {
            runCatching { dataSource.songsForGenre(genre.id) }
                .getOrDefault(emptyList())
        }
    }

    val topSongs = remember(stats) { statsStore.topSongs(12) }
    val recentStats = remember(stats) { statsStore.recentlyPlayed(12) }
    val topArtists = remember(stats) { statsStore.topArtists(10) }

    // Quick picks: top + recent, deduped, resolved to playable songs.
    val quickPicks = remember(topSongs, recentStats) {
        (topSongs + recentStats)
            .distinctBy { it.key }
            .take(8)
            .map { it.toSongUi() }
    }
    val recentSongs = remember(recentStats) {
        recentStats.take(6).map { it.toSongUi() }
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Wider screens get another quick-pick column.
        val pickColumns = if (maxWidth < 600.dp) 2 else 3

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Greeting + settings.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (showSettingsGear) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Direct-only: paste a link to download.
            if (showPasteCard) {
                item {
                    PasteLinkCard(
                        onPasteLink = onPasteLink,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { pasteCardExtra() }
            }

            if (quickPicks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Quick picks",
                        actionLabel = "Play all",
                        onAction = { onPlaySongs(quickPicks) },
                    )
                }
                item {
                    QuickPickGrid(
                        picks = quickPicks,
                        columns = pickColumns,
                        onPlaySong = onPlaySong,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // Mood chips: real genres from the library.
            if (genres.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGenre == null,
                                onClick = { selectedGenre = null },
                                label = { Text("All") },
                            )
                        }
                        items(genres, key = { it.id }) { genre ->
                            FilterChip(
                                selected = selectedGenre?.id == genre.id,
                                onClick = {
                                    selectedGenre =
                                        if (selectedGenre?.id == genre.id) null
                                        else genre
                                },
                                label = { Text(genre.name) },
                            )
                        }
                    }
                }
            }

            // Genre shelf, or jump-back-in when no genre is picked.
            val genre = selectedGenre
            if (genre != null) {
                item {
                    SectionHeader(title = genre.name)
                }
                if (genreSongs.isEmpty()) {
                    item {
                        Text(
                            text = "No songs tagged with this genre yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = 20.dp,
                                vertical = 8.dp,
                            ),
                        )
                    }
                } else {
                    items(genreSongs, key = { it.queueKey }) { song ->
                        SongRow(
                            song = song,
                            onClick = { onPlaySongs(genreSongs) },
                        )
                    }
                }
            } else if (recentSongs.isNotEmpty()) {
                item { SectionHeader(title = "Jump back in") }
                items(
                    recentSongs,
                    key = { it.queueKey },
                ) { song ->
                    SongRow(
                        song = song,
                        onClick = { onPlaySong(song) },
                    )
                }
            }

            if (albums.isNotEmpty()) {
                item { SectionHeader(title = "Your albums") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(albums, key = { it.id }) { album ->
                            AlbumCard(
                                title = album.title,
                                subtitle = album.artist,
                                artworkKey = album.artworkKey,
                                onClick = { onOpenAlbum(album.title) },
                            )
                        }
                    }
                }
            }

            if (topArtists.isNotEmpty()) {
                item { SectionHeader(title = "Top artists") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(topArtists, key = { it.artist }) { artist ->
                            ArtistCircle(
                                artist = artist,
                                onClick = { onOpenArtist(artist.artist) },
                            )
                        }
                    }
                }
            }

            if (songs.isEmpty() && quickPicks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.MusicNote,
                        title = "No music yet",
                        body = if (BuildConfig.INCLUDE_DOWNLOADER) {
                            "Head to Search to find music, or paste a " +
                                "link there to download your first song."
                        } else {
                            "Add audio files to your device and they " +
                                "will show up here."
                        },
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 2-3 column grid of compact pick cards (artwork + title + artist). */
@Composable
private fun QuickPickGrid(
    picks: List<SongUi>,
    columns: Int,
    onPlaySong: (SongUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        picks.chunked(columns).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { song ->
                    Card(
                        onClick = { onPlaySong(song) },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkImage(
                                artworkKey = song.artworkKey,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentDescription = "Album art",
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                // Pad a short last row so cards keep their width.
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Circular artist avatar with a name underneath. */
@Composable
private fun ArtistCircle(
    artist: ArtistStat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            artworkKey = artist.artworkKey,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentDescription = "Artist photo",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Direct-only card: paste a link to start a download. */
@Composable
private fun PasteLinkCard(
    onPasteLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var link by remember { mutableStateOf("") }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Download from a link",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("Paste a YouTube, Spotify or Amazon link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val trimmed = link.trim()
                    if (trimmed.isNotEmpty()) {
                        onPasteLink(trimmed)
                        link = ""
                    }
                },
                enabled = link.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Download")
            }
        }
    }
}
