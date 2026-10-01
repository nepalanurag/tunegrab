package com.tunegrab.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.player.ArtworkImage

/**
 * Album detail: cover, metadata, and the full song list. Tapping a song
 * plays it with the album's songs as the queue.
 */
@Composable
fun AlbumDetailScreen(
    album: AlbumUi,
    songs: List<SongUi>?,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    onBack: () -> Unit,
    onDeleteSong: (SongUi) -> Unit = {},
    onAddToPlaylist: (SongUi) -> Unit = {},
) {
    DetailScaffold(
        onBack = onBack,
        headerArt = {
            ArtworkImage(
                artworkKey = album.artworkKey,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentDescription = "Album art",
            )
        },
        title = album.title,
        subtitle = album.artist,
        meta = buildString {
            append("${album.songCount} songs")
            if (album.year > 0) append(" • ${album.year}")
        },
        songs = songs,
        onSongClick = onSongClick,
        onDeleteSong = onDeleteSong,
        onAddToPlaylist = onAddToPlaylist,
    )
}

/**
 * Artist detail: mosaic artwork, metadata, and every song by the artist.
 */
@Composable
fun ArtistDetailScreen(
    artist: ArtistUi,
    songs: List<SongUi>?,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    onBack: () -> Unit,
    onDeleteSong: (SongUi) -> Unit = {},
    onAddToPlaylist: (SongUi) -> Unit = {},
) {
    DetailScaffold(
        onBack = onBack,
        headerArt = {
            ArtistMosaic(
                keys = artist.artworkKeys,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentDescription = artist.name,
            )
        },
        title = artist.name,
        subtitle = "${artist.albumCount} albums",
        meta = "${artist.songCount} songs",
        songs = songs,
        onSongClick = onSongClick,
        onDeleteSong = onDeleteSong,
        onAddToPlaylist = onAddToPlaylist,
    )
}

@Composable
private fun DetailScaffold(
    onBack: () -> Unit,
    headerArt: @Composable () -> Unit,
    title: String,
    subtitle: String,
    meta: String,
    songs: List<SongUi>?,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    onDeleteSong: (SongUi) -> Unit,
    onAddToPlaylist: (SongUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            headerArt()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            songs == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No songs found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(songs, index) },
                        onSwipeDelete = { onDeleteSong(song) },
                        onSwipeAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
            }
        }
    }
}
