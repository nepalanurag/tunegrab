package com.tunegrab.app.ui.player

import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an album-art thumbnail for [artworkKey] — the string form of the
 * *song's own* content Uri — via ContentResolver.loadThumbnail, with an
 * in-memory LRU cache. On API 29+ this returns the file's embedded cover art.
 *
 * Streaming tracks pass an https thumbnail URL instead; those are fetched
 * over the network and cached the same way.
 *
 * No new dependencies (no Coil). Falls back to a tonal Material icon when
 * [artworkKey] is null, the Uri can't be parsed/loaded, or the device is
 * below API 29 (loadThumbnail requires Q+).
 */
@Composable
fun ArtworkImage(
    artworkKey: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    // artworkKey is null for items without art (e.g. artists) — never pass
    // null into LruCache.get(), which throws NullPointerException.
    var bitmap by remember(artworkKey) {
        mutableStateOf<ImageBitmap?>(artworkKey?.let { artworkCache.get(it) })
    }

    LaunchedEffect(artworkKey) {
        if (artworkKey == null || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                if (artworkKey.startsWith("http")) {
                    loadHttpBitmap(artworkKey)?.asImageBitmap()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = Uri.parse(artworkKey)
                    context.contentResolver
                        .loadThumbnail(uri, Size(512, 512), null)
                        .asImageBitmap()
                } else null
            }.getOrNull()
        }
        if (loaded != null) {
            artworkCache.put(artworkKey, loaded)
            bitmap = loaded
        }
    }

    val art = bitmap
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        }
    }
}

private val artworkCache = object : LruCache<String, ImageBitmap>(64) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        value.width * value.height * 4 / 1024 // KB
}

/** Fetches a remote thumbnail (YouTube artwork) as a Bitmap. Never throws. */
private fun loadHttpBitmap(url: String): android.graphics.Bitmap? {
    return try {
        val connection = java.net.URL(url).openConnection()
            as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = true
        connection.connect()
        if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) return null
        connection.inputStream.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        }
    } catch (_: Exception) {
        null
    }
}

@Preview(showBackground = true, widthDp = 120, heightDp = 120)
@Composable
private fun ArtworkImageFallbackPreview() {
    MaterialTheme {
        ArtworkImage(artworkKey = null, modifier = Modifier.fillMaxSize())
    }
}
