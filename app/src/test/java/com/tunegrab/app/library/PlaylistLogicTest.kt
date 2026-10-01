package com.tunegrab.app.library

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure playlist-manipulation helpers: duplicates are
 * rejected, insertion order is preserved, unknown ids fail cleanly, remote
 * entries round-trip through JSON, and downloaded files link back to
 * remote entries.
 */
class PlaylistLogicTest {

    private fun playlist(id: String, vararg songIds: Long) = Playlist(
        id = id,
        name = id,
        entries = songIds.map { PlaylistEntry.Local(it) },
        createdAtMs = 0L,
    )

    private fun remotePlaylist(id: String) = Playlist(
        id = id,
        name = id,
        entries = listOf(
            PlaylistEntry.Remote("vid1", "Song One", "Artist A", "https://thumb/1.jpg", 180L),
            PlaylistEntry.Local(7L),
        ),
        createdAtMs = 0L,
    )

    @Test
    fun `adding a new song appends it and returns true`() {
        val (updated, added) = addSongToPlaylist(
            listOf(playlist("p", 1L, 2L)), "p", 3L
        )
        assertTrue(added)
        assertEquals(listOf(1L, 2L, 3L), updated.single { it.id == "p" }.songIds)
    }

    @Test
    fun `adding a duplicate is rejected and the list is unchanged`() {
        val before = listOf(playlist("p", 1L, 2L))
        val (updated, added) = addSongToPlaylist(before, "p", 2L)
        assertFalse(added)
        assertEquals(before, updated)
    }

    @Test
    fun `adding to an unknown playlist id fails cleanly`() {
        val before = listOf(playlist("p", 1L))
        val (updated, added) = addSongToPlaylist(before, "nope", 9L)
        assertFalse(added)
        assertEquals(before, updated)
    }

    @Test
    fun `other playlists are untouched`() {
        val (updated, added) = addSongToPlaylist(
            listOf(playlist("a", 1L), playlist("b", 2L)), "b", 3L
        )
        assertTrue(added)
        assertEquals(listOf(1L), updated.single { it.id == "a" }.songIds)
        assertEquals(listOf(2L, 3L), updated.single { it.id == "b" }.songIds)
    }

    @Test
    fun `repeated adds keep insertion order without duplicates`() {
        var lists = listOf(playlist("p"))
        val results = mutableListOf<Boolean>()
        for (id in listOf(5L, 3L, 5L, 1L, 3L)) {
            val (next, added) = addSongToPlaylist(lists, "p", id)
            lists = next
            results += added
        }
        assertEquals(listOf(5L, 3L, 1L), lists.single().songIds)
        // First occurrences added, repeats rejected.
        assertEquals(listOf(true, true, false, true, false), results)
    }

    @Test
    fun `adding a duplicate remote video is rejected`() {
        val before = listOf(remotePlaylist("p"))
        val entry = PlaylistEntry.Remote("vid1", "Song One", "Artist A", null, null)
        val (updated, added) = addEntryToPlaylist(before, "p", entry)
        assertFalse(added)
        assertEquals(before, updated)
    }

    @Test
    fun `local and remote entries can mix in insertion order`() {
        val (updated, added) = addEntryToPlaylist(
            listOf(remotePlaylist("p")), "p",
            PlaylistEntry.Local(9L)
        )
        assertTrue(added)
        val entries = updated.single { it.id == "p" }.entries
        assertEquals(3, entries.size)
        assertTrue(entries[2] is PlaylistEntry.Local)
    }

    @Test
    fun `entries survive a JSON round trip`() {
        val before = listOf(remotePlaylist("p"))
        val json = serializeEntries(before.single().entries).toString()
        val after = deserializeEntries(JSONArray(json))
        assertEquals(before.single().entries, after)
    }

    @Test
    fun `linking a download flips the remote entry to local`() {
        val before = remotePlaylist("p").entries
        val after = linkEntryLocal(before, "vid1", "content://media/audio/42")
        val remote = after.filterIsInstance<PlaylistEntry.Remote>().single { it.videoId == "vid1" }
        assertEquals("content://media/audio/42", remote.localContentUri)
        assertTrue(remote.isDownloaded)
        // The local entry is untouched.
        assertEquals(before.filterIsInstance<PlaylistEntry.Local>(), after.filterIsInstance<PlaylistEntry.Local>())
    }

    @Test
    fun `linking an unknown video id changes nothing`() {
        val before = remotePlaylist("p").entries
        assertEquals(before, linkEntryLocal(before, "nope", "content://x"))
    }

    @Test
    fun `legacy songs array deserializes to local entries`() {
        // Simulates a playlist written by the pre-unified format.
        val arr = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "local")
                put("songId", 3L)
            })
        }
        val entries = deserializeEntries(arr)
        assertEquals(listOf(PlaylistEntry.Local(3L)), entries)
    }
}
