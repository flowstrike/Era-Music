package com.spyou.eramusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSortSearchTest {

    private fun song(
        title: String,
        artist: String = "",
        album: String = "",
        duration: Long = 0,
        date: Long = 0,
    ) = Song(
        id = title.hashCode().toLong(),
        title = title,
        artist = artist,
        album = album,
        durationMs = duration,
        albumId = 0,
        dateAddedSec = date,
    )

    @Test
    fun sortByTitleIsAlphabeticalCaseInsensitive() {
        val list = listOf(song("banana"), song("Apple"), song("cherry"))
        val sorted = MusicRepository.sort(list, SortOrder.TITLE).map { it.title }
        assertEquals(listOf("Apple", "banana", "cherry"), sorted)
    }

    @Test
    fun sortByDurationAscending() {
        val list = listOf(song("a", duration = 300), song("b", duration = 100), song("c", duration = 200))
        val sorted = MusicRepository.sort(list, SortOrder.DURATION).map { it.title }
        assertEquals(listOf("b", "c", "a"), sorted)
    }

    @Test
    fun sortByDateAddedNewestFirst() {
        val list = listOf(song("old", date = 1), song("new", date = 3), song("mid", date = 2))
        val sorted = MusicRepository.sort(list, SortOrder.DATE_ADDED).map { it.title }
        assertEquals(listOf("new", "mid", "old"), sorted)
    }

    @Test
    fun searchMatchesPartialArtistCaseInsensitive() {
        val list = listOf(
            song("Song A", artist = "The Beatles"),
            song("Song B", artist = "Queen"),
        )
        val result = MusicRepository.search(list, "beat").map { it.title }
        assertEquals(listOf("Song A"), result)
    }

    @Test
    fun blankSearchReturnsAll() {
        val list = listOf(song("a"), song("b"))
        assertEquals(2, MusicRepository.search(list, "  ").size)
    }
}
