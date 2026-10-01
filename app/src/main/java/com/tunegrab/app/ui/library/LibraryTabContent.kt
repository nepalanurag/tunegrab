package com.tunegrab.app.ui.library

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunegrab.app.MediaStoreLibraryDataSource
import com.tunegrab.app.library.DeleteResult
import com.tunegrab.app.library.FavoritesStore
import com.tunegrab.app.library.Playlist
import com.tunegrab.app.library.PlaylistEntry
import com.tunegrab.app.library.PlaylistStore
import kotlinx.coroutines.launch

/**
 * Pro Phase 1: the Library tab. Shows a permission prompt until the user
 * grants media access, then the browsable library. Taps route into the
 * shared player controller.
 */
@Composable
fun LibraryTabContent(
    mediaGranted: Boolean,
    dataSource: MediaStoreLibraryDataSource,
    playerController: com.tunegrab.app.ui.player.PlayerUiController,
    onRequestPermission: () -> Unit,
    playlistStore: PlaylistStore,
    /** Swipe-right on a song row calls this; the host shows the playlist sheet. */
    onAddToPlaylist: (SongUi) -> Unit,
    revealSongId: Long? = null,
    onRevealConsumed: () -> Unit = {},
    /** After playlist downloads start, the host shows the Download tab. */
    onDownloadStarted: () -> Unit = {},
    /**
     * Album name Home asked the Library to open. Resolved to the matching
     * album and shown as the detail screen, then consumed.
     */
    albumTarget: String? = null,
    onAlbumTargetConsumed: () -> Unit = {},
    /** Hearted songs backing the Liked tab. */
    favoritesStore: FavoritesStore? = null,
    /**
     * Downloads a streamed playlist entry so it works offline. Only
     * provided by the direct build; the Play build leaves the default
     * no-op and LibraryScreen hides the download rows.
     */
    onDownloadRemoteEntry: (playlist: Playlist, entry: PlaylistEntry.Remote, subfolder: String?) -> Unit = { _, _, _ -> },
    /** True while this playlist entry has a live queue item. */
    isRemoteEntryQueued: (playlistId: String, videoId: String) -> Boolean = { _, _ -> false },
) {
    if (!mediaGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Let TuneGrab see your music",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant access to audio files so the library can list " +
                    "your songs, albums and artists.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequestPermission) { Text("Grant access") }
        }
        return
    }
    // Album/artist detail navigation: tapping a tile opens its song list
    // instead of just playing the first track.
    var selectedAlbum by remember { mutableStateOf<AlbumUi?>(null) }
    var selectedArtist by remember { mutableStateOf<ArtistUi?>(null) }
    val detailKey = selectedAlbum?.id to selectedArtist?.id
    var detailSongs by remember(detailKey) {
        mutableStateOf<List<SongUi>?>(null)
    }
    LaunchedEffect(detailKey) {
        val album = selectedAlbum
        val artist = selectedArtist
        if (album == null && artist == null) {
            detailSongs = null
            return@LaunchedEffect
        }
        detailSongs = runCatching {
            if (album != null) dataSource.songsForAlbum(album.id)
            else dataSource.songsForArtist(artist!!.id)
        }.getOrDefault(emptyList())
    }
    BackHandler(enabled = selectedAlbum != null || selectedArtist != null) {
        selectedAlbum = null
        selectedArtist = null
    }

    // External navigation (Home -> album detail): resolve the album name
    // once, show its detail screen, then tell the host it's consumed.
    LaunchedEffect(albumTarget) {
        val name = albumTarget ?: return@LaunchedEffect
        val match = runCatching {
            dataSource.albums(0, 100, SortOption.TITLE_ASC, name)
        }.getOrDefault(emptyList())
            .firstOrNull { it.title.equals(name, ignoreCase = true) }
        if (match != null) {
            selectedAlbum = match
            selectedArtist = null
        }
        onAlbumTargetConsumed()
    }

    // ---- Delete flow: trash icon -> confirm -> MediaStore delete ----
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<SongUi?>(null) }
    var consentSong by remember { mutableStateOf<SongUi?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleteSignal by remember { mutableStateOf<Long?>(null) }

    fun removeFromLists(songId: Long) {
        detailSongs = detailSongs?.filterNot { it.id == songId }
        deleteSignal = songId
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = consentSong
        consentSong = null
        if (result.resultCode == Activity.RESULT_OK && song != null) {
            removeFromLists(song.id)
        }
    }

    fun performDelete(song: SongUi) {
        scope.launch {
            when (val r = dataSource.deleteSong(song.id)) {
                is DeleteResult.Deleted -> removeFromLists(song.id)
                is DeleteResult.NeedsConsent -> {
                    consentSong = song
                    runCatching {
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(r.intentSender).build()
                        )
                    }.onFailure {
                        consentSong = null
                        deleteError = "Couldn't open the system delete dialog."
                    }
                }
                is DeleteResult.Failed -> deleteError = r.message
            }
        }
    }

    // ---- Playlist downloads: streamed entries go straight to the service
    // ---- with their playlist context; finished files link back to the
    // ---- Remote playlist entries can be saved offline (direct build) ----
    // The actual download work is injected by the host; the Play build
    // passes no-ops so this file stays downloader-free.

    val album = selectedAlbum
    val artist = selectedArtist
    when {
        album != null -> AlbumDetailScreen(
            album = album,
            songs = detailSongs,
            onSongClick = { songs, index -> playerController.playQueue(songs, index) },
            onBack = { selectedAlbum = null },
            onDeleteSong = { pendingDelete = it },
            onAddToPlaylist = onAddToPlaylist,
        )
        artist != null -> ArtistDetailScreen(
            artist = artist,
            songs = detailSongs,
            onSongClick = { songs, index -> playerController.playQueue(songs, index) },
            onBack = { selectedArtist = null },
            onDeleteSong = { pendingDelete = it },
            onAddToPlaylist = onAddToPlaylist,
        )
        else -> LibraryScreen(
            dataSource = dataSource,
            onSongClick = { songs, index -> playerController.playQueue(songs, index) },
            onAlbumClick = { selectedAlbum = it; selectedArtist = null },
            onArtistClick = { selectedArtist = it; selectedAlbum = null },
            revealSongId = revealSongId,
            onRevealConsumed = onRevealConsumed,
            onDeleteSong = { pendingDelete = it },
            onAddToPlaylist = onAddToPlaylist,
            deleteSignal = deleteSignal,
            onDeleteSignalConsumed = { deleteSignal = null },
            playlistStore = playlistStore,
            favoritesStore = favoritesStore,
            onDownloadEntry = { playlist, entry ->
                onDownloadRemoteEntry(playlist, entry, null)
                onDownloadStarted()
            },
            onDownloadPlaylist = { playlist ->
                playlist.entries
                    .filterIsInstance<PlaylistEntry.Remote>()
                    .filter { it.localContentUri == null }
                    .filter { !isRemoteEntryQueued(playlist.id, it.videoId) }
                    .forEach { onDownloadRemoteEntry(playlist, it, playlist.name) }
                onDownloadStarted()
            },
        )
    }

    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete song?") },
            text = {
                Text("\"${song.title}\" will be permanently removed from your device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        performDelete(song)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
    deleteError?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { deleteError = null }) { Text("OK") }
            },
        )
    }
}
