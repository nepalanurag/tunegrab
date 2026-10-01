package com.tunegrab.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tunegrab.app.ui.theme.TuneGrabTheme

/**
 * Shimmering placeholder block: the share-sheet resolving state and list
 * loading states. The alpha pulses; the shape comes from the caller.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha),
        ),
    )
}

/**
 * Illustrated empty state: line-art icon, a plain title, one helpful line,
 * and an optional action button. Used for empty library, empty queue,
 * empty history.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** Skeleton for the share-sheet resolving state: art block + two lines. */
@Composable
fun ResolvingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(20.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBox(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    TuneGrabTheme(darkTheme = true) {
        EmptyState(
            icon = Icons.Filled.MusicNote,
            title = "Your library is empty",
            body = "Share a song link to download your first one.",
            actionLabel = "Paste a link",
            onAction = {},
        )
    }
}

/**
 * Converts a listening-stats entry back into a playable [SongUi].
 * Remote (YouTube) entries carry a "yt:"-prefixed key.
 */
fun com.tunegrab.app.library.PlayStat.toSongUi(): com.tunegrab.app.ui.library.SongUi {
    val videoId = key.removePrefix("yt:").takeIf { key.startsWith("yt:") }
    return if (videoId != null) {
        com.tunegrab.app.ui.library.SongUi.remote(videoId, title, artist, artworkKey, null)
    } else {
        com.tunegrab.app.ui.library.SongUi(
            id = key.toLongOrNull() ?: -1L,
            title = title,
            artist = artist,
            album = "",
            durationMs = 0L,
            artworkKey = artworkKey,
        )
    }
}
