package com.tunegrab.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.player.ArtworkImage
import com.tunegrab.app.ui.player.formatDurationMs
import com.tunegrab.app.ui.theme.TuneGrabTheme

/**
 * Playlist card with a stacked-artwork treatment: two offset rounded rects
 * behind the cover suggest a stack of songs. Used in the Library playlists
 * filter.
 */
@Composable
fun PlaylistCard(
    name: String,
    songCount: Int,
    totalDurationMs: Long,
    artworkKey: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            // Back of the stack: two offset tonal plates.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = 8.dp, y = (-8).dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) { Spacer(Modifier.height(120.dp)) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = 4.dp, y = (-4).dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) { Spacer(Modifier.height(120.dp)) }
            ArtworkImage(
                artworkKey = artworkKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentDescription = "Playlist art for $name",
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$songCount songs · ${formatDurationMs(totalDurationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Tall carousel card for Home ("Keep listening", "Recent downloads"):
 * portrait artwork with title/artist below, NomaTune-style proportions.
 */
@Composable
fun CarouselCard(
    title: String,
    subtitle: String,
    artworkKey: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            artworkKey = artworkKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentDescription = "Artwork for $title",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistCardPreview() {
    TuneGrabTheme(darkTheme = true) {
        PlaylistCard(
            name = "Gym mix",
            songCount = 24,
            totalDurationMs = 5_400_000L,
            artworkKey = null,
            onClick = {},
            modifier = Modifier.width(180.dp).padding(16.dp),
        )
    }
}
