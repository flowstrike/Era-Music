# Spotify Playlist Offline Download — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Download 3 Spotify playlists to internal storage for fully offline playback inside the app.

**Architecture:** Spotify Web API fetches track metadata, NewPipeExtractor searches YouTube and extracts audio stream URLs, audio files download to `context.filesDir/music/`. Room tracks sync state. WorkManager handles periodic re-sync. All tracks playable through existing Media3/ExoPlayer pipeline.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Media3/ExoPlayer, NewPipeExtractor, OkHttp, Gson, WorkManager, Coil

**Spec:** `docs/superpowers/specs/2026-05-30-spotify-playlist-offline-download-design.md`

---

## File Structure

### New files

| File | Purpose |
|------|---------|
| `data/playable/PlayableTrack.kt` | Common interface for playable tracks |
| `data/playable/DownloadedTrack.kt` | Runtime model for downloaded tracks |
| `data/db/SpotifyPlaylistEntity.kt` | Room entity for synced playlists |
| `data/db/DownloadedTrackEntity.kt` | Room entity for downloaded tracks |
| `data/db/PlaylistTrackCrossRef.kt` | Junction table (track ↔ playlist) |
| `data/db/DownloadDao.kt` | DAO for all download-related queries |
| `data/spotify/SpotifyAuthService.kt` | Client Credentials OAuth token management |
| `data/spotify/SpotifyMetadataService.kt` | Fetch playlist + track metadata |
| `data/spotify/SpotifyModels.kt` | Gson data classes for Spotify API JSON |
| `data/youtube/NewPipeDownloader.kt` | OkHttp-based Downloader for NewPipeExtractor |
| `data/youtube/YouTubeDownloadService.kt` | Search YouTube, download audio |
| `data/download/DownloadProgress.kt` | Sealed class for progress state |
| `data/download/SyncOrchestrator.kt` | Full sync pipeline |
| `data/download/SyncWorker.kt` | WorkManager CoroutineWorker |
| `ui/download/DownloadBanner.kt` | Composable for download prompt/progress |
| `ui/download/DownloadViewModel.kt` | ViewModel for download state |
| `ui/playlists/SyncedPlaylistDetailScreen.kt` | Detail screen for downloaded tracks |

### Modified files

| File | Change |
|------|--------|
| `data/Song.kt` | Implement `PlayableTrack` |
| `data/db/EraDatabase.kt` | Add entities, DAO, bump version to 2 |
| `data/SettingsStore.kt` | Add download flags |
| `AppContainer.kt` | Wire new services |
| `playback/PlayerConnection.kt` | Change `currentSongId` to `String?`, add `setDownloadedQueue()` |
| `ui/components/SongRow.kt` | Accept `PlayableTrack` instead of `Song` |
| `ui/playlists/PlaylistsScreen.kt` | Show synced playlists section |
| `ui/EraNavHost.kt` | Integrate banner, routes, sync trigger |
| `gradle/libs.versions.toml` | New dependency entries |
| `app/build.gradle.kts` | New dependencies, BuildConfig |
| `settings.gradle.kts` | JitPack repository |

---

## Task 1: Build Configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add dependency entries to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
okhttp = "4.12.0"
gson = "2.11.0"
newpipe-extractor = "v0.24.3"
work = "2.10.0"
```

Add to `[libraries]`:

```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
newpipe-extractor = { group = "com.github.TeamNewPipe", name = "NewPipeExtractor", version.ref = "newpipe-extractor" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
```

- [ ] **Step 2: Add JitPack repository**

In `settings.gradle.kts`, add JitPack inside the `repositories` block within `dependencyResolutionManagement`:

```kotlin
maven { url = uri("https://jitpack.io") }
```

The full `dependencyResolutionManagement` block becomes:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

- [ ] **Step 3: Add dependencies and BuildConfig to app build.gradle.kts**

In `app/build.gradle.kts`:

1. Add `buildFeatures { buildConfig = true }` inside the `android` block (alongside existing `compose = true`).
2. Add BuildConfig fields inside `android > defaultConfig`:

```kotlin
buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${property("spotify.client.id")}\"")
buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${property("spotify.client.secret")}\"")
```

3. Add dependency lines in the `dependencies` block:

```kotlin
implementation(libs.okhttp)
implementation(libs.gson)
implementation(libs.newpipe.extractor)
implementation(libs.androidx.work.runtime.ktx)
```

- [ ] **Step 4: Add Spotify credentials to local.properties**

In `local.properties` (already gitignored), add:

```properties
spotify.client.id=YOUR_CLIENT_ID
spotify.client.secret=YOUR_CLIENT_SECRET
```

The user must register a Spotify app at https://developer.spotify.com/dashboard and paste the values here.

- [ ] **Step 5: Verify Gradle sync succeeds**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | head -50`

Expected: No errors. Dependencies for okhttp, gson, NewPipeExtractor, work-runtime-ktx are listed.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts app/build.gradle.kts
git commit -m "build: add NewPipeExtractor, OkHttp, Gson, WorkManager dependencies"
```

---

## Task 2: Database Layer

**Files:**
- Create: `data/db/SpotifyPlaylistEntity.kt`
- Create: `data/db/DownloadedTrackEntity.kt`
- Create: `data/db/PlaylistTrackCrossRef.kt`
- Create: `data/db/DownloadDao.kt`
- Modify: `data/db/EraDatabase.kt`

- [ ] **Step 1: Create SpotifyPlaylistEntity**

Create `app/src/main/java/com/spyou/eramusic/data/db/SpotifyPlaylistEntity.kt`:

```kotlin
package com.spyou.eramusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spotify_playlists")
data class SpotifyPlaylistEntity(
    @PrimaryKey val spotifyId: String,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val trackCount: Int,
    val lastSyncedAt: Long,
)
```

- [ ] **Step 2: Create DownloadedTrackEntity**

Create `app/src/main/java/com/spyou/eramusic/data/db/DownloadedTrackEntity.kt`:

```kotlin
package com.spyou.eramusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val spotifyId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val localFilePath: String,
    val artworkUrl: String?,
    val downloadStatus: String,
    val downloadedAt: Long,
)
```

- [ ] **Step 3: Create PlaylistTrackCrossRef**

Create `app/src/main/java/com/spyou/eramusic/data/db/PlaylistTrackCrossRef.kt`:

```kotlin
package com.spyou.eramusic.data.db

import androidx.room.Entity

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["spotifyPlaylistId", "spotifyTrackId"],
)
data class PlaylistTrackCrossRef(
    val spotifyPlaylistId: String,
    val spotifyTrackId: String,
)
```

- [ ] **Step 4: Create DownloadDao**

Create `app/src/main/java/com/spyou/eramusic/data/db/DownloadDao.kt`:

```kotlin
package com.spyou.eramusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: SpotifyPlaylistEntity)

    @Query("SELECT * FROM spotify_playlists ORDER BY name ASC")
    fun observePlaylists(): Flow<List<SpotifyPlaylistEntity>>

    @Query("SELECT * FROM spotify_playlists WHERE spotifyId = :id")
    suspend fun playlist(id: String): SpotifyPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadedTrackEntity)

    @Query("UPDATE downloaded_tracks SET downloadStatus = :status, downloadedAt = :downloadedAt WHERE spotifyId = :id")
    suspend fun updateStatus(id: String, status: String, downloadedAt: Long)

    @Query("DELETE FROM downloaded_tracks WHERE spotifyId = :id")
    suspend fun deleteTrack(id: String)

    @Query("SELECT * FROM downloaded_tracks WHERE spotifyId = :id")
    suspend fun track(id: String): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE spotifyId IN (:ids) AND downloadStatus = 'COMPLETE'")
    suspend fun tracksByIds(ids: List<String>): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE downloadStatus = 'COMPLETE'")
    fun observeCompletedTracks(): Flow<List<DownloadedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE spotifyPlaylistId = :playlistId AND spotifyTrackId = :trackId")
    suspend fun removeCrossRef(playlistId: String, trackId: String)

    @Query("SELECT spotifyTrackId FROM playlist_track_cross_ref WHERE spotifyPlaylistId = :playlistId")
    suspend fun trackIdsForPlaylist(playlistId: String): List<String>

    @Query("SELECT spotifyTrackId FROM playlist_track_cross_ref WHERE spotifyPlaylistId = :playlistId")
    fun observeTrackIdsForPlaylist(playlistId: String): Flow<List<String>>

    @Query("SELECT DISTINCT spotifyPlaylistId FROM playlist_track_cross_ref WHERE spotifyTrackId = :trackId")
    suspend fun playlistsForTrack(trackId: String): List<String>
}
```

- [ ] **Step 5: Update EraDatabase — add entities, DAO, migration**

Replace `app/src/main/java/com/spyou/eramusic/data/db/EraDatabase.kt` entirely:

```kotlin
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
    version = 2,
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

        private fun build(context: Context): EraDatabase =
            Room.databaseBuilder(context.applicationContext, EraDatabase::class.java, "era.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO playlists (name, isFavorites) VALUES ('Favorites', 1)")
                    }
                })
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/db/
git commit -m "data: add Spotify download entities, DAO, and DB migration v1→v2"
```

---

## Task 3: NewPipeExtractor Initialization

**Files:**
- Create: `data/youtube/NewPipeDownloader.kt`

- [ ] **Step 1: Create OkHttp-based Downloader**

Create `app/src/main/java/com/spyou/eramusic/data/youtube/NewPipeDownloader.kt`:

```kotlin
package com.spyou.eramusic.data.youtube

import okhttp3.OkHttpClient
import okhttp3.Request.Companion.toRequest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val builder = request.url.toRequest().newBuilder()
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        request.httpMethod.method?.let { method ->
            val body = request.httpMethod.body?.toByteArray()
                ?.let { bytes -> okhttp3.RequestBody.create(null, bytes) }
            builder.method(method, body)
        }
        val call = client.newCall(builder.build())
        val response = call.execute()
        if (response.code == 429) {
            throw ReCaptchaException("Rate limited", request.url)
        }
        val responseBody = response.body?.string().orEmpty()
        val headers = response.headers.toMultimap()
            .mapValues { (_, values) -> values.joinToString(", ") }
        return Response(response.code, headers, responseBody, request.url)
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader() }
    }
}
```

Note: Exact NewPipeExtractor API for `Request`/`Response` may vary slightly by version. If compilation fails, check `org.schabi.newpipe.extractor.downloader` package for exact method signatures and adapt.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/youtube/NewPipeDownloader.kt
git commit -m "data: add OkHttp-based NewPipeExtractor downloader"
```

---

## Task 4: Spotify Services

**Files:**
- Create: `data/spotify/SpotifyAuthService.kt`
- Create: `data/spotify/SpotifyModels.kt`
- Create: `data/spotify/SpotifyMetadataService.kt`

- [ ] **Step 1: Create SpotifyAuthService**

Create `app/src/main/java/com/spyou/eramusic/data/spotify/SpotifyAuthService.kt`:

```kotlin
package com.spyou.eramusic.data.spotify

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpotifyAuthService(
    private val clientId: String,
    private val clientSecret: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiry: Long = 0

    suspend fun getAccessToken(): String {
        cachedToken?.let { token ->
            if (System.currentTimeMillis() < tokenExpiry) return token
        }
        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", Credentials.basic(clientId, clientSecret))
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: error("Empty token response")
            val parsed = gson.fromJson(json, TokenResponse::class.java)
            cachedToken = parsed.accessToken
            tokenExpiry = System.currentTimeMillis() + (parsed.expiresIn * 1000L) - 60_000L
            cachedToken!!
        }
    }

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresIn: Int,
    )
}
```

- [ ] **Step 2: Create Spotify API model classes**

Create `app/src/main/java/com/spyou/eramusic/data/spotify/SpotifyModels.kt`:

```kotlin
package com.spyou.eramusic.data.spotify

import com.google.gson.annotations.SerializedName

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
)

data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val tracks: List<SpotifyTrack>,
)

internal data class PlaylistResponse(
    val id: String,
    val name: String,
    val description: String?,
    val images: List<ImageResponse>,
    val tracks: PlaylistTracksSummary,
)

internal data class PlaylistTracksSummary(val total: Int)

internal data class ImageResponse(val url: String)

internal data class TracksPageResponse(val items: List<TrackItemResponse>)

internal data class TrackItemResponse(val track: TrackDataResponse?)

internal data class TrackDataResponse(
    val id: String,
    val name: String,
    @SerializedName("duration_ms") val durationMs: Long,
    val album: AlbumResponse,
    val artists: List<ArtistResponse>,
)

internal data class AlbumResponse(val name: String, val images: List<ImageResponse>)

internal data class ArtistResponse(val name: String)
```

- [ ] **Step 3: Create SpotifyMetadataService**

Create `app/src/main/java/com/spyou/eramusic/data/spotify/SpotifyMetadataService.kt`:

```kotlin
package com.spyou.eramusic.data.spotify

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpotifyMetadataService(
    private val authService: SpotifyAuthService,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetchPlaylist(playlistId: String): SpotifyPlaylistInfo =
        withContext(Dispatchers.IO) {
            val token = authService.getAccessToken()

            val playlistRequest = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId?fields=id,name,description,images,tracks.total")
                .header("Authorization", "Bearer $token")
                .build()
            val playlistJson = client.newCall(playlistRequest).execute()
                .body?.string() ?: error("Empty playlist response")
            val playlistData = gson.fromJson(playlistJson, PlaylistResponse::class.java)

            val tracks = fetchAllTracks(playlistId, token, playlistData.tracks.total)

            SpotifyPlaylistInfo(
                id = playlistData.id,
                name = playlistData.name,
                description = playlistData.description,
                artworkUrl = playlistData.images.firstOrNull()?.url,
                tracks = tracks,
            )
        }

    private fun fetchAllTracks(
        playlistId: String,
        token: String,
        total: Int,
    ): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 100
        do {
            val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks" +
                "?offset=$offset&limit=$limit" +
                "&fields=items(track(id,name,duration_ms,album(name,images),artists(name)))"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val json = client.newCall(request).execute()
                .body?.string() ?: break
            val page = gson.fromJson(json, TracksPageResponse::class.java)
            for (item in page.items) {
                val track = item.track ?: continue
                tracks.add(
                    SpotifyTrack(
                        id = track.id,
                        title = track.name,
                        artist = track.artists.firstOrNull()?.name ?: "Unknown",
                        album = track.album.name,
                        durationMs = track.durationMs,
                        artworkUrl = track.album.images.firstOrNull()?.url,
                    )
                )
            }
            offset += limit
        } while (page.items.size == limit && offset < total)
        return tracks
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/spotify/
git commit -m "data: add Spotify auth and metadata services"
```

---

## Task 5: YouTube Download Service

**Files:**
- Modify: `data/youtube/NewPipeDownloader.kt` (already created)
- Create: `data/youtube/YouTubeDownloadService.kt`

- [ ] **Step 1: Create YouTubeDownloadService**

Create `app/src/main/java/com/spyou/eramusic/data/youtube/YouTubeDownloadService.kt`:

```kotlin
package com.spyou.eramusic.data.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.File
import java.util.concurrent.TimeUnit

class YouTubeDownloadService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun searchAndDownload(
        title: String,
        artist: String,
        targetDurationMs: Long,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val query = "$title $artist"
            val service = ServiceList.YouTube
            val searchQH = service.getSearchQHFactory()
                .fromQuery(query)
            val searchInfo = org.schabi.newpipe.extractor.search.SearchInfo
                .getInfo(service, searchQH)
            val results = searchInfo.relatedItems
            if (results.isEmpty()) return@withContext false

            val bestItem = results
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .filter { it.duration > 0 }
                .minByOrNull {
                    kotlin.math.abs(it.duration * 1000L - targetDurationMs)
                } ?: results.first()

            val streamInfo = StreamInfo.getInfo(service, bestItem.url)
            val audioStream = pickAudioStream(streamInfo.audioStreams)
                ?: return@withContext false

            outputFile.parentFile?.mkdirs()
            val request = Request.Builder().url(audioStream.content).build()
            val response = httpClient.newCall(request).execute()
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output, 8192)
                }
            } ?: return@withContext false

            outputFile.exists() && outputFile.length() > 0
        } catch (_: Exception) {
            outputFile.delete()
            false
        }
    }

    private fun pickAudioStream(streams: List<AudioStream>): AudioStream? {
        return streams
            .filter { it.content != null && it.content.isNotEmpty() }
            .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.bitrate }
    }
}
```

Note: NewPipeExtractor's `AudioStream` API may differ slightly across versions. If `it.content` doesn't work, try `it.url` or check the actual getter name in the library source.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL (or note any API adjustments needed)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/youtube/YouTubeDownloadService.kt
git commit -m "data: add YouTube download service via NewPipeExtractor"
```

---

## Task 6: Playback Integration

**Files:**
- Create: `data/playable/PlayableTrack.kt`
- Create: `data/playable/DownloadedTrack.kt`
- Modify: `data/Song.kt`
- Modify: `playback/PlayerConnection.kt`
- Modify: `ui/components/SongRow.kt`

- [ ] **Step 1: Create PlayableTrack interface**

Create `app/src/main/java/com/spyou/eramusic/data/playable/PlayableTrack.kt`:

```kotlin
package com.spyou.eramusic.data.playable

import android.net.Uri

interface PlayableTrack {
    val trackId: String
    val trackTitle: String
    val trackArtist: String
    val trackArtworkUri: Uri?
}
```

- [ ] **Step 2: Create DownloadedTrack runtime model**

Create `app/src/main/java/com/spyou/eramusic/data/playable/DownloadedTrack.kt`:

```kotlin
package com.spyou.eramusic.data.playable

import android.net.Uri
import java.io.File

data class DownloadedTrack(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val localFile: File,
) : PlayableTrack {
    override val trackId: String get() = spotifyId
    override val trackTitle: String get() = title
    override val trackArtist: String get() = artist
    override val trackArtworkUri: Uri? get() = artworkUrl?.let { Uri.parse(it) }
}
```

- [ ] **Step 3: Update Song to implement PlayableTrack**

In `app/src/main/java/com/spyou/eramusic/data/Song.kt`, add the `PlayableTrack` implementation:

Change the class declaration from:
```kotlin
data class Song(
```
to:
```kotlin
data class Song(
```
And add the `PlayableTrack` interface. The full file becomes:

```kotlin
package com.spyou.eramusic.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.spyou.eramusic.data.playable.PlayableTrack

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val dateAddedSec: Long,
) : PlayableTrack {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )

    override val trackId: String get() = id.toString()
    override val trackTitle: String get() = title
    override val trackArtist: String get() = artist
    override val trackArtworkUri: Uri? get() = albumArtUri
}
```

- [ ] **Step 4: Update PlayerConnection — String mediaId + downloaded queue**

In `app/src/main/java/com/spyou/eramusic/playback/PlayerConnection.kt`:

1. Change `currentSongId` type from `StateFlow<Long?>` to `StateFlow<String?>`:

```kotlin
private val _currentSongId = MutableStateFlow<String?>(null)
val currentSongId: StateFlow<String?> = _currentSongId.asStateFlow()
```

2. Change the `syncState` assignment from:
```kotlin
_currentSongId.value = item?.mediaId?.toLongOrNull()
```
to:
```kotlin
_currentSongId.value = item?.mediaId
```

3. Add a new method after `setQueue`:

```kotlin
fun setDownloadedQueue(tracks: List<com.spyou.eramusic.data.playable.DownloadedTrack>, startIndex: Int) {
    val c = controller ?: return
    if (tracks.isEmpty()) return
    c.setMediaItems(
        tracks.map { it.toMediaItem() },
        startIndex.coerceIn(tracks.indices),
        0L,
    )
    c.prepare()
    c.play()
}

private fun com.spyou.eramusic.data.playable.DownloadedTrack.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(spotifyId)
        .setUri(android.net.Uri.fromFile(localFile))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                .build()
        )
        .build()
```

Note: Add the `File` import: `import java.io.File` is not needed since `DownloadedTrack.localFile` is already a `File`.

- [ ] **Step 5: Update SongRow to accept PlayableTrack**

In `app/src/main/java/com/spyou/eramusic/ui/components/SongRow.kt`:

1. Change import from `Song` to `PlayableTrack`:

Replace:
```kotlin
import com.spyou.eramusic.data.Song
```
with:
```kotlin
import com.spyou.eramusic.data.playable.PlayableTrack
```

2. Change function signature from `song: Song` to `song: PlayableTrack`:

```kotlin
fun SongRow(
    song: PlayableTrack,
```

3. Change internal references:
- `song.albumArtUri` → `song.trackArtworkUri`
- `song.title` → `song.trackTitle`
- `song.artist` → `song.trackArtist`

The 3 specific lines become:

```kotlin
leadingContent = { Artwork(uri = song.trackArtworkUri, size = 50.dp) },
```

```kotlin
text = song.trackTitle,
```

```kotlin
text = song.trackArtist,
```

- [ ] **Step 6: Update all SongRow call sites for currentSongId type change**

In `app/src/main/java/com/spyou/eramusic/ui/songs/SongsScreen.kt`, change:

```kotlin
isCurrent = song.id == currentSongId,
```
to:
```kotlin
isCurrent = song.id.toString() == currentSongId,
```

In `app/src/main/java/com/spyou/eramusic/ui/playlists/PlaylistDetailScreen.kt`, change:

```kotlin
isCurrent = song.id == currentSongId,
```
to:
```kotlin
isCurrent = song.id.toString() == currentSongId,
```

In `app/src/main/java/com/spyou/eramusic/ui/EraNavHost.kt`, change the `isFavorite` computation:

```kotlin
isFavorite = currentSongId != null && currentSongId in favoriteIds,
```
to:
```kotlin
isFavorite = currentSongId?.toLongOrNull()?.let { it in favoriteIds } == true,
```

In `app/src/main/java/com/spyou/eramusic/ui/nowplaying/NowPlayingScreen.kt`, change the parameter type:

```kotlin
currentSongId: Long?,
```
to:
```kotlin
currentSongId: String?,
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/playable/ app/src/main/java/com/spyou/eramusic/data/Song.kt app/src/main/java/com/spyou/eramusic/playback/PlayerConnection.kt app/src/main/java/com/spyou/eramusic/ui/components/SongRow.kt app/src/main/java/com/spyou/eramusic/ui/songs/SongsScreen.kt app/src/main/java/com/spyou/eramusic/ui/playlists/PlaylistDetailScreen.kt app/src/main/java/com/spyou/eramusic/ui/EraNavHost.kt app/src/main/java/com/spyou/eramusic/ui/nowplaying/NowPlayingScreen.kt
git commit -m "feat: add PlayableTrack interface, DownloadedTrack model, String mediaId for mixed playback"
```

---

## Task 7: Sync Engine

**Files:**
- Create: `data/download/DownloadProgress.kt`
- Create: `data/download/SyncOrchestrator.kt`

- [ ] **Step 1: Create DownloadProgress sealed class**

Create `app/src/main/java/com/spyou/eramusic/data/download/DownloadProgress.kt`:

```kotlin
package com.spyou.eramusic.data.download

sealed class DownloadProgress {
    data object Idle : DownloadProgress()
    data class Syncing(
        val playlistIndex: Int,
        val totalPlaylists: Int,
    ) : DownloadProgress()
    data class Downloading(
        val playlistIndex: Int,
        val totalPlaylists: Int,
        val trackIndex: Int,
        val totalNewTracks: Int,
        val trackTitle: String,
        val trackArtist: String,
        val completedSoFar: Int,
        val failedSoFar: Int,
    ) : DownloadProgress()
    data class Complete(
        val totalDownloaded: Int,
        val totalFailed: Int,
    ) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
```

- [ ] **Step 2: Create SyncOrchestrator**

Create `app/src/main/java/com/spyou/eramusic/data/download/SyncOrchestrator.kt`:

```kotlin
package com.spyou.eramusic.data.download

import android.content.Context
import com.spyou.eramusic.data.db.DownloadedTrackEntity
import com.spyou.eramusic.data.db.PlaylistTrackCrossRef
import com.spyou.eramusic.data.db.SpotifyPlaylistEntity
import com.spyou.eramusic.data.db.DownloadDao
import com.spyou.eramusic.data.spotify.SpotifyMetadataService
import com.spyou.eramusic.data.youtube.YouTubeDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SyncOrchestrator(
    private val spotifyMetadataService: SpotifyMetadataService,
    private val youTubeDownloadService: YouTubeDownloadService,
    private val downloadDao: DownloadDao,
    private val context: Context,
) {
    private val _progress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    companion object {
        val PLAYLIST_IDS = listOf(
            "2IqhvoGlZzmKQawiK8L6jA",
            "6s3PWOnScpZwBAXvcxpJFo",
            "0GewAFN4Nf5Ce7qBGKmgay",
        )
    }

    suspend fun syncAll() {
        _progress.value = DownloadProgress.Idle
        var totalDownloaded = 0
        var totalFailed = 0

        try {
            for ((index, playlistId) in PLAYLIST_IDS.withIndex()) {
                val playlistNum = index + 1
                _progress.value = DownloadProgress.Syncing(playlistNum, PLAYLIST_IDS.size)

                val spotifyPlaylist = spotifyMetadataService.fetchPlaylist(playlistId)

                downloadDao.upsertPlaylist(
                    SpotifyPlaylistEntity(
                        spotifyId = spotifyPlaylist.id,
                        name = spotifyPlaylist.name,
                        description = spotifyPlaylist.description,
                        artworkUrl = spotifyPlaylist.artworkUrl,
                        trackCount = spotifyPlaylist.tracks.size,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                )

                val existingTrackIds = downloadDao.trackIdsForPlaylist(playlistId).toSet()
                val remoteTrackIds = spotifyPlaylist.tracks.map { it.id }.toSet()

                val removedTrackIds = existingTrackIds - remoteTrackIds
                for (trackId in removedTrackIds) {
                    val otherPlaylists = downloadDao.playlistsForTrack(trackId)
                    if (otherPlaylists.size <= 1) {
                        val track = downloadDao.track(trackId)
                        if (track != null) {
                            File(context.filesDir, track.localFilePath).delete()
                            downloadDao.deleteTrack(trackId)
                        }
                    }
                    downloadDao.removeCrossRef(playlistId, trackId)
                }

                val newTracks = spotifyPlaylist.tracks.filter { it.id !in existingTrackIds }
                for ((trackIdx, track) in newTracks.withIndex()) {
                    _progress.value = DownloadProgress.Downloading(
                        playlistIndex = playlistNum,
                        totalPlaylists = PLAYLIST_IDS.size,
                        trackIndex = trackIdx + 1,
                        totalNewTracks = newTracks.size,
                        trackTitle = track.title,
                        trackArtist = track.artist,
                        completedSoFar = totalDownloaded,
                        failedSoFar = totalFailed,
                    )

                    val relativePath = "music/${track.id}.m4a"
                    val file = File(context.filesDir, relativePath)
                    file.parentFile?.mkdirs()

                    downloadDao.insertTrack(
                        DownloadedTrackEntity(
                            spotifyId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = track.durationMs,
                            localFilePath = relativePath,
                            artworkUrl = track.artworkUrl,
                            downloadStatus = "DOWNLOADING",
                            downloadedAt = 0L,
                        )
                    )
                    downloadDao.insertCrossRef(
                        PlaylistTrackCrossRef(playlistId, track.id)
                    )

                    val success = youTubeDownloadService.searchAndDownload(
                        title = track.title,
                        artist = track.artist,
                        targetDurationMs = track.durationMs,
                        outputFile = file,
                    )

                    if (success) {
                        downloadDao.updateStatus(track.id, "COMPLETE", System.currentTimeMillis())
                        totalDownloaded++
                    } else {
                        downloadDao.updateStatus(track.id, "FAILED", 0L)
                        totalFailed++
                    }
                }
            }
            _progress.value = DownloadProgress.Complete(totalDownloaded, totalFailed)
        } catch (e: Exception) {
            _progress.value = DownloadProgress.Error(e.message ?: "Sync failed")
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/download/
git commit -m "data: add download progress model and sync orchestrator"
```

---

## Task 8: WorkManager Worker + App Wiring

**Files:**
- Create: `data/download/SyncWorker.kt`
- Modify: `AppContainer.kt`
- Modify: `EraApp.kt`

- [ ] **Step 1: Create SyncWorker**

Create `app/src/main/java/com/spyou/eramusic/data/download/SyncWorker.kt`:

```kotlin
package com.spyou.eramusic.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as com.spyou.eramusic.EraApp).container
        return try {
            container.syncOrchestrator.syncAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
```

- [ ] **Step 2: Update AppContainer to wire all services**

Replace `app/src/main/java/com/spyou/eramusic/AppContainer.kt`:

```kotlin
package com.spyou.eramusic

import android.content.Context
import com.spyou.eramusic.data.MusicRepository
import com.spyou.eramusic.data.PlaylistRepository
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.data.db.EraDatabase
import com.spyou.eramusic.data.download.SyncOrchestrator
import com.spyou.eramusic.data.spotify.SpotifyAuthService
import com.spyou.eramusic.data.spotify.SpotifyMetadataService
import com.spyou.eramusic.data.youtube.NewPipeDownloader
import com.spyou.eramusic.data.youtube.YouTubeDownloadService
import com.spyou.eramusic.playback.PlayerConnection
import com.spyou.eramusic.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.schabi.newpipe.extractor.NewPipe

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database: EraDatabase by lazy { EraDatabase.get(appContext) }
    val musicRepository: MusicRepository by lazy { MusicRepository(appContext) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database.playlistDao()) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }
    val sleepTimer: SleepTimer by lazy { SleepTimer(appScope) }

    private val spotifyAuthService: SpotifyAuthService by lazy {
        SpotifyAuthService(
            BuildConfig.SPOTIFY_CLIENT_ID,
            BuildConfig.SPOTIFY_CLIENT_SECRET,
        )
    }
    private val spotifyMetadataService: SpotifyMetadataService by lazy {
        SpotifyMetadataService(spotifyAuthService)
    }
    val youTubeDownloadService: YouTubeDownloadService by lazy {
        YouTubeDownloadService(appContext)
    }
    val syncOrchestrator: SyncOrchestrator by lazy {
        SyncOrchestrator(
            spotifyMetadataService,
            youTubeDownloadService,
            database.downloadDao(),
            appContext,
        )
    }

    fun initNewPipe() {
        NewPipe.init(NewPipeDownloader.instance)
    }
}
```

- [ ] **Step 3: Update EraApp to initialize NewPipe**

Replace `app/src/main/java/com/spyou/eramusic/EraApp.kt`:

```kotlin
package com.spyou.eramusic

import android.app.Application
import android.content.Context

class EraApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initNewPipe()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as EraApp).container
```

- [ ] **Step 4: Update SettingsStore with download flags**

Replace `app/src/main/java/com/spyou/eramusic/data/SettingsStore.kt`:

```kotlin
package com.spyou.eramusic.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val sortKey = intPreferencesKey("sort_order")
    private val downloadsInitKey = booleanPreferencesKey("pref_downloads_initialized")
    private val lastSyncKey = longPreferencesKey("pref_last_sync_time")

    val sortOrder: Flow<SortOrder> = appContext.dataStore.data.map { prefs ->
        val ordinal = prefs[sortKey] ?: SortOrder.TITLE.ordinal
        SortOrder.entries.getOrElse(ordinal) { SortOrder.TITLE }
    }

    suspend fun setSortOrder(order: SortOrder) {
        appContext.dataStore.edit { it[sortKey] = order.ordinal }
    }

    val downloadsInitialized: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[downloadsInitKey] ?: false
    }

    suspend fun setDownloadsInitialized(value: Boolean) {
        appContext.dataStore.edit { it[downloadsInitKey] = value }
    }

    val lastSyncTime: Flow<Long> = appContext.dataStore.data.map { prefs ->
        prefs[lastSyncKey] ?: 0L
    }

    suspend fun setLastSyncTime(timeMs: Long) {
        appContext.dataStore.edit { it[lastSyncKey] = timeMs }
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/data/download/SyncWorker.kt app/src/main/java/com/spyou/eramusic/AppContainer.kt app/src/main/java/com/spyou/eramusic/EraApp.kt app/src/main/java/com/spyou/eramusic/data/SettingsStore.kt
git commit -m "feat: wire sync worker, AppContainer, NewPipe init, download settings"
```

---

## Task 9: Download ViewModel

**Files:**
- Create: `ui/download/DownloadViewModel.kt`

- [ ] **Step 1: Create DownloadViewModel**

Create `app/src/main/java/com/spyou/eramusic/ui/download/DownloadViewModel.kt`:

```kotlin
package com.spyou.eramusic.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spyou.eramusic.appContainer
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.data.db.DownloadDao
import com.spyou.eramusic.data.db.SpotifyPlaylistEntity
import com.spyou.eramusic.data.download.DownloadProgress
import com.spyou.eramusic.data.download.SyncOrchestrator
import com.spyou.eramusic.data.download.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DownloadViewModel(
    private val syncOrchestrator: SyncOrchestrator,
    private val downloadDao: DownloadDao,
    private val settingsStore: SettingsStore,
    workManager: WorkManager,
) : ViewModel() {

    val progress: StateFlow<DownloadProgress> =
        syncOrchestrator.progress.stateIn(viewModelScope, SharingStarted.Eagerly, DownloadProgress.Idle)

    val syncedPlaylists: StateFlow<List<SpotifyPlaylistEntity>> =
        downloadDao.observePlaylists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloadsInitialized: StateFlow<Boolean> =
        settingsStore.downloadsInitialized.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lastSyncTime: StateFlow<Long> =
        settingsStore.lastSyncTime.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val periodicSync = PeriodicWorkRequestBuilder<SyncWorker>(
        24, TimeUnit.HOURS
    ).setConstraints(
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    ).build()

    init {
        WorkManager.getInstance(/* context from application */).enqueueUniquePeriodicWork(
            "spotify_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSync,
        )
    }

    fun startSync() {
        viewModelScope.launch {
            syncOrchestrator.syncAll()
            settingsStore.setDownloadsInitialized(true)
            settingsStore.setLastSyncTime(System.currentTimeMillis())
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                val container = app.appContainer
                DownloadViewModel(
                    container.syncOrchestrator,
                    container.database.downloadDao(),
                    container.settingsStore,
                    WorkManager.getInstance(app),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/ui/download/DownloadViewModel.kt
git commit -m "ui: add DownloadViewModel with sync trigger and state"
```

---

## Task 10: Download Banner UI

**Files:**
- Create: `ui/download/DownloadBanner.kt`

- [ ] **Step 1: Create DownloadBanner composable**

Create `app/src/main/java/com/spyou/eramusic/ui/download/DownloadBanner.kt`:

```kotlin
package com.spyou.eramusic.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spyou.eramusic.data.download.DownloadProgress

@Composable
fun DownloadBanner(
    progress: DownloadProgress,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = when (progress) {
        is DownloadProgress.Idle -> false
        is DownloadProgress.Complete -> false
        is DownloadProgress.Syncing -> true
        is DownloadProgress.Downloading -> true
        is DownloadProgress.Error -> true
    }

    AnimatedVisibility(visible = isVisible) {
        Surface(
            tonalElevation = 2.dp,
            modifier = modifier.fillMaxWidth(),
        ) {
            when (progress) {
                is DownloadProgress.Idle -> {}
                is DownloadProgress.Syncing -> SyncingContent(progress, onDismiss)
                is DownloadProgress.Downloading -> DownloadingContent(progress, onDismiss)
                is DownloadProgress.Complete -> {}
                is DownloadProgress.Error -> ErrorContent(progress, onDismiss, onStartDownload)
            }
        }
    }
}

@Composable
private fun SyncingContent(
    progress: DownloadProgress.Syncing,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = "Fetching playlist ${progress.playlistIndex}/${progress.totalPlaylists}...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
        }
    }
}

@Composable
private fun DownloadingContent(
    progress: DownloadProgress.Downloading,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = "Downloading: ${progress.trackTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Playlist ${progress.playlistIndex}/${progress.totalPlaylists} · " +
                        "Track ${progress.trackIndex}/${progress.totalNewTracks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
            }
        }
        val fraction = if (progress.totalNewTracks > 0) {
            progress.trackIndex.toFloat() / progress.totalNewTracks
        } else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun ErrorContent(
    progress: DownloadProgress.Error,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Download failed: ${progress.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FilledTonalButton(onClick = onRetry) {
            Text("Retry")
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
        }
    }
}

@Composable
fun DownloadPromptBanner(
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download playlists for offline use",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "3 playlists · ~250 MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onDownload) {
                Text("Download")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/ui/download/DownloadBanner.kt
git commit -m "ui: add download banner and progress composables"
```

---

## Task 11: Synced Playlist Detail Screen

**Files:**
- Create: `ui/playlists/SyncedPlaylistDetailScreen.kt`

- [ ] **Step 1: Create SyncedPlaylistDetailScreen**

Create `app/src/main/java/com/spyou/eramusic/ui/playlists/SyncedPlaylistDetailScreen.kt`:

```kotlin
package com.spyou.eramusic.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.data.playable.DownloadedTrack
import com.spyou.eramusic.data.db.DownloadedTrackEntity
import com.spyou.eramusic.playback.PlayerConnection
import com.spyou.eramusic.ui.components.Artwork
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedPlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    downloadDao: com.spyou.eramusic.data.db.DownloadDao,
    playerConnection: PlayerConnection,
    currentSongId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val trackIds by remember(playlistId) {
        downloadDao.observeTrackIdsForPlaylist(playlistId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val tracks = remember(trackIds) {
        trackIds.mapNotNull { id ->
            val entity = runCatching {
                kotlinx.coroutines.runBlocking { downloadDao.track(id) }
            }.getOrNull()
            entity?.let {
                DownloadedTrack(
                    spotifyId = it.spotifyId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    durationMs = it.durationMs,
                    artworkUrl = it.artworkUrl,
                    localFile = File(context.filesDir, it.localFilePath),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = { playerConnection.setDownloadedQueue(tracks, 0) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tracks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No tracks downloaded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                itemsIndexed(
                    tracks,
                    key = { _, track -> track.spotifyId },
                ) { index, track ->
                    SyncedTrackRow(
                        track = track,
                        isCurrent = track.spotifyId == currentSongId,
                        onClick = { playerConnection.setDownloadedQueue(tracks, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedTrackRow(
    track: DownloadedTrack,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Artwork(uri = track.trackArtworkUri, size = 50.dp)
        },
        headlineContent = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            Icon(
                Icons.Rounded.CloudDone,
                contentDescription = "Synced",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        },
    )
}
```

Note: The `runBlocking` call in the composable is not ideal. A better approach would be to expose a `Flow<List<DownloadedTrack>>` from the DAO/ViewModel. If performance is an issue, refactor to use a ViewModel with a pre-loaded StateFlow. For the initial implementation this works.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/ui/playlists/SyncedPlaylistDetailScreen.kt
git commit -m "ui: add synced playlist detail screen"
```

---

## Task 12: Full Integration — Playlists Screen + EraNavHost

**Files:**
- Modify: `ui/playlists/PlaylistsScreen.kt`
- Modify: `ui/EraNavHost.kt`

- [ ] **Step 1: Update PlaylistsScreen to show synced playlists**

The synced playlists appear in a section above user playlists with a "Synced from Spotify" header.

Replace `app/src/main/java/com/spyou/eramusic/ui/playlists/PlaylistsScreen.kt`:

```kotlin
package com.spyou.eramusic.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.data.db.SpotifyPlaylistEntity
import com.spyou.eramusic.ui.components.NameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    syncedPlaylists: List<SpotifyPlaylistEntity>,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSyncedPlaylist: (String, String) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlists") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New playlist")
            }
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (syncedPlaylists.isNotEmpty()) {
                item {
                    Text(
                        text = "Synced from Spotify",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(syncedPlaylists, key = { "synced_${it.spotifyId}" }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = {
                            Text(
                                "${playlist.trackCount} songs",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            onOpenSyncedPlaylist(playlist.spotifyId, playlist.name)
                        },
                    )
                }
                item {
                    androidx.compose.material3.HorizontalDivider()
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = {
                        Text(
                            "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (playlist.isFavorites) Icons.Rounded.Favorite else Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = if (playlist.isFavorites) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.create(name)
                showCreate = false
            },
        )
    }
}
```

- [ ] **Step 2: Update EraNavHost with banner, routes, sync trigger**

Replace `app/src/main/java/com/spyou/eramusic/ui/EraNavHost.kt`:

```kotlin
package com.spyou.eramusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spyou.eramusic.appContainer
import com.spyou.eramusic.data.download.DownloadProgress
import com.spyou.eramusic.ui.components.MiniPlayer
import com.spyou.eramusic.ui.download.DownloadBanner
import com.spyou.eramusic.ui.download.DownloadPromptBanner
import com.spyou.eramusic.ui.download.DownloadViewModel
import com.spyou.eramusic.ui.nowplaying.NowPlayingScreen
import com.spyou.eramusic.ui.playlists.PlaylistDetailScreen
import com.spyou.eramusic.ui.playlists.PlaylistsScreen
import com.spyou.eramusic.ui.playlists.PlaylistsViewModel
import com.spyou.eramusic.ui.playlists.SyncedPlaylistDetailScreen
import com.spyou.eramusic.ui.songs.LibraryViewModel
import com.spyou.eramusic.ui.songs.SongsScreen

private object Routes {
    const val SONGS = "songs"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{id}"
    const val SYNCED_PLAYLIST_DETAIL = "synced_playlist/{playlistId}/{playlistName}"
    const val NOW_PLAYING = "now_playing"

    fun playlistDetail(id: Long) = "playlist/$id"
    fun syncedPlaylistDetail(id: String, name: String) =
        "synced_playlist/$id/${java.net.URLEncoder.encode(name, "UTF-8")}"
}

@Composable
fun EraNavHost() {
    val context = LocalContext.current
    val container = remember { context.appContainer }
    val player = container.playerConnection

    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory)
    val downloadViewModel: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory)

    LaunchedEffect(Unit) {
        player.connect()
        libraryViewModel.refresh()
        playlistsViewModel.refreshSongs()
    }

    val currentSongId by player.currentSongId.collectAsStateWithLifecycle()
    val favoriteIds by playlistsViewModel.favoriteIds.collectAsStateWithLifecycle()
    val downloadsInitialized by downloadViewModel.downloadsInitialized.collectAsStateWithLifecycle()
    val downloadProgress by downloadViewModel.progress.collectAsStateWithLifecycle()
    val syncedPlaylists by downloadViewModel.syncedPlaylists.collectAsStateWithLifecycle()

    var showDownloadPrompt by remember { mutableStateOf(false) }
    var showDownloadBanner by remember { mutableStateOf(false) }

    LaunchedEffect(downloadsInitialized) {
        if (!downloadsInitialized) {
            showDownloadPrompt = true
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != Routes.NOW_PLAYING

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    if (showDownloadPrompt && !downloadsInitialized) {
                        DownloadPromptBanner(
                            onDownload = {
                                showDownloadPrompt = false
                                showDownloadBanner = true
                                downloadViewModel.startSync()
                            },
                            onDismiss = { showDownloadPrompt = false },
                        )
                    }
                    if (showDownloadBanner) {
                        DownloadBanner(
                            progress = downloadProgress,
                            onDismiss = { showDownloadBanner = false },
                            onStartDownload = { downloadViewModel.startSync() },
                        )
                    }
                    MiniPlayer(
                        player = player,
                        onExpand = { navController.navigate(Routes.NOW_PLAYING) },
                    )
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == Routes.SONGS,
                            onClick = { navController.navigateTopLevel(Routes.SONGS) },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                            label = { Text("Songs") },
                        )
                        NavigationBarItem(
                            selected = currentRoute?.startsWith("playlist") == true,
                            onClick = { navController.navigateTopLevel(Routes.PLAYLISTS) },
                            icon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                            label = { Text("Playlists") },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SONGS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SONGS) {
                SongsScreen(
                    libraryViewModel = libraryViewModel,
                    playlistsViewModel = playlistsViewModel,
                    currentSongId = currentSongId,
                )
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    viewModel = playlistsViewModel,
                    syncedPlaylists = syncedPlaylists,
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlistDetail(id)) },
                    onOpenSyncedPlaylist = { id, name ->
                        navController.navigate(Routes.syncedPlaylistDetail(id, name))
                    },
                )
            }
            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                PlaylistDetailScreen(
                    playlistId = id,
                    viewModel = playlistsViewModel,
                    currentSongId = currentSongId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SYNCED_PLAYLIST_DETAIL,
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.StringType },
                    navArgument("playlistName") { type = NavType.StringType },
                ),
            ) { entry ->
                val playlistId = entry.arguments?.getString("playlistId") ?: return@composable
                val playlistName = entry.arguments?.getString("playlistName") ?: "Playlist"
                SyncedPlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    downloadDao = container.database.downloadDao(),
                    playerConnection = player,
                    currentSongId = currentSongId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    player = player,
                    sleepTimer = container.sleepTimer,
                    currentSongId = currentSongId,
                    isFavorite = currentSongId?.toLongOrNull()?.let { it in favoriteIds } == true,
                    onToggleFavorite = {
                        currentSongId?.toLongOrNull()?.let { playlistsViewModel.toggleFavorite(it) }
                    },
                    onCollapse = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spyou/eramusic/ui/playlists/PlaylistsScreen.kt app/src/main/java/com/spyou/eramusic/ui/EraNavHost.kt
git commit -m "feat: integrate download banner, synced playlists, and full navigation"
```

---

## Post-Implementation

### Verification

1. **Build the app:** `./gradlew :app:assembleDebug`
2. **Install on device:** `./gradlew :app:installDebug`
3. **Test first-open flow:** App should show download prompt banner. Tap Download. Verify progress banner appears. Tracks download in background.
4. **Test playback:** Navigate to Playlists tab. Synced playlists should appear at top. Tap a playlist. Tracks should play from internal storage.
5. **Test offline:** Enable airplane mode. Play a synced playlist. Should play without network.

### Spotify API Setup

The user must:
1. Go to https://developer.spotify.com/dashboard
2. Create an app
3. Get Client ID and Client Secret
4. Add to `local.properties`:
   ```
   spotify.client.id=<YOUR_ID>
   spotify.client.secret=<YOUR_SECRET>
   ```
