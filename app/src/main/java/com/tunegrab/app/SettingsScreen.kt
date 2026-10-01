package com.tunegrab.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tunegrab.app.library.MediaStoreRepository
import com.tunegrab.app.library.SongTagParser
import com.tunegrab.app.library.SongTagger
import com.tunegrab.app.library.SortBy
import com.tunegrab.app.ui.components.ProBadge
import com.tunegrab.app.ui.settings.EqualizerScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where the free build's "Get TuneGrab Pro" card sends the buyer: the
 * Ko-fi payment page, outside Google Play. The buyer pays there and
 * downloads the Pro APK by direct download.
 *
 * TODO: point this at the TuneGrab Pro shop item once it exists.
 */
internal const val PRO_PAYMENT_URL = "https://ko-fi.com/anuragnepal"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRequirePro: (reason: String) -> Unit
) {
    val filenameStyle by AppSettings.filenameStyle.collectAsState()
    val audioFormat by AppSettings.audioFormat.collectAsState()
    val audioQuality by AppSettings.audioQuality.collectAsState()
    val statsForNerds by AppSettings.statsForNerds.collectAsState()
    val isPro by AppSettings.isPro.collectAsState()
    val skipSilence by AppSettings.skipSilence.collectAsState()
    var showEqualizer by remember { mutableStateOf(false) }
    var showCleanup by remember { mutableStateOf(false) }

    if (showEqualizer) {
        BackHandler { showEqualizer = false }
        EqualizerScreen(onBack = { showEqualizer = false })
        return
    }

    if (showCleanup) {
        CleanupSongInfoDialog(onDismiss = { showCleanup = false })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (!isPro) {
                    PlanCard(
                        isPro = isPro,
                        onGoPro = {
                            onRequirePro("Unlock downloads, Radio, and the sleep timer with one purchase.")
                        }
                    )
                }
            }

            // The Play build has no downloader: instead of the download
            // settings it shows a Pro card that links out to the Ko-fi
            // payment page (outside Google Play).
            if (BuildConfig.INCLUDE_DOWNLOADER) {
            item { SectionHeader("Filename") }
            item {
                SettingsCard {
                    FilenameStyle.entries.forEach { style ->
                        val locked = !AppSettings.isFilenameAllowed(style)
                        OptionRow(
                            title = style.title + if (locked) " · Pro" else "",
                            summary = style.example + ".flac",
                            selected = style == filenameStyle,
                            onSelect = {
                                if (locked) onRequirePro(
                                    "\"${style.title}\" filenames are a Pro feature."
                                )
                                else AppSettings.setFilenameStyle(style)
                            }
                        )
                    }
                }
            }

            item { SectionHeader("Audio format") }
            item {
                SettingsCard {
                    AudioFormat.entries.forEach { format ->
                        val locked = !AppSettings.isFormatAllowed(format)
                        OptionRow(
                            title = format.title + if (locked) " · Pro" else "",
                            summary = format.summary,
                            selected = format == audioFormat,
                            onSelect = {
                                if (locked) onRequirePro(
                                    "${format.title} downloads are a Pro feature."
                                )
                                else AppSettings.setAudioFormat(format)
                            }
                        )
                    }
                    if (!isPro) {
                        Text(
                            "Free plan includes MP3.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            item { SectionHeader("Quality") }
            item {
                SettingsCard {
                    AudioQuality.entries.forEach { quality ->
                        val locked = !AppSettings.isQualityAllowed(quality)
                        OptionRow(
                            title = quality.title + if (locked) " · Pro" else "",
                            summary = quality.summary,
                            selected = quality == audioQuality,
                            onSelect = {
                                if (locked) onRequirePro(
                                    "\"${quality.title}\" quality is a Pro feature."
                                )
                                else AppSettings.setAudioQuality(quality)
                            },
                            enabled = audioFormat != AudioFormat.FLAC
                        )
                    }
                    if (audioFormat == AudioFormat.FLAC) {
                        Text(
                            "FLAC is lossless. Quality is always at maximum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else if (!isPro) {
                        Text(
                            "Free plan includes Medium quality.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            } else {
                item { ProDownloadCard() }
            }

            item { SectionHeader("Playback") }
            item {
                SettingsCard {
                    ToggleRow(
                        title = "Skip silence",
                        summary = "Jump over silent intros and outros. Takes effect when playback restarts.",
                        checked = skipSilence,
                        onCheckedChange = { AppSettings.setSkipSilence(it) }
                    )
                    // The Play build has no Pro tier: the equalizer is a
                    // locked Pro teaser there.
                    val eqLocked = !BuildConfig.INCLUDE_DOWNLOADER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (eqLocked) onRequirePro("The equalizer is a Pro feature.")
                                else showEqualizer = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Equalizer" + if (eqLocked) " · Pro" else "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (eqLocked) "Bass, treble and presets. Pro only."
                                else "Bass, treble and presets. Applies instantly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (eqLocked) ProBadge()
                    }
                }
            }

            if (BuildConfig.INCLUDE_DOWNLOADER) {
            item { SectionHeader("Display") }
            item {
                SettingsCard {
                    ToggleRow(
                        title = "Stats for nerds",
                        summary = "Show download speed and ETA in progress",
                        checked = statsForNerds,
                        onCheckedChange = { AppSettings.setStatsForNerds(it) }
                    )
                }
            }
            }

            // Song-info cleanup is shown in both builds; in the Play build
            // it is a locked Pro teaser.
            item { SectionHeader("Library") }
            item {
                SettingsCard {
                    val cleanupLocked = !BuildConfig.INCLUDE_DOWNLOADER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (cleanupLocked) onRequirePro(
                                    "Cleaning up song info is a Pro feature."
                                )
                                else showCleanup = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Clean up song info" + if (cleanupLocked) " · Pro" else "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Fix artist and title tags on your downloaded songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cleanupLocked) ProBadge()
                    }
                }
            }

            if (BuildConfig.INCLUDE_DOWNLOADER) {
            item {
                Text(
                    "Changes apply to the next download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun PlanCard(isPro: Boolean, onGoPro: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isPro) "Pro unlocked" else "Free plan",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (isPro) "Thanks for supporting the app. Everything is unlocked."
                    else "Music player · downloads, Radio & sleep timer are Pro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isPro) {
                Button(onClick = onGoPro) {
                    Text("Go Pro")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/**
 * Free build only: takes the place of the download settings. Sends the
 * buyer to the Ko-fi payment page (outside Google Play), where they pay
 * and download the Pro APK directly.
 */
@Composable
private fun ProDownloadCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "TuneGrab Pro",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Downloads, Radio, the sleep timer, the equalizer, and the online artist screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(PRO_PAYMENT_URL),
                )
                context.startActivity(intent)
            }) {
                Text("Get Pro")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    title: String,
    summary: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.4f
                )
            )
        }
    }
}
/**
 * One proposed tag fix: the file's current title/artist and what the
 * parser thinks they should be.
 */
private data class TagProposal(
    val filePath: String,
    val mimeType: String,
    val oldTitle: String,
    val oldArtist: String,
    val newTitle: String,
    val newArtist: String,
)

private enum class CleanupPhase { Scanning, Review, Applying, Done }

/**
 * "Clean up song info" flow: scans downloaded songs under the public
 * Music folder, shows every proposed artist/title fix for review, then
 * applies them with progress and re-scans the files so the library
 * picks the new tags up. Only title/artist are written; artwork and
 * everything else in the file is untouched.
 */
@Composable
private fun CleanupSongInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(CleanupPhase.Scanning) }
    var proposals by remember { mutableStateOf<List<TagProposal>>(emptyList()) }
    var skipped by remember { mutableStateOf(0) }
    var applied by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val (found, skipCount) = withContext(Dispatchers.IO) {
            scanTagProposals(context)
        }
        proposals = found
        skipped = skipCount
        phase = CleanupPhase.Review
    }

    fun applyAll() {
        val list = proposals
        total = list.size
        applied = 0
        failed = 0
        phase = CleanupPhase.Applying
        scope.launch(Dispatchers.IO) {
            var ok = 0
            var bad = 0
            list.forEach { p ->
                if (SongTagger.writeTags(File(p.filePath), p.newTitle, p.newArtist)) {
                    ok++
                    MediaScannerConnection.scanFile(
                        context, arrayOf(p.filePath), arrayOf(p.mimeType), null
                    )
                } else {
                    bad++
                }
                applied = ok + bad
            }
            failed = bad
            val summary = buildString {
                append("Updated $ok ${if (ok == 1) "song" else "songs"}")
                if (bad > 0) append(", $bad failed")
                if (skipped > 0) append(" ($skipped skipped)")
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                applied = ok
                failed = bad
                phase = CleanupPhase.Done
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (phase != CleanupPhase.Applying) onDismiss() },
        title = { Text("Clean up song info") },
        text = {
            when (phase) {
                CleanupPhase.Scanning -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Scanning your downloads…")
                }
                CleanupPhase.Review -> {
                    if (proposals.isEmpty()) {
                        Text("Every downloaded song already has clean info.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${proposals.size} ${if (proposals.size == 1) "song" else "songs"} can be cleaned up:"
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(proposals.take(60)) { p ->
                                    Column {
                                        Text(
                                            "\"${p.oldTitle}\" — ${p.oldArtist.ifBlank { "Unknown artist" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "→ \"${p.newTitle}\" — ${p.newArtist}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                if (proposals.size > 60) {
                                    item {
                                        Text(
                                            "+ ${proposals.size - 60} more",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (skipped > 0) {
                                Text(
                                    "$skipped ${if (skipped == 1) "file" else "files"} skipped " +
                                        "(already clean or unreadable).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                CleanupPhase.Applying -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) applied.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Updating $applied of $total…")
                }
                CleanupPhase.Done -> {
                    val summary = buildString {
                        append("Updated $applied ${if (applied == 1) "song" else "songs"}")
                        if (failed > 0) append(", $failed failed")
                        if (skipped > 0) append(" ($skipped skipped)")
                    }
                    Text(summary)
                }
            }
        },
        confirmButton = {
            when (phase) {
                CleanupPhase.Review ->
                    if (proposals.isNotEmpty()) {
                        TextButton(onClick = ::applyAll) {
                            Text("Update ${proposals.size} ${if (proposals.size == 1) "song" else "songs"}")
                        }
                    }
                CleanupPhase.Done -> TextButton(onClick = onDismiss) { Text("Close") }
                else -> {}
            }
        },
        dismissButton = {
            if (phase == CleanupPhase.Review) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * Scans the on-device library for downloaded songs whose tags look like
 * raw yt-dlp output, returning the proposed fixes plus a skipped count.
 * Skips files that are unreadable, already clean, or where the parse is
 * not confident.
 */
private suspend fun scanTagProposals(context: Context): Pair<List<TagProposal>, Int> {
    if (!MediaStoreRepository.hasPermission(context)) return emptyList<TagProposal>() to 0
    @Suppress("DEPRECATION")
    val musicDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_MUSIC
    ).absolutePath
    val repo = MediaStoreRepository(context)
    val songs = repo.getSongs(MediaStoreRepository.SONGS_ALL, 0, SortBy.TITLE)
    val proposals = mutableListOf<TagProposal>()
    var skipped = 0
    for (song in songs) {
        val path = song.filePath
        if (path.isBlank() || !path.startsWith(musicDir)) continue
        val file = File(path)
        if (!file.isFile || !file.canWrite()) {
            skipped++
            continue
        }
        val tags = SongTagger.readTags(file)
        val curTitle = tags?.title ?: song.title
        if (curTitle.isBlank()) {
            skipped++
            continue
        }
        val rawArtist = tags?.artist ?: song.artist
        val curArtist = rawArtist
            .takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            .orEmpty()
        val parsed = SongTagParser.parse(
            rawTitle = curTitle,
            artistTag = curArtist.takeIf { it.isNotEmpty() },
            uploader = curArtist.takeIf { it.isNotEmpty() },
        )
        if (!parsed.confident) {
            skipped++
            continue
        }
        val sameTitle = parsed.title.equals(curTitle, ignoreCase = true)
        val sameArtist = parsed.artist.equals(curArtist, ignoreCase = true)
        if (sameTitle && sameArtist) {
            skipped++
            continue
        }
        proposals.add(
            TagProposal(
                filePath = path,
                mimeType = song.mimeType,
                oldTitle = curTitle,
                oldArtist = curArtist,
                newTitle = parsed.title,
                newArtist = parsed.artist,
            )
        )
    }
    return proposals to skipped
}
