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
import java.io.File
import java.security.MessageDigest

/**
 * Loads an album-art thumbnail for [artworkKey] — the string form of the
 * *song's own* content Uri — via ContentResolver.loadThumbnail, with an
 * in-memory LRU cache. On API 29+ this returns the file's embedded cover art.
 *
 * Streaming tracks pass an https thumbnail URL instead; those are fetched
 * over the network and cached in memory plus on disk (app cache dir, so
 * photos survive restarts and stay visible offline).
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
                    loadHttpBitmapCached(context, artworkKey)?.asImageBitmap()
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

/** Raw bytes of a URL, or null. Never throws. */
private fun downloadUrlBytes(url: String): ByteArray? {
    return try {
        val connection = java.net.URL(url).openConnection()
            as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = true
        connection.connect()
        if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) return null
        connection.inputStream.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}

/**
 * Remote thumbnail with a disk cache: memory LRU is still checked first
 * by the caller; here disk is checked before the network, and fresh
 * downloads are written to disk for offline use. Never throws.
 */
private fun loadHttpBitmapCached(
    context: android.content.Context,
    url: String,
): android.graphics.Bitmap? {
    val dir = File(context.cacheDir, "artwork").apply { mkdirs() }
    val file = ArtworkDiskCache.fileFor(dir, url)
    if (file.exists()) {
        // Touch for LRU ordering; a corrupt entry falls through to network.
        file.setLastModified(System.currentTimeMillis())
        runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()?.let { return it }
        runCatching { file.delete() }
    }
    val bytes = downloadUrlBytes(url) ?: return null
    runCatching {
        file.writeBytes(bytes)
        ArtworkDiskCache.enforceCap(dir)
    }
    return runCatching {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * Disk cache for remote artwork, keyed by SHA-256 of the URL. Pure
 * helpers ([keyFor], [planEviction]) are unit-tested on the JVM.
 */
internal object ArtworkDiskCache {
    const val MAX_BYTES = 100L * 1024 * 1024

    /** Stable file name for a URL: hex SHA-256. Pure. */
    fun keyFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(url.toByteArray(Charsets.UTF_8))
        return buildString(hash.size * 2) {
            for (b in hash) append("%02x".format(b.toInt() and 0xff))
        }
    }

    fun fileFor(dir: File, url: String): File = File(dir, keyFor(url) + ".img")

    /**
     * Oldest-first eviction plan: the files to delete so the rest fit
     * under [maxBytes]. Entries are (file, sizeBytes, lastModifiedMs).
     * Pure.
     */
    fun planEviction(
        entries: List<Triple<File, Long, Long>>,
        maxBytes: Long,
    ): List<File> {
        var total = entries.sumOf { it.second }
        if (total <= maxBytes) return emptyList()
        val toDelete = mutableListOf<File>()
        for ((file, size, _) in entries.sortedBy { it.third }) {
            if (total < maxBytes) break
            toDelete.add(file)
            total -= size
        }
        return toDelete
    }

    /** Deletes oldest files until the dir fits under the cap. Never throws. */
    fun enforceCap(dir: File, maxBytes: Long = MAX_BYTES) {
        val entries = dir.listFiles()
            ?.map { Triple(it, it.length(), it.lastModified()) }
            ?: return
        for (f in planEviction(entries, maxBytes)) {
            runCatching { f.delete() }
        }
    }
}

@Preview(showBackground = true, widthDp = 120, heightDp = 120)
@Composable
private fun ArtworkImageFallbackPreview() {
    MaterialTheme {
        ArtworkImage(artworkKey = null, modifier = Modifier.fillMaxSize())
    }
}
