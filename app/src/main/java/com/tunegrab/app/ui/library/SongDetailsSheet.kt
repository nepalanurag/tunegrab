package com.tunegrab.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunegrab.app.BuildConfig
import com.tunegrab.app.library.SongDetails
import com.tunegrab.app.library.SongDetailsLoader
import com.tunegrab.app.ui.components.ProBadge
import com.tunegrab.app.ui.player.ArtworkImage
import com.tunegrab.app.ui.player.formatDurationMs

/**
 * Oto-style song details: quality badge, stat cards, FILE / ARTWORK / TAGS
 * sections. Opened from the player's overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(
    details: SongDetails?,
    artworkKey: String?,
    loading: Boolean = false,
    onDismiss: () -> Unit,
    /** Opens the artist's YouTube Music profile; null hides the button. */
    onGoToArtist: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (loading || details == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // Header: art + title + artist.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArtworkImage(
                    artworkKey = artworkKey,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentDescription = "Album art",
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        details.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(details.artist, details.album)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            details.qualityBadge?.let { badge ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Play build: the online artist screen is a locked Pro teaser.
            // Hidden entirely in the F-Droid build (no Pro labels).
            if (onGoToArtist != null && details.artist.isNotBlank() && BuildConfig.FLAVOR != "fdroid") {
                Spacer(Modifier.height(12.dp))
                val artistLocked = !BuildConfig.INCLUDE_DOWNLOADER
                OutlinedButton(
                    onClick = onGoToArtist,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View ${details.artist} on YouTube Music")
                    if (artistLocked) {
                        Spacer(Modifier.width(8.dp))
                        ProBadge()
                    }
                }
            }

            // Stat cards: format / sample rate / depth / channels.
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    value = details.format,
                    label = "Format",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = details.sampleRateHz
                        ?.let { SongDetailsLoader.formatSampleRate(it) } ?: "-",
                    label = "Sample rate",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = details.bitDepth?.toString() ?: "-",
                    label = "Bit depth",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = details.channels?.toString() ?: "-",
                    label = "Channels",
                    modifier = Modifier.weight(1f),
                )
            }

            // FILE section.
            Spacer(Modifier.height(20.dp))
            DetailSection("FILE") {
                DetailRow("File name", details.fileName)
                DetailRow("File path", details.filePath.ifEmpty { "-" })
                Row(Modifier.fillMaxWidth()) {
                    DetailCell(
                        "Size",
                        SongDetailsLoader.formatFileSize(details.sizeBytes),
                        Modifier.weight(1f),
                    )
                    DetailCell(
                        "Bitrate",
                        details.bitrateKbps?.let { "$it kb/s" } ?: "-",
                        Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    DetailCell(
                        "Length",
                        formatDurationMs(details.durationMs),
                        Modifier.weight(1f),
                    )
                    DetailCell(
                        "Sampling rate",
                        details.sampleRateHz?.let { "$it Hz" } ?: "-",
                        Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    DetailCell(
                        "Date added",
                        SongDetailsLoader.formatDateTime(details.dateAddedSec),
                        Modifier.weight(1f),
                    )
                    DetailCell(
                        "Last modified",
                        SongDetailsLoader.formatDateTime(details.dateModifiedSec),
                        Modifier.weight(1f),
                    )
                }
            }

            // ARTWORK section (only when the file has embedded art).
            if (details.artWidth != null || details.artFormat != null) {
                Spacer(Modifier.height(20.dp))
                DetailSection("ARTWORK") {
                    Row(Modifier.fillMaxWidth()) {
                        DetailCell(
                            "Dimensions",
                            if (details.artWidth != null && details.artHeight != null)
                                "${details.artWidth} × ${details.artHeight}"
                            else "-",
                            Modifier.weight(1f),
                        )
                        DetailCell(
                            "Format",
                            details.artFormat ?: "-",
                            Modifier.weight(1f),
                        )
                        DetailCell(
                            "Size",
                            details.artSizeBytes
                                ?.let { SongDetailsLoader.formatFileSize(it) } ?: "-",
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            // TAGS section.
            Spacer(Modifier.height(20.dp))
            DetailSection("TAGS") {
                DetailRow("Title", details.title)
                DetailRow("Artist", details.artist)
                DetailRow("Album", details.album)
                details.genre?.let { DetailRow("Genre", it) }
                if (details.year > 0) DetailRow("Year", details.year.toString())
                if (details.trackNumber > 0) {
                    DetailRow("Track", (details.trackNumber % 1000).toString())
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DetailCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
