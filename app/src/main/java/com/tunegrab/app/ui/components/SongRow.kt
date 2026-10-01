package com.tunegrab.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.library.SongUi
import com.tunegrab.app.ui.player.ArtworkImage
import com.tunegrab.app.ui.player.formatDurationMs
import com.tunegrab.app.ui.theme.TuneGrabTheme

/**
 * The workhorse row of the app: 56dp rounded artwork, title, artist,
 * duration, overflow menu. Used in Library songs, album details, search
 * results, queue, and history.
 */
@Composable
fun SongRow(
    song: SongUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOverflow: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        ArtworkImage(
            artworkKey = song.artworkKey,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentDescription = "Album art",
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDurationMs(song.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (onOverflow != null) {
            IconButton(onClick = onOverflow, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * SongRow with a checkbox: the share-sheet collection flow and the
 * add-to-playlist flow. Tapping the row toggles the checkbox.
 */
@Composable
fun SelectableSongRow(
    song: SongUi,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SongRow(
        song = song,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        leading = {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(end = 4.dp),
            )
        },
    )
}

private fun previewSong() = SongUi(
    id = 1L,
    title = "Deja Vu",
    artist = "Olivia Rodrigo",
    album = "SOUR",
    durationMs = 210_000L,
    artworkKey = null,
)

@Preview(showBackground = true)
@Composable
private fun SongRowPreview() {
    TuneGrabTheme(darkTheme = true) {
        Column {
            SongRow(song = previewSong(), onClick = {}, onOverflow = {})
            SelectableSongRow(song = previewSong(), checked = true, onCheckedChange = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SongRowLightPreview() {
    TuneGrabTheme(darkTheme = false) {
        Column {
            SongRow(song = previewSong(), onClick = {}, onOverflow = {})
        }
    }
}
