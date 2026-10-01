package com.tunegrab.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.tunegrab.app.ui.activity.ActivityScreen
import com.tunegrab.app.ui.home.HomeScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tunegrab.app.library.DeleteResult
import com.tunegrab.app.library.FavoritesStore
import com.tunegrab.app.library.LibraryScanner
import com.tunegrab.app.library.ListeningStatsStore
import com.tunegrab.app.library.MediaStoreRepository
import com.tunegrab.app.library.PlaylistStore
import com.tunegrab.app.library.SongDetails
import com.tunegrab.app.library.SongDetailsLoader
import com.tunegrab.app.library.SortBy
import com.tunegrab.app.player.PlayerManager
import com.tunegrab.app.ui.library.AddToPlaylistSheet
import com.tunegrab.app.ui.library.LibraryTabContent
import com.tunegrab.app.ui.library.SongDetailsSheet
import com.tunegrab.app.ui.library.SongUi
import com.tunegrab.app.ui.player.MiniPlayer
import com.tunegrab.app.ui.player.NowPlayingScreen
import com.tunegrab.app.ui.player.NpTheme
import com.tunegrab.app.ui.theme.TuneGrabTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared player shell for both flavors: permissions, the Media3 player
 * controller, Library tab, Now Playing, mini player, bottom nav, Settings,
 * and the shared sheets (add-to-playlist, details, delete).
 *
 * Flavor-specific behavior (downloads, online search, artist/album screens,
 * share-sheet intake, Pro upsell) lives behind the open hooks below and is
 * implemented by the flavor's `MainActivity` subclass. The Play Store build
 * is a plain local music player; the direct build is the full TuneGrab.
 */
open class BaseMainActivity : ComponentActivity() {

    private var storageGranted by mutableStateOf(false)
    protected val isStorageGranted: Boolean get() = storageGranted
    private var mediaGranted by mutableStateOf(false)

    // Freemium UI state. The upsell dialog itself is flavor UI (see
    // ProUpsellSlot); the Play build never shows it.
    protected var showProUpsell by mutableStateOf(false)
    protected var proUpsellReason by mutableStateOf("")

    /** Current bottom-nav tab. Flavors choose the launch tab. */
    protected var appTab by mutableStateOf(AppTab.HOME)

    /** Tab the app opens on. Both builds open on Home. */
    protected open val defaultTab: AppTab = AppTab.HOME

    /**
     * Ordered bottom-nav tabs for this flavor. Direct: Home, Search,
     * Library, Activity. Play Store: Home, Library (+ a Settings action).
     */
    protected open val navTabs: List<AppTab>
        get() = listOf(AppTab.HOME, AppTab.LIBRARY, AppTab.ACTIVITY)

    /** Play build shows a Settings entry in the bottom bar (opens the overlay). */
    protected open val showSettingsNavItem: Boolean = false

    /** Set when a finished download is tapped: Library reveals this song. */
    protected var revealSongId by mutableStateOf<Long?>(null)

    /** Song chosen for the add-to-playlist sheet; settable from flavor UI. */
    protected var addSheetSong by mutableStateOf<SongUi?>(null)

    /** Settings overlay; hoisted so flavor tabs can open it. */
    protected var showSettings by mutableStateOf(false)

    protected val trackCache = TrackCache()
    protected lateinit var playerController: PlayerUiControllerAdapter
    protected lateinit var playlistStore: PlaylistStore
    protected val snackState = SnackbarHostState()

    /** Open the Now Playing screen. */
    protected fun openNowPlaying() {
        showNowPlaying = true
    }

    /** Transient confirmation ("Added to queue"); the action opens Now Playing. */
    protected fun showSnack(message: String, actionLabel: String? = null) {
        lifecycleScope.launch {
            val result = snackState.showSnackbar(message, actionLabel)
            if (actionLabel != null && result == SnackbarResult.ActionPerformed) {
                showNowPlaying = true
            }
        }
    }

    /**
     * Saves the current queue (e.g. a Radio run) as a new playlist.
     * Only reachable from Radio UI, which is direct-build only.
     */
    protected fun saveRadioAsPlaylist() {
        val songs = playerController.queue.value
        if (songs.isEmpty()) return
        val seedTitle = songs.firstOrNull()?.title?.takeIf { it.isNotBlank() }
        val name = if (seedTitle != null) "$seedTitle Radio" else "Radio"
        lifecycleScope.launch {
            val playlist = playlistStore.createPlaylist(name)
            songs.forEach { s ->
                val videoId = s.remoteVideoId
                if (s.isRemote && videoId != null) {
                    playlistStore.addRemoteToPlaylist(
                        playlist.id,
                        videoId,
                        s.title,
                        s.artist,
                        s.artworkKey,
                        s.durationMs.takeIf { it > 0 }?.div(1000),
                    )
                } else {
                    playlistStore.addToPlaylist(playlist.id, s.id)
                }
            }
            showSnack("Saved to playlist $name", null)
        }
    }

    private var showNowPlaying by mutableStateOf(false)

    // ---- Flavor hooks ----
    //
    // The base shell calls these; the flavor's MainActivity subclass
    // implements the download/online ones. The Play build leaves every
    // one at its no-op default.

    /** Extra bottom-nav items (Download, Search in the direct build). */
    @Composable
    protected open fun RowScope.ExtraNavItems() {
    }

    /**
     * Flavor tab content. Return true when [tab] was handled; the base
     * handles LIBRARY itself.
     */
    @Composable
    protected open fun FlavorTabContent(tab: AppTab): Boolean = false

    /** Full-screen overlays above everything (artist/album in direct). */
    @Composable
    protected open fun FlavorOverlays() {
    }

    /** Pro upsell dialog slot. Empty in the Play build (no Pro tier). */
    @Composable
    protected open fun ProUpsellSlot() {
    }

    /** A download finished and the user tapped it: reveal it in Library. */
    protected open fun onDownloadStarted() {
    }

    /** Open the online artist screen. No-op in the Play build. */
    protected open fun openArtist(name: String, videoId: String?) {
    }

    /** Download a streaming track from Now Playing. No-op in the Play build. */
    protected open fun onDownloadSongRequested(song: SongUi) {
    }

    /**
     * Save a streamed playlist entry offline. No-op in the Play build
     * (remote entries can't exist there).
     */
    protected open fun onDownloadRemoteEntry(
        playlist: com.tunegrab.app.library.Playlist,
        entry: com.tunegrab.app.library.PlaylistEntry.Remote,
        subfolder: String?,
    ) {
    }

    /** True while a playlist entry has a live download queue item. */
    protected open fun isRemoteEntryQueued(playlistId: String, videoId: String): Boolean = false

    // ---- Home / Activity flavor slots ----

    /** Direct build shows the link paste card on Home. */
    protected open val homeShowPasteCard: Boolean = false

    /** A link was pasted/submitted on Home. Direct build starts the download flow. */
    protected open fun onPasteLink(link: String) {}

    /** Extra content under the Home paste card (direct: match-preview card). */
    @Composable
    protected open fun HomePasteExtra() {}

    /** Direct build shows a settings gear on Home (Play has the Settings tab). */
    protected open val homeShowSettingsGear: Boolean = false

    /** Open a local album's detail screen. */
    protected open fun openAlbum(album: String) {}

    /**
     * Album name Home asked the Library to open. The flavor sets it;
     * LibraryTabContent consumes it and shows the album detail.
     */
    protected open val albumTarget: String? get() = null
    protected open fun onAlbumTargetConsumed() {}

    /**
     * Download queue + history section for the Activity tab. Direct build
     * renders the live queue UI; the Play build leaves the default no-op.
     */
    @Composable
    protected open fun ActivityDownloadSections() {}

    /**
     * Back pressed on a tab (Now Playing already closed). Return true when
     * consumed; the base exits the app otherwise.
     */
    protected open fun onTabBackPressed(): Boolean = false

    /** Incoming intents (share, media-search). The base ignores them. */
    protected open fun handleIntent(intent: Intent?) {
    }

    /** True when the flavor wants the sleep timer row (direct build). */
    protected open val showSleepTimer: Boolean = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStorageStatus()
        }

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshMediaStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.milestone(this, "BaseMainActivity.onCreate start")
        // Draw edge-to-edge so the gesture navigation bar sits under the
        // bottom nav instead of overlapping it; insets are handled per-screen.
        enableEdgeToEdge()
        CrashReporter.milestone(this, "enableEdgeToEdge done")
        refreshStorageStatus()
        refreshMediaStatus()
        ensureNotificationPermission()
        appTab = defaultTab
        playlistStore = PlaylistStore(applicationContext)
        playerController = PlayerUiControllerAdapter(
            trackCache,
            onProFeatureLocked = { reason ->
                proUpsellReason = reason
                showProUpsell = true
            },
            radioFetcher = DirectBridge.radioFetcher(),
        )
        // Store model: no ads are ever shown, so the ads SDK is never
        // initialized.
        // Re-derives Pro from the flavor's license check on every launch
        // so a patched boolean can never survive; also runs the tamper
        // sweep. (The Play build has no Pro tier: always false there.)
        CrashReporter.milestone(this, "before refreshEntitlement")
        LicenseGuard.refreshEntitlement(this)
        CrashReporter.milestone(this, "after refreshEntitlement")
        handleIntent(intent)

        setContent {
            TuneGrabTheme {
                val isPro by AppSettings.isPro.collectAsState()

                // A completed Pro purchase dismisses any paywall.
                LaunchedEffect(isPro) {
                    if (isPro) {
                        showProUpsell = false
                    }
                }

                // ---- Hoisted main-content state ----
                // The Settings screen swaps out the whole main-content subtree
                // below; anything remember()ed inside it — notably the player
                // controller and its queue — was destroyed when opening Settings,
                // which made the mini player vanish on return. Declaring it here
                // lets it survive Settings (and any future top-level screen).
                // (appTab, playerController, playlistStore, and trackCache now
                // live as activity fields so flavor subclasses can reach them.)
                var npTheme by remember { mutableStateOf(NpTheme.CLEAN) }
                val mediaRepo = remember { MediaStoreRepository(applicationContext) }
                val libraryDataSource = remember {
                    MediaStoreLibraryDataSource(mediaRepo, trackCache)
                }
                // Playlists, favorites, stats. Hoisted here (not inside a
                // branch) so both the player subtree and the Library screen
                // share one instance.
                val favoritesStore = remember { FavoritesStore(applicationContext) }
                val statsStore = remember { ListeningStatsStore(applicationContext) }
                // (playlistStore is hoisted above so the Library screen
                // shares the same instance.)
                val detailsLoader = remember { SongDetailsLoader(applicationContext, mediaRepo) }
                val outerScope = rememberCoroutineScope()
                // Song chosen for the details sheet.
                var detailsSong by remember { mutableStateOf<SongUi?>(null) }
                var detailsData by remember { mutableStateOf<SongDetails?>(null) }
                var detailsLoading by remember { mutableStateOf(false) }
                // Delete flow for the player (library rows use their own).
                var playerDeleteSong by remember { mutableStateOf<SongUi?>(null) }
                var playerDeleteError by remember { mutableStateOf<String?>(null) }
                var playerConsentSong by remember { mutableStateOf<SongUi?>(null) }

                if (showSettings) {
                    // Back gesture closes Settings instead of the app.
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        onBack = { showSettings = false },
                        onRequirePro = { reason ->
                            proUpsellReason = reason
                            showProUpsell = true
                        }
                    )
                } else {
                    // Back gesture walks back through the UI layers: Now
                    // Playing closes first, then the flavor handles tab back
                    // (Library -> Download in the direct build); otherwise
                    // back exits the app. (Now Playing is registered last
                    // so it wins.)
                    BackHandler(enabled = !showNowPlaying) {
                        if (!onTabBackPressed()) finish()
                    }
                    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

                    suspend fun songUiForTrack(): SongUi? {
                        val id = playerController.current.value?.id ?: return null
                        return runCatching {
                            libraryDataSource.songsByIds(listOf(id)).firstOrNull()
                        }.getOrNull()
                    }

                    // Oto-style quality line under the seekbar, refreshed per track.
                    val currentTrack by playerController.current.collectAsState()
                    var audioInfoLine by remember { mutableStateOf<String?>(null) }
                    // Streams from flat YouTube Music rows carry no uploader,
                    // so the artist starts as the unknown placeholder. Resolve
                    // the video's real channel once per video in the
                    // background and patch it into the queue; Now Playing,
                    // the mini player, and lyrics all pick it up.
                    // (Direct build only: the bridge returns null in the
                    // Play build, where remote tracks never exist.)
                    val resolvedArtists = remember { mutableSetOf<String>() }
                    LaunchedEffect(currentTrack?.queueKey) {
                        val track = currentTrack
                        val videoId = track?.remoteVideoId
                        if (track != null && track.isRemote &&
                            track.artist == SongUi.UNKNOWN_ARTIST &&
                            videoId != null && resolvedArtists.add(videoId)
                        ) {
                            val realArtist = withContext(Dispatchers.IO) {
                                DirectBridge.fetchUploader(videoId)
                            }
                            if (!realArtist.isNullOrBlank() &&
                                realArtist != SongUi.UNKNOWN_ARTIST
                            ) {
                                playerController.updateTrackArtist(track.queueKey, realArtist)
                            }
                        }
                    }
                    LaunchedEffect(currentTrack?.id) {
                        val track = currentTrack
                        audioInfoLine = null
                        if (track != null) {
                            audioInfoLine = if (track.isRemote) {
                                // Streaming from YouTube Music: no local
                                // file metadata to show.
                                "Streaming"
                            } else {
                                runCatching {
                                    detailsLoader.qualityLine(track.id)
                                }.getOrNull()
                            }
                        }
                    }

                    fun showDetailsForCurrent() {
                        outerScope.launch {
                            val song = songUiForTrack() ?: return@launch
                            detailsSong = song
                            detailsLoading = true
                            detailsData = runCatching {
                                detailsLoader.load(song.id)
                            }.getOrNull()
                            detailsLoading = false
                        }
                    }

                    val playerConsentLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { result ->
                        val song = playerConsentSong
                        playerConsentSong = null
                        if (result.resultCode == Activity.RESULT_OK && song != null) {
                            playerController.next()
                        }
                    }

                    fun performPlayerDelete(song: SongUi) {
                        outerScope.launch {
                            when (val r = libraryDataSource.deleteSong(song.id)) {
                                is DeleteResult.Deleted -> {
                                    // Move off the deleted track.
                                    playerController.next()
                                }
                                is DeleteResult.NeedsConsent -> {
                                    playerConsentSong = song
                                    runCatching {
                                        playerConsentLauncher.launch(
                                            IntentSenderRequest.Builder(r.intentSender).build()
                                        )
                                    }.onFailure {
                                        playerConsentSong = null
                                        playerDeleteError = "Couldn't open the system delete dialog."
                                    }
                                }
                                is DeleteResult.Failed ->
                                    playerDeleteError = r.message
                            }
                        }
                    }

                    // Incremental library rescan when entering the Library tab.
                    LaunchedEffect(appTab, mediaGranted) {
                        if (appTab == AppTab.LIBRARY && mediaGranted) {
                            runCatching {
                                if (LibraryScanner.needsRescan(applicationContext)) {
                                    LibraryScanner.performRescan(applicationContext)
                                }
                            }
                        }
                    }

                    if (showNowPlaying) {
                        NowPlayingScreen(
                            controller = playerController,
                            theme = npTheme,
                            isPro = isPro,
                            onClose = { showNowPlaying = false },
                            audioInfoLine = audioInfoLine,
                            onShowDetails = { showDetailsForCurrent() },
                            onAddToPlaylist = {
                                // Works for both local and streaming tracks;
                                // the sheet accepts either.
                                playerController.current.value?.let { addSheetSong = it }
                            },
                            onDeleteCurrent = {
                                // The Delete row is hidden for streaming
                                // tracks (no local file); stay defensive.
                                playerController.current.value
                                    ?.takeIf { !it.isRemote }
                                    ?.let { playerDeleteSong = it }
                            },
                            onGoToArtist = {
                                playerController.current.value?.let { track ->
                                    openArtist(track.artist, track.remoteVideoId)
                                }
                            },
                            onSaveRadio = { saveRadioAsPlaylist() },
                            onDownloadSong = { song -> onDownloadSongRequested(song) },
                            favoritesStore = favoritesStore,
                        )
                    } else {
                        // Explicit theme background on the root: without it
                        // the Activity's window background shows through and
                        // screens render on the wrong color (charcoal instead
                        // of maroon).
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                // Status-bar inset handled once here so no
                                // screen draws its header under the clock.
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .statusBarsPadding()
                                ) {
                                    when (appTab) {
                                        AppTab.HOME -> HomeScreen(
                                            dataSource = libraryDataSource,
                                            statsStore = statsStore,
                                            onPlaySong = { song ->
                                                playerController.playQueue(listOf(song), 0)
                                            },
                                            onPlaySongs = { songs ->
                                                playerController.playQueue(songs, 0)
                                            },
                                            onOpenAlbum = { openAlbum(it) },
                                            onOpenArtist = { openArtist(it, null) },
                                            showPasteCard = homeShowPasteCard,
                                            onPasteLink = ::onPasteLink,
                                            pasteCardExtra = { HomePasteExtra() },
                                            showSettingsGear = homeShowSettingsGear,
                                            onOpenSettings = { showSettings = true },
                                        )
                                        AppTab.LIBRARY -> LibraryTabContent(
                                            mediaGranted = mediaGranted,
                                            dataSource = libraryDataSource,
                                            playerController = playerController,
                                            onRequestPermission = ::requestMediaPermission,
                                            playlistStore = playlistStore,
                                            onAddToPlaylist = { addSheetSong = it },
                                            revealSongId = revealSongId,
                                            onRevealConsumed = { revealSongId = null },
                                            onDownloadStarted = { onDownloadStarted() },
                                            albumTarget = albumTarget,
                                            onAlbumTargetConsumed = ::onAlbumTargetConsumed,
                                            favoritesStore = favoritesStore,
                                            onDownloadRemoteEntry = ::onDownloadRemoteEntry,
                                            isRemoteEntryQueued = ::isRemoteEntryQueued,
                                        )
                                        AppTab.ACTIVITY -> ActivityScreen(
                                            dataSource = libraryDataSource,
                                            statsStore = statsStore,
                                            mediaGranted = mediaGranted,
                                            downloadSections = { ActivityDownloadSections() },
                                            onPlaySong = { song ->
                                                playerController.playQueue(listOf(song), 0)
                                            },
                                        )
                                        // Download / Search tabs live in the
                                        // flavor's MainActivity subclass.
                                        else -> FlavorTabContent(appTab)
                                    }
                                }
                                MiniPlayer(
                                    controller = playerController,
                                    onExpand = { showNowPlaying = true }
                                )
                                NavigationBar(
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    for (tab in navTabs) {
                                        val (icon, label) = when (tab) {
                                            AppTab.HOME -> Icons.Filled.Home to "Home"
                                            AppTab.SEARCH -> Icons.Filled.Search to "Search"
                                            AppTab.LIBRARY -> Icons.Filled.LibraryMusic to "Library"
                                            AppTab.ACTIVITY -> Icons.Filled.History to "Activity"
                                            AppTab.DOWNLOAD -> continue
                                        }
                                        NavigationBarItem(
                                            selected = appTab == tab,
                                            onClick = { appTab = tab },
                                            icon = {
                                                Icon(
                                                    icon,
                                                    contentDescription = null
                                                )
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                    if (showSettingsNavItem) {
                                        NavigationBarItem(
                                            selected = showSettings,
                                            onClick = { showSettings = true },
                                            icon = {
                                                Icon(
                                                    Icons.Filled.Settings,
                                                    contentDescription = null
                                                )
                                            },
                                            label = { Text("Settings") }
                                        )
                                    }
                                }
                            }
                            SnackbarHost(
                                hostState = snackState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 96.dp),
                            )
                        }
                    }

                    // Full-screen flavor overlays above everything (online
                    // artist/album screens in the direct build).
                    FlavorOverlays()

                    playerDeleteSong?.let { song ->
                        AlertDialog(
                            onDismissRequest = { playerDeleteSong = null },
                            title = { Text("Delete song?") },
                            text = {
                                Text("\"${song.title}\" will be permanently removed from your device.")
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        playerDeleteSong = null
                                        performPlayerDelete(song)
                                    }
                                ) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { playerDeleteSong = null }) { Text("Cancel") }
                            },
                        )
                    }
                    playerDeleteError?.let { msg ->
                        AlertDialog(
                            onDismissRequest = { playerDeleteError = null },
                            text = { Text(msg) },
                            confirmButton = {
                                TextButton(onClick = { playerDeleteError = null }) { Text("OK") }
                            },
                        )
                    }
                }

                // Oto-style sheets shared by the library and the player.
                addSheetSong?.let { song ->
                    AddToPlaylistSheet(
                        song = song,
                        store = playlistStore,
                        onDismiss = { addSheetSong = null },
                        onAdded = { addSheetSong = null },
                    )
                }
                detailsSong?.let { song ->
                    SongDetailsSheet(
                        details = detailsData,
                        artworkKey = song.artworkKey,
                        loading = detailsLoading,
                        onDismiss = {
                            detailsSong = null
                            detailsData = null
                        },
                        onGoToArtist = {
                            val name = detailsData?.artist?.takeIf { it.isNotBlank() }
                            detailsSong = null
                            detailsData = null
                            if (name != null) openArtist(name, null)
                        },
                    )
                }

                if (showProUpsell) {
                    ProUpsellSlot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Pro Phase 1: connect the Media3 controller for playback.
        PlayerManager.connect(this)
    }

    override fun onStop() {
        super.onStop()
        PlayerManager.disconnect()
    }

    override fun onResume() {
        super.onResume()
        refreshStorageStatus()
        // Re-checks entitlement when returning from the Play purchase flow
        // (throttled — cheap when nothing changed).
        LicenseGuard.refreshEntitlement(this)
    }

    // ---------------- storage permission ----------------

    private fun refreshStorageStatus() {
        storageGranted = when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            else -> true // API 29–32: MediaStore writes need no permission
        }
    }

    protected fun requestStoragePermission() {
        val perms = when {
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> emptyArray()
        }
        if (perms.isEmpty()) {
            refreshStorageStatus()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    /** Android 13+: the foreground service's progress notification needs this. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    // ---------------- library (Pro Phase 1) ----------------

    private fun mediaPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun refreshMediaStatus() {
        mediaGranted = ContextCompat.checkSelfPermission(
            this, mediaPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMediaPermission() {
        mediaPermissionLauncher.launch(mediaPermission())
    }
}

// ------------------------------------------------------------------ UI ---