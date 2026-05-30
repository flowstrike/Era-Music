package com.spyou.eramusic.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Playlist::class,
        PlaylistSong::class,
        SpotifyPlaylistEntity::class,
        DownloadedTrackEntity::class,
        PlaylistTrackCrossRef::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class EraDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: EraDatabase? = null

        fun get(context: Context): EraDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `spotify_playlists` (
                        `spotifyId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `artworkUrl` TEXT,
                        `trackCount` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`spotifyId`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `downloaded_tracks` (
                        `spotifyId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `localFilePath` TEXT NOT NULL,
                        `artworkUrl` TEXT,
                        `downloadStatus` TEXT NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`spotifyId`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_track_cross_ref` (
                        `spotifyPlaylistId` TEXT NOT NULL,
                        `spotifyTrackId` TEXT NOT NULL,
                        PRIMARY KEY(`spotifyPlaylistId`, `spotifyTrackId`)
                    )
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun build(context: Context): EraDatabase =
            Room.databaseBuilder(context.applicationContext, EraDatabase::class.java, "era.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO playlists (name, isFavorites) VALUES ('Favorites', 1)")
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
