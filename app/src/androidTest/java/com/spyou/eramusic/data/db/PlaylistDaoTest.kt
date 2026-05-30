package com.spyou.eramusic.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

    private lateinit var db: EraDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EraDatabase::class.java).build()
        dao = db.playlistDao()
        runBlocking { dao.insertPlaylist(Playlist(name = "Favorites", isFavorites = true)) }
    }

    @After
    fun teardown() = db.close()

    @Test
    fun addAndRemoveSongsUpdatesCount() = runBlocking {
        val id = dao.insertPlaylist(Playlist(name = "Road trip"))
        dao.addSong(id, 1L)
        dao.addSong(id, 2L)
        assertEquals(2, dao.observePlaylists().first().first { it.id == id }.songCount)
        dao.removeSong(id, 1L)
        assertEquals(1, dao.observePlaylists().first().first { it.id == id }.songCount)
    }

    @Test
    fun toggleFavoriteReflectedInObserveIsFavorite() = runBlocking {
        val favId = dao.favoritesId()
        assertFalse(dao.observeIsFavorite(10L).first())
        dao.addSong(favId, 10L)
        assertTrue(dao.observeIsFavorite(10L).first())
        dao.removeSong(favId, 10L)
        assertFalse(dao.observeIsFavorite(10L).first())
    }

    @Test
    fun favoritesPinnedFirstInPlaylistOrder() = runBlocking {
        dao.insertPlaylist(Playlist(name = "AAA"))
        val first = dao.observePlaylists().first().first()
        assertTrue(first.isFavorites)
    }
}
