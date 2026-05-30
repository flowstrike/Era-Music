package com.spyou.eramusic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Playlist::class, PlaylistSong::class],
    version = 1,
    exportSchema = true,
)
abstract class EraDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: EraDatabase? = null

        fun get(context: Context): EraDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): EraDatabase =
            Room.databaseBuilder(context.applicationContext, EraDatabase::class.java, "era.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Seed the reserved Favorites playlist.
                        db.execSQL("INSERT INTO playlists (name, isFavorites) VALUES ('Favorites', 1)")
                    }
                })
                .build()
    }
}
