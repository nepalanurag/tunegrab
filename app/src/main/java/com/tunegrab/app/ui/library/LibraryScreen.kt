package com.tunegrab.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tunegrab.app.library.FavoritesStore
import com.tunegrab.app.library.Playlist
import com.tunegrab.app.library.PlaylistEntry
import com.tunegrab.app.library.PlaylistStore
import com.tunegrab.app.ui.player.ArtworkImage
import com.tunegrab.app.ui.player.formatDurationMs
import com.tunegrab.app.ui.theme.TuneGrabTheme

private const val PAGE_SIZE = 200

/**
 * Library browser: 5 tabs (songs / albums / artists / genres / folders),
 * paged loading, search + sort. All data comes through [LibraryDataSource];
 * playback/navigation decisions go out through the callbacks so the
 * coordinator can wire the real player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    dataSource: LibraryDataSource,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    onAlbumClick: (AlbumUi) -> Unit,
    onArtistClick: (ArtistUi) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the Songs tab opens and scrolls to this MediaStore song id. */
    revealSongId: Long? = null,
    onRevealConsumed: () -> Unit = {},
    /** Delete button on each song row calls this; the host shows confirmation. */
    onDeleteSong: (SongUi) -> Unit = {},
    /** Swipe-right on a song row calls this; the host shows the playlist sheet. */
    onAddToPlaylist: (SongUi) -> Unit = {},
    /** When set, SongsTab drops this id from its list (after a delete). */
    deleteSignal: Long? = null,
    onDeleteSignalConsumed: () -> Unit = {},
    /** Backing store for the Playlists tab; null shows an empty state. */
    playlistStore: PlaylistStore? = null,
    /** Download one streamed playlist entry. */
    onDownloadEntry: (playlist: Playlist, entry: PlaylistEntry.Remote) -> Unit = { _, _ -> },
    /** Download every not-yet-downloaded streamed entry in the playlist. */
    onDownloadPlaylist: (playlist: Playlist) -> Unit = {},
    /** Hearted songs backing the Liked tab; null shows an empty state. */
    favoritesStore: FavoritesStore? = null,
) {
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SortOption.TITLE_ASC) }
    var artistGridView by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(
        initialPage = LibraryTab.SONGS.ordinal,
    ) { LibraryTab.entries.size }
    val scope = rememberCoroutineScope()

    // Keep the tab row in sync with horizontal swipes.
    LaunchedEffect(pagerState.currentPage) {
        val swiped = LibraryTab.entries[pagerState.currentPage]
        if (tab != swiped) tab = swiped
    }

    // A finished download can ask the library to reveal its song.
    LaunchedEffect(revealSongId) {
        if (revealSongId != null) pagerState.scrollToPage(LibraryTab.SONGS.ordinal)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Keep the tab row clear of the status bar (it was drawing
            // underneath it) and pin the background to the theme so dark
            // mode never shows the light window background through gaps.
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Filter chips, not tabs: one list that reshapes per filter.
        // (Material 3 reserves tabs for peer destinations; chips filter
        // a single collection.)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            items(LibraryTab.entries) { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(t.ordinal) }
                    },
                    label = {
                        Text(
                            t.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                        )
                    },
                )
            }
        }

        SearchSortRow(
            query = query,
            onQueryChange = { query = it },
            sort = sort,
            onSortChange = { sort = it },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (LibraryTab.entries[page]) {
                LibraryTab.SONGS -> SongsTab(
                    dataSource, query, sort, onSongClick,
                    revealSongId = revealSongId,
                    onRevealConsumed = onRevealConsumed,
                    onDeleteSong = onDeleteSong,
                    deleteSignal = deleteSignal,
                    onDeleteSignalConsumed = onDeleteSignalConsumed,
                    onAddToPlaylist = onAddToPlaylist,
                )
                LibraryTab.LIKED -> LikedTab(
                    dataSource = dataSource,
                    favoritesStore = favoritesStore,
                    query = query,
                    onSongClick = onSongClick,
                )
                LibraryTab.ALBUMS -> AlbumsTab(dataSource, query, sort, onAlbumClick)
                LibraryTab.ARTISTS -> ArtistsTab(
                    dataSource = dataSource,
                    query = query,
                    sort = sort,
                    onArtistClick = onArtistClick,
                    gridView = artistGridView,
                    onToggleGridView = { artistGridView = !artistGridView },
                    onSortChange = { sort = it },
                )
                LibraryTab.GENRES -> GenresTab(dataSource, query)
                LibraryTab.FOLDERS -> FoldersTab(dataSource, query, onSongClick)
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    dataSource = dataSource,
                    playlistStore = playlistStore,
                    query = query,
                    onSongClick = onSongClick,
                    onDeleteSong = onDeleteSong,
                    onAddToPlaylist = onAddToPlaylist,
                    onDownloadEntry = onDownloadEntry,
                    onDownloadPlaylist = onDownloadPlaylist,
                )
            }
        }
    }
}

@Composable
private fun SearchSortRow(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search your music") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(
                onClick = { sortMenuOpen = true },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.Sort, contentDescription = "Sort")
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            onSortChange(option)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Generic paginator keyed on a cache key (tab + sort + query). Loads page 0
 * on first composition / key change, then more pages on demand.
 */
private class Paginator<T>(
    private val loadPage: suspend (page: Int, pageSize: Int) -> List<T>,
) {
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false)
        private set
    var endReached by mutableStateOf(false)
        private set
    var loadCount by mutableStateOf(0)
        private set

    suspend fun refresh() {
        items.clear()
        endReached = false
        loadMore()
    }

    suspend fun loadMore() {
        if (loading || endReached) return
        loading = true
        val page = items.size / PAGE_SIZE
        val result = runCatching { loadPage(page, PAGE_SIZE) }.getOrDefault(emptyList())
        if (result.size < PAGE_SIZE) endReached = true
        items.addAll(result)
        loadCount++
        loading = false
    }
}

@Composable
private fun <T> rememberPaginator(
    key: Any,
    loadPage: suspend (page: Int, pageSize: Int) -> List<T>,
): Paginator<T> {
    val paginator = remember(key) { Paginator(loadPage) }
    LaunchedEffect(paginator) { paginator.refresh() }
    return paginator
}

/** Simple "loading more" footer; hidden when nothing is loading. */
@Composable
private fun LoadingFooter(visible: Boolean) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Songs ---

@Composable
private fun SongsTab(
    dataSource: LibraryDataSource,
    query: String,
    sort: SortOption,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
    revealSongId: Long? = null,
    onRevealConsumed: () -> Unit = {},
    onDeleteSong: (SongUi) -> Unit = {},
    deleteSignal: Long? = null,
    onDeleteSignalConsumed: () -> Unit = {},
    onAddToPlaylist: (SongUi) -> Unit = {},
) {
    val paginator = rememberPaginator(key = Triple("songs", sort, query)) { page, pageSize ->
        dataSource.songs(page, pageSize, sort, query)
    }
    val listState = rememberLazyListState()

    // Drop a deleted song from the list without a full reload.
    LaunchedEffect(deleteSignal) {
        deleteSignal?.let { id ->
            paginator.items.removeAll { it.id == id }
            onDeleteSignalConsumed()
        }
    }

    // Reveal a freshly downloaded song: page through until it's loaded,
    // then scroll to it. Consumed exactly once.
    LaunchedEffect(revealSongId) {
        val target = revealSongId ?: return@LaunchedEffect
        var guard = 0
        while (guard++ < 500) {
            if (!paginator.loading) {
                val idx = paginator.items.indexOfFirst { it.id == target }
                if (idx != -1) {
                    listState.scrollToItem(idx)
                    break
                }
                if (paginator.endReached) break
                paginator.loadMore()
            } else {
                kotlinx.coroutines.delay(120)
            }
        }
        onRevealConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        if (!paginator.loading && paginator.items.isEmpty()) {
            EmptyState(
                icon = { Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                text = if (query.isBlank()) "No songs on this device" else "No songs match \"$query\"",
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(paginator.items, key = { _, s -> s.id }) { index, song ->
                    if (index >= paginator.items.size - 10 && !paginator.endReached) {
                        LaunchedEffect(index, paginator.loadCount) { paginator.loadMore() }
                    }
                    SongRow(
                        song = song,
                        onClick = { onSongClick(paginator.items.toList(), index) },
                        onSwipeDelete = { onDeleteSong(song) },
                        onSwipeAddToPlaylist = { onAddToPlaylist(song) },
                    )
                }
                item { LoadingFooter(paginator.loading) }
            }
        }
    }
}

/**
 * A song row with swipe actions, Oto-style:
 * - swipe right -> add to playlist
 * - swipe left -> delete from device (with confirmation)
 *
 * Pass both swipe callbacks null for a plain non-swipeable row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongRow(
    song: SongUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeDelete: (() -> Unit)? = null,
    onSwipeAddToPlaylist: (() -> Unit)? = null,
) {
    if (onSwipeDelete == null && onSwipeAddToPlaylist == null) {
        SongRowContent(song, onClick, modifier)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Snap back after firing the action; the confirm dialog /
            // playlist sheet handles the rest.
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeAddToPlaylist?.invoke()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = onSwipeAddToPlaylist != null,
        enableDismissFromEndToStart = onSwipeDelete != null,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val isDelete = direction == SwipeToDismissBoxValue.EndToStart
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDelete) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment =
                        if (isDelete) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Icon(
                        if (isDelete) Icons.Filled.Delete else Icons.Filled.PlaylistAdd,
                        contentDescription =
                            if (isDelete) "Delete" else "Add to playlist",
                        tint =
                            if (isDelete) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        content = { SongRowContent(song, onClick) },
    )
}

@Composable
private fun SongRowContent(song: SongUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            ArtworkImage(
                artworkKey = song.artworkKey,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentDescription = "Album art",
            )
        },
        headlineContent = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${song.artist} • ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                formatDurationMs(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// --------------------------------------------------------------- Albums ---

@Composable
private fun AlbumsTab(
    dataSource: LibraryDataSource,
    query: String,
    sort: SortOption,
    onAlbumClick: (AlbumUi) -> Unit,
) {
    val paginator = rememberPaginator(key = Triple("albums", sort, query)) { page, pageSize ->
        dataSource.albums(page, pageSize, sort, query)
    }
    val gridState = rememberLazyGridState()

    Box(Modifier.fillMaxSize()) {
        if (!paginator.loading && paginator.items.isEmpty()) {
            EmptyState(
                icon = { Icon(Icons.Filled.Album, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                text = if (query.isBlank()) "No albums found" else "No albums match \"$query\"",
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(paginator.items, key = { it.id }) { album ->
                    AlbumTile(album = album, onClick = { onAlbumClick(album) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadingFooter(paginator.loading)
                }
                // Trigger next page when the grid scrolls near the end.
                if (!paginator.endReached && paginator.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(paginator.loadCount) { paginator.loadMore() }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumTile(album: AlbumUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            ArtworkImage(
                artworkKey = album.artworkKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentDescription = "Cover of ${album.title}",
            )
            Column(Modifier.padding(12.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${album.artist} • ${album.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// -------------------------------------------------------------- Artists ---

@Composable
private fun ArtistsTab(
    dataSource: LibraryDataSource,
    query: String,
    sort: SortOption,
    onArtistClick: (ArtistUi) -> Unit,
    gridView: Boolean,
    onToggleGridView: () -> Unit,
    onSortChange: (SortOption) -> Unit,
) {
    val paginator = rememberPaginator(key = Triple("artists", sort, query)) { page, pageSize ->
        dataSource.artists(page, pageSize, sort, query)
    }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        // Oto-style control row: sort pill on the left, list/grid toggle on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var sortMenuOpen by remember { mutableStateOf(false) }
            Box {
                Surface(
                    shape = CircleShape,
                    tonalElevation = 1.dp,
                    modifier = Modifier.clickable { sortMenuOpen = true },
                ) {
                    Text(
                        sort.pillLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                onSortChange(option)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, tonalElevation = 1.dp) {
                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    IconButton(
                        onClick = { if (gridView) onToggleGridView() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.ViewList,
                            contentDescription = "List view",
                            tint = if (!gridView) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { if (!gridView) onToggleGridView() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = "Grid view",
                            tint = if (gridView) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (!paginator.loading && paginator.items.isEmpty()) {
                EmptyState(
                    icon = { Icon(Icons.Filled.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    text = if (query.isBlank()) "No artists found" else "No artists match \"$query\"",
                )
            } else if (gridView) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(paginator.items, key = { it.id }) { artist ->
                        ArtistTile(artist = artist, onClick = { onArtistClick(artist) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadingFooter(paginator.loading)
                    }
                    if (!paginator.endReached && paginator.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(paginator.loadCount) { paginator.loadMore() }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(paginator.items, key = { it.id }) { artist ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    artist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text("${artist.albumCount} albums • ${artist.songCount} songs")
                            },
                            leadingContent = {
                                ArtistMosaic(
                                    keys = artist.artworkKeys,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentDescription = artist.name,
                                )
                            },
                            modifier = Modifier.clickable { onArtistClick(artist) },
                        )
                    }
                    item { LoadingFooter(paginator.loading) }
                    if (!paginator.endReached && paginator.items.isNotEmpty()) {
                        item {
                            LaunchedEffect(paginator.loadCount) { paginator.loadMore() }
                        }
                    }
                }
            }
        }
    }
}

/** Compact sort label for the pill, e.g. "TITLE ↑". */
private fun SortOption.pillLabel(): String = when (this) {
    SortOption.TITLE_ASC -> "TITLE ↑"
    SortOption.ARTIST_ASC -> "ARTIST ↑"
    SortOption.ALBUM_ASC -> "ALBUM ↑"
    SortOption.DURATION_DESC -> "LONGEST ↓"
    SortOption.DATE_ADDED_DESC -> "RECENT ↓"
}

@Composable
fun ArtistTile(artist: ArtistUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Oto-style: circular portrait with the name below, no card chrome.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistMosaic(
            keys = artist.artworkKeys,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentDescription = artist.name,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${artist.albumCount} albums • ${artist.songCount} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Artist artwork: MediaStore keeps no artist photos, so the tile shows a
 * collage of the artist's own album covers — one large when there's a
 * single album, a 2x2 mosaic otherwise, a music-note fallback when bare.
 */
@Composable
fun ArtistMosaic(
    keys: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    when {
        keys.isEmpty() -> ArtworkImage(
            artworkKey = null,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        keys.size == 1 -> ArtworkImage(
            artworkKey = keys[0],
            modifier = modifier,
            contentDescription = contentDescription,
        )
        else -> {
            val quad = listOf(
                keys.getOrNull(0),
                keys.getOrNull(1),
                keys.getOrNull(2),
                keys.getOrNull(3),
            )
            Column(modifier) {
                Row(Modifier.weight(1f)) {
                    ArtworkImage(quad[0], Modifier.weight(1f).fillMaxSize(), contentDescription)
                    ArtworkImage(quad[1], Modifier.weight(1f).fillMaxSize(), contentDescription)
                }
                Row(Modifier.weight(1f)) {
                    ArtworkImage(quad[2], Modifier.weight(1f).fillMaxSize(), contentDescription)
                    ArtworkImage(quad[3], Modifier.weight(1f).fillMaxSize(), contentDescription)
                }
            }
        }
    }
}

// --------------------------------------------------------------- Genres ---

@Composable
private fun GenresTab(dataSource: LibraryDataSource, query: String) {
    var genres by remember { mutableStateOf<List<GenreUi>?>(null) }
    LaunchedEffect(dataSource, query) {
        genres = runCatching { dataSource.genres() }.getOrDefault(emptyList())
    }
    val shown = remember(genres, query) {
        val list = genres.orEmpty()
        if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
    }
    if (genres == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (shown.isEmpty()) {
        EmptyState(
            icon = { Icon(Icons.Filled.Category, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            text = "No genres found",
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(shown, key = { _, g -> g.id }) { _, genre ->
                ListItem(
                    headlineContent = { Text(genre.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${genre.songCount} songs") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }
}

// -------------------------------------------------------------- Folders ---

@Composable
private fun FoldersTab(
    dataSource: LibraryDataSource,
    query: String,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
) {
    var folders by remember { mutableStateOf<List<FolderUi>?>(null) }
    var openFolder by remember { mutableStateOf<FolderUi?>(null) }
    var folderSongs by remember { mutableStateOf<List<SongUi>?>(null) }

    LaunchedEffect(dataSource, query) {
        folders = runCatching { dataSource.folders() }.getOrDefault(emptyList())
        openFolder = null
    }
    LaunchedEffect(openFolder) {
        folderSongs = openFolder?.let { runCatching { dataSource.songsForFolder(it.path) }.getOrNull() }
    }

    val shown = remember(folders, query) {
        val list = folders.orEmpty()
        if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) }
    }

    val folder = openFolder
    if (folder != null) {
        // Drill-in: songs inside the folder, with a back row.
        val songs = folderSongs
        Column(Modifier.fillMaxSize()) {
            ListItem(
                modifier = Modifier.clickable { openFolder = null },
                headlineContent = { Text("Back to folders") },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
            )
            if (songs == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        SongRow(song = song, onClick = { onSongClick(songs, index) })
                    }
                }
            }
        }
    } else if (folders == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (shown.isEmpty()) {
        EmptyState(
            icon = { Icon(Icons.Filled.Folder, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            text = "No folders found",
        )
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(shown, key = { _, f -> f.path }) { _, f ->
                ListItem(
                    modifier = Modifier.clickable { openFolder = f },
                    headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${f.songCount} songs • ${f.path}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Open folder")
                    },
                )
            }
        }
    }
}

// ----------------------------------------------------------------- Stats ---

/**
 * Liked tab: every hearted song in one list, local and streamed.
 * Local hearts resolve through [LibraryDataSource.songsByIds]; streamed
 * hearts carry their own metadata in [FavoritesStore]. Tapping a row
 * plays it with the liked list as the queue.
 */
@Composable
private fun LikedTab(
    dataSource: LibraryDataSource,
    favoritesStore: FavoritesStore?,
    query: String,
    onSongClick: (songs: List<SongUi>, index: Int) -> Unit,
) {
    if (favoritesStore == null) {
        EmptyState(
            icon = {
                Icon(
                    Icons.Filled.Favorite, null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            text = "Songs you heart will show up here",
        )
        return
    }
    val localIds by favoritesStore.favorites.collectAsState()
    val remoteFavs by favoritesStore.remoteFavoriteTracks.collectAsState()
    var localSongs by remember { mutableStateOf<List<SongUi>>(emptyList()) }

    LaunchedEffect(localIds) {
        localSongs = if (localIds.isEmpty()) emptyList()
        else dataSource.songsByIds(localIds.toList())
    }

    val songs = remember(localSongs, remoteFavs) {
        val remote = remoteFavs.map {
            SongUi.remote(
                videoId = it.videoId,
                title = it.title.ifBlank { "Unknown title" },
                artist = it.artist.ifBlank { SongUi.UNKNOWN_ARTIST },
                thumbnailUrl = it.artworkKey,
                durationSec = null,
            )
        }
        (localSongs + remote).sortedBy { it.title.lowercase() }
    }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
        }
    }

    if (songs.isEmpty()) {
        EmptyState(
            icon = {
                Icon(
                    Icons.Filled.Favorite, null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            text = "Songs you heart will show up here",
        )
        return
    }
    if (filtered.isEmpty()) {
        EmptyState(
            icon = {
                Icon(
                    Icons.Filled.Favorite, null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            text = "No liked songs match \"$query\"",
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(filtered, key = { _, s -> s.queueKey }) { index, song ->
            SongRow(
                song = song,
                onClick = { onSongClick(filtered, index) },
            )
        }
    }
}
// -------------------------------------------------------------- Preview ---

private class PreviewLibraryDataSource : LibraryDataSource {
    private val sample = List(5) {
        SongUi(
            id = it.toLong(),
            title = "Track ${it + 1}",
            artist = "Sample Artist",
            album = "Sample Album",
            durationMs = 187_000L + it * 12_000,
            artworkKey = null,
        )
    }

    override suspend fun songs(page: Int, pageSize: Int, sort: SortOption, query: String) =
        sample.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }

    override suspend fun albums(page: Int, pageSize: Int, sort: SortOption, query: String) =
        listOf(AlbumUi(1, "Sample Album", "Sample Artist", 5, 2026, null))

    override suspend fun artists(page: Int, pageSize: Int, sort: SortOption, query: String) =
        listOf(ArtistUi(1, "Sample Artist", 5, 1))

    override suspend fun genres() = listOf(GenreUi(1, "Electronic", 5))
    override suspend fun folders() = listOf(FolderUi("/storage/music", "music", 5))
    override suspend fun songsForAlbum(albumId: Long) = sample
    override suspend fun songsForArtist(artistId: Long) = sample
    override suspend fun songsForFolder(path: String) = sample
    override suspend fun songsForGenre(genreId: Long) = sample
    override suspend fun songCount() = 5L

    override suspend fun deleteSong(songId: Long) =
        com.tunegrab.app.library.DeleteResult.Deleted

    override suspend fun songsByIds(ids: List<Long>): List<SongUi> =
        ids.mapNotNull { id -> sample.firstOrNull { it.id == id } }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    TuneGrabTheme {
        LibraryScreen(
            dataSource = PreviewLibraryDataSource(),
            onSongClick = { _, _ -> },
            onAlbumClick = {},
            onArtistClick = {},
        )
    }
}
