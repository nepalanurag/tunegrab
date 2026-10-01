package com.tunegrab.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesStoreTest {

    @Test fun `remote metadata survives a json round trip`() {
        val input = listOf(
            RemoteFavorite("abc123", "Fireflies", "Owl City", "https://img/abc.jpg"),
            RemoteFavorite("def456", "Closer", "The Chainsmokers", null),
        )
        val decoded = FavoritesStore.decodeRemoteMeta(FavoritesStore.encodeRemoteMeta(input))
        assertEquals(input.toSet(), decoded.toSet())
    }

    @Test fun `blank and corrupt metadata decode to empty`() {
        assertTrue(FavoritesStore.decodeRemoteMeta(null).isEmpty())
        assertTrue(FavoritesStore.decodeRemoteMeta("").isEmpty())
        assertTrue(FavoritesStore.decodeRemoteMeta("not json{{").isEmpty())
    }

    @Test fun `empty list encodes and decodes`() {
        val decoded = FavoritesStore.decodeRemoteMeta(FavoritesStore.encodeRemoteMeta(emptyList()))
        assertTrue(decoded.isEmpty())
    }
}
