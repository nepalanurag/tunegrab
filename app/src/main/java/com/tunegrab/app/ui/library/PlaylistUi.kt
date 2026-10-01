package com.tunegrab.app.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.OutlinedButton
import com.tunegrab.app.library.Playlist
import com.tunegrab.app.library.PlaylistEntry
import com.tunegrab.app.library.PlaylistStore
import kotlinx.coroutines.launch

/**
 * Bottom sheet for "swipe right -> add to playlist" (and the player's
 * overflow menu). Lists existing playlists, creates a new one inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: SongUi,
    store: PlaylistStore,
    onDismiss: () -> Unit,
    onAdded: (playlistName: String) -> Unit,
) {
    val playlists by store.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            feedback?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet. Create one below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text("${playlist.entries.size} songs")
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val videoId = song.remoteVideoId
                                    val added = if (videoId != null) {
                                        store.addRemoteToPlaylist(
                                            playlist.id,
                                            videoId = videoId,
                                            title = song.title,
                                            artist = song.artist,
                                            thumbnailUrl = song.artworkKey,
                                            durationSec = (song.durationMs / 1000)
                                                .takeIf { it > 0 },
                                        )
                                    } else {
                                        store.addToPlaylist(playlist.id, song.id)
                                    }
                                    if (added) {
                                        onAdded(playlist.name)
                                    } else {
                                        feedback = "Already in \"${playlist.name}\""
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    scope.launch {
                        val created = store.createPlaylist(newName)
                        val videoId = song.remoteVideoId
                        if (videoId != null) {
                            store.addRemoteToPlaylist(
                                created.id,
                                videoId = videoId,
                                title = song.title,
                                artist = song.artist,
                                thumbnailUrl = song.artworkKey,
                                durationSec = (song.durationMs / 1000).takeIf { it > 0 },
                            )
                        } else {
                            store.addToPlaylist(created.id, song.id)
                        }
                        onAdded(created.name)
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Create")
                }
            }
        }
    }
}

// --------------------------------------------------------------- Playlists ---

/**
 * The Playlists library tab. Drill-in to a playlist is handled internally
 * (like FoldersTab): song rows keep the swipe actions, with swipe-left
 * removing the song from the playlist instead of deleting the file.
 *
 * Playlists hold a unified mix of downloaded library songs and streaming
 * YouTube Music entries. Streaming entries play through the main player;
 * downloading one flips it to its local file automatically.
 */
@Composable
fun PlaylistsTab(
    dataSource: LibraryDataSource,
    playlistStore: PlaylistStore?,
    query: String,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    onDeleteSong: (SongUi) -> Unit,
    onAddToPlaylist: (SongUi) -> Unit,
    onDownloadEntry: (playlist: Playlist, entry: PlaylistEntry.Remote) -> Unit = { _, _ -> },
    onDownloadPlaylist: (playlist: Playlist) -> Unit = {},
) {
    val playlists by if (playlistStore != null) {
        playlistStore.playlists.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistSongs by remember { mutableStateOf<List<SongUi>?>(null) }
    val scope = rememberCoroutineScope()

    // Refresh the open playlist's songs when the store changes (e.g. a
    // finished download links a remote entry to its local file).
    LaunchedEffect(openPlaylist?.id, playlists) {
        val id = openPlaylist?.id
        playlistSongs = if (id == null) {
            null
        } else {
            val target = playlists.firstOrNull { it.id == id }
            openPlaylist = target
            target?.let { runCatching { resolveEntries(dataSource, it.entries) }.getOrNull() }
        }
    }

    val shown = remember(playlists, query) {
        if (query.isBlank()) playlists
        else playlists.filter { it.name.contains(query, ignoreCase = true) }
    }

    val playlist = openPlaylist
    if (playlist != null) {
        PlaylistDetailContent(
            playlist = playlist,
            songs = playlistSongs,
            onBack = { openPlaylist = null },
            onSongClick = onSongClick,
            onPlayAll = { songs -> if (songs.isNotEmpty()) onSongClick(songs, 0) },
            onRemoveSong = { song ->
                playlistStore?.let { store ->
                    scope.launch { store.removeEntry(playlist.id, song.queueKey) }
                }
            },
            onAddToPlaylist = onAddToPlaylist,
            onDownloadEntry = { song ->
                val entry = playlist.entries
                    .filterIsInstance<PlaylistEntry.Remote>()
                    .firstOrNull { it.videoId == song.remoteVideoId }
                if (entry != null) onDownloadEntry(playlist, entry)
            },
            onDownloadPlaylist = { onDownloadPlaylist(playlist) },
            onDeletePlaylist = {
                playlistStore?.let { store ->
                    scope.launch {
                        store.deletePlaylist(playlist.id)
                        openPlaylist = null
                    }
                }
            },
        )
        return
    }

    if (shown.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (query.isBlank())
                    "No playlists yet.\nSwipe a song right to add it to a new playlist."
                else "No playlists match \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(shown, key = { it.id }) { item ->
            ListItem(
                headlineContent = {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text("${item.entries.size} songs") },
                leadingContent = {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.clickable { openPlaylist = item },
            )
        }
    }
}

/**
 * Resolves playlist entries to UI songs, preserving order. Local entries
 * whose file vanished are dropped; remote entries always resolve.
 */
suspend fun resolveEntries(
    dataSource: LibraryDataSource,
    entries: List<PlaylistEntry>,
): List<SongUi> {
    val localIds = entries
        .filterIsInstance<PlaylistEntry.Local>()
        .map { it.songId }
    val byId = runCatching { dataSource.songsByIds(localIds) }
        .getOrNull().orEmpty().associateBy { it.id }
    return entries.mapNotNull { entry ->
        when (entry) {
            is PlaylistEntry.Local -> byId[entry.songId]
            is PlaylistEntry.Remote -> SongUi(
                id = -1L,
                title = entry.title,
                artist = entry.artist,
                album = "",
                durationMs = (entry.durationSec ?: 0L) * 1000L,
                artworkKey = entry.thumbnailUrl,
                remoteVideoId = entry.videoId,
                localContentUri = entry.localContentUri,
            )
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    songs: List<SongUi>?,
    onBack: () -> Unit,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    onPlayAll: (List<SongUi>) -> Unit,
    onRemoveSong: (SongUi) -> Unit,
    onAddToPlaylist: (SongUi) -> Unit,
    onDownloadEntry: (SongUi) -> Unit,
    onDownloadPlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val streamingCount = songs?.count { it.isRemote && it.localContentUri == null } ?: 0
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.entries.size} songs" +
                        if (streamingCount > 0) " · $streamingCount streaming" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (streamingCount > 0) {
                OutlinedButton(onClick = onDownloadPlaylist) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Save all")
                }
            }
            Button(onClick = { songs?.let(onPlayAll) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Play")
            }
        }
        val list = songs
        when {
            list == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            list.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "This playlist is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                // queueKey qualified with the index: the same song can be in
                // a playlist twice, and Lazy needs unique keys.
                itemsIndexed(list, key = { index, s -> "${s.queueKey}@$index" }) { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            SongRow(
                                song = song,
                                onClick = { onSongClick(list, index) },
                                // Inside a playlist, swipe-left removes from the
                                // playlist (non-destructive); file delete lives on
                                // the Songs tab and album/artist details.
                                onSwipeDelete = { onRemoveSong(song) },
                                onSwipeAddToPlaylist = { onAddToPlaylist(song) },
                            )
                        }
                        if (song.isRemote && song.localContentUri == null) {
                            IconButton(onClick = { onDownloadEntry(song) }) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"${playlist.name}\" will be removed. Your songs stay on the device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeletePlaylist()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
