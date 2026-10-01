package com.tunegrab.app.library

/**
 * Pure library logic: sorting, incremental diffing, and folder naming.
 * Zero Android imports — this file runs on the JVM and is covered by
 * [com.tunegrab.app.library.LibraryLogicTest].
 *
 * [LibraryModels.Song] implements [SongSortable]; the repository sorts in
 * SQL where possible, and this mirrors the same semantics for lists that
 * are already in memory.
 */

/** The sort-relevant fields of a song, free of Android types. */
interface SongSortable {
    val title: String
    val artist: String
    val album: String
    val durationMs: Long
    val dateAddedSec: Long
    val year: Int
}

/** Id-level diff between two snapshots of the library (old -> new). */
data class SongDiff(
    /** Ids present in [new] but not in [old], in [new] order. */
    val added: List<Long>,
    /** Ids present in [old] but not in [new], in [old] order. */
    val removed: List<Long>
)

/**
 * Sorts songs in memory with the same semantics as
 * [MediaStoreRepository.getSongs]' SQL ordering:
 * - TITLE: title, case-insensitive
 * - ARTIST: artist, then album, then title
 * - ALBUM: album, then title
 * - DURATION: shortest first
 * - DATE_ADDED: newest first
 * - YEAR: newest first, then title
 */
fun sortSongs(songs: List<SongSortable>, sortBy: SortBy): List<SongSortable> {
    val byTitle = compareBy(String.CASE_INSENSITIVE_ORDER) { song: SongSortable -> song.title }
    val comparator = when (sortBy) {
        SortBy.TITLE -> byTitle
        SortBy.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: SongSortable ->
            song.artist
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { song: SongSortable ->
            song.album
        }.then(byTitle)
        SortBy.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: SongSortable ->
            song.album
        }.then(byTitle)
        SortBy.DURATION -> compareBy { song: SongSortable -> song.durationMs }
        SortBy.DATE_ADDED -> compareByDescending { song: SongSortable -> song.dateAddedSec }
        SortBy.YEAR -> compareByDescending { song: SongSortable -> song.year }
            .then(byTitle)
    }
    return songs.sortedWith(comparator)
}

/**
 * Computes which song ids were added or removed between two snapshots,
 * for incremental RecyclerView/Compose list updates.
 */
fun diffSongs(old: List<Long>, new: List<Long>): SongDiff {
    val oldSet = old.toSet()
    val newSet = new.toSet()
    return SongDiff(
        added = new.filter { it !in oldSet },
        removed = old.filter { it !in newSet }
    )
}

/**
 * Display name for a folder path: the last path segment.
 * Handles trailing slashes, the filesystem root, and empty input without
 * throwing.
 */
fun folderDisplayName(path: String): String {
    val trimmed = path.trim().trimEnd('/')
    if (trimmed.isEmpty()) {
        // "/" -> "/", "" -> "".
        return path.trim().take(1)
    }
    return trimmed.substringAfterLast('/')
}
