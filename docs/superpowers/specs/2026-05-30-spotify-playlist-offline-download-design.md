# Spotify Playlist Offline Download

## Problem

The app needs to bundle 3 specific Spotify playlists so users can play them offline on first open, stored in internal app storage (invisible to other apps).

### Target Playlists

1. `2IqhvoGlZzmKQawiK8L6jA` — Spotify playlist
2. `6s3PWOnScpZwBAXvcxpJFo` — Spotify playlist
3. `0GewAFN4Nf5Ce7qBGKmgay` — Spotify playlist

## Architecture

Five components:

1. **SpotifyMetadataService** — Fetches track listings via Spotify Web API (Client Credentials flow, public playlists only, no user login)
2. **YouTubeDownloadService** — Uses NewPipeExtractor to search YouTube for each track and download audio as .m4a
3. **SyncOrchestrator** — Coordinates the full pipeline: fetch metadata → diff with local DB → download new → delete removed
4. **Database layer** — New Room tables for downloaded tracks and Spotify playlists
5. **UI layer** — Download banner with progress, integrated into existing Playlists tab

### Data Flow

```
App opens
  → WorkManager sync job triggers
  → SpotifyMetadataService fetches current playlist track lists
  → Diff against Room DB (downloaded_tracks)
  → New tracks: YouTubeDownloadService searches and downloads audio
  → Removed tracks: delete local file + DB row
  → Updated tracks available for playback via ExoPlayer
```

## Spotify API Access

- **Method:** Client Credentials OAuth flow (no user login required for public playlists)
- **Setup:** Developer registers app at developer.spotify.com, embeds `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` in BuildConfig
- **Token lifecycle:** Fetched on demand, cached until expiry (1 hour), refreshed automatically

## YouTube Download (NewPipeExtractor)

- **Library:** `com.github.TeamNewPipe:NewPipeExtractor` (Maven/JitPack)
- **Flow per track:**
  1. Search YouTube for `"{title} {artist}"`
  2. Pick top result (or filter by duration proximity to Spotify's duration)
  3. Extract audio-only stream URL (prefer AAC/M4A, highest quality ≤ 128kbps)
  4. Download stream to internal storage via OkHttp
- **Error handling:** If a track fails, mark as FAILED in DB, skip and continue. Retry on next sync.

## Database Schema

### `spotify_playlists` table

| Column         | Type     | Notes                        |
|----------------|----------|------------------------------|
| spotifyId      | TEXT PK  | Spotify playlist ID          |
| name           | TEXT     | Playlist name                |
| description    | TEXT?    | Playlist description         |
| artworkUrl     | TEXT?    | Cover image URL              |
| trackCount     | INT      | Number of tracks at last sync|
| lastSyncedAt   | LONG     | Epoch ms of last sync        |

### `downloaded_tracks` table

| Column            | Type     | Notes                                    |
|-------------------|----------|------------------------------------------|
| spotifyId         | TEXT PK  | Spotify track ID                         |
| title             | TEXT     | Track title                              |
| artist            | TEXT     | Primary artist                           |
| album             | TEXT     | Album name                               |
| durationMs        | LONG     | Duration from Spotify metadata           |
| localFilePath     | TEXT     | Relative path under context.filesDir     |
| artworkUrl        | TEXT?    | Album art URL                            |
| downloadStatus    | TEXT     | PENDING, DOWNLOADING, COMPLETE, FAILED   |
| downloadedAt      | LONG     | Epoch ms when download completed         |

### `playlist_track_cross_ref` table (junction)

| Column            | Type     | Notes                                    |
|-------------------|----------|------------------------------------------|
| spotifyPlaylistId | TEXT     | FK to spotify_playlists.spotifyId        |
| spotifyTrackId    | TEXT     | FK to downloaded_tracks.spotifyId        |

Primary key: `(spotifyPlaylistId, spotifyTrackId)` — allows a track to belong to multiple playlists while the audio file is stored once.

## Storage

```
context.filesDir/
  music/
    <spotifyTrackId>.m4a
```

- Uses `context.filesDir` (internal storage)
- Not scanned by MediaStore
- Not visible in file managers or other apps
- Automatically deleted on app uninstall
- No storage permissions required

## Playback Integration

### Unified track model

The existing `Song` data class represents MediaStore tracks. Downloaded tracks use a parallel model:

```kotlin
data class DownloadedTrack(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val localFile: File,
)
```

Both types are convertible to Media3 `MediaItem`:
- MediaStore songs: `Song.toMediaItem()` (existing)
- Downloaded tracks: `DownloadedTrack.toMediaItem()` using `Uri.fromFile(localFile)`

Add a new `setDownloadedQueue(tracks: List<DownloadedTrack>, startIndex: Int)` method to `PlayerConnection` that converts `DownloadedTrack` to `MediaItem` using `Uri.fromFile()`. This avoids modifying the existing `setQueue()` which is typed for `Song`.

### Integration point

- Downloaded playlists appear in the existing Playlists tab with a special "Synced" badge
- The `PlaylistDetailScreen` shows tracks from either Room (user playlists) or downloaded_tracks (synced playlists)
- Artwork loaded via Coil from URL (Spotify album art)

## UI Design

### First-open experience

1. A dismissible banner appears at the top of the main screen: "Download 3 playlists for offline use? Estimated ~250 MB"
2. "Download" button starts the sync
3. During download: banner shows progress — "Downloading playlist 1/3... Track 5/20" with a linear progress bar
4. User can dismiss the banner and use the app normally with local songs
5. Download continues in background via WorkManager

### Playlists tab integration

- The 3 Spotify playlists appear at the top of the Playlists tab with a cloud/download icon
- Tapping opens `PlaylistDetailScreen` showing all downloaded tracks
- A small sync indicator (spinner while syncing, checkmark when up to date)

### Sync status banner

- Persistent but subtle: shows only during active sync
- Auto-dismisses when sync completes

## Sync Logic

### Trigger

- **First open:** Immediate sync after user taps "Download" on banner
- **Subsequent opens:** WorkManager `PeriodicWorkRequest` (every 24 hours) + `OneTimeWorkRequest` on app open if >1 hour since last sync
- **Network constraint:** `NetworkType.CONNECTED` required
- **DataStore flag:** `pref_downloads_initialized` tracks whether first download completed

### Diff algorithm

1. Fetch all 3 playlist track lists from Spotify
2. For each playlist:
   - Get current track IDs from Spotify → Set A
   - Get stored track IDs from Room → Set B
   - New tracks (A - B): insert as PENDING, queue for download
   - Removed tracks (B - A): delete local file + DB row
   - Existing tracks: no action (already COMPLETE)
3. Process download queue sequentially

### Error handling

- Individual track failures: mark FAILED, continue with next track
- Network failure: WorkManager auto-retries with backoff
- Storage full: stop sync, show error notification
- Rate limiting (YouTube): exponential backoff between downloads

## Dependencies to Add

```kotlin
// build.gradle.kts additions

// Spotify Web API (OkHttp-based HTTP client for API calls)
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// NewPipeExtractor (YouTube search and stream extraction)
implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.3")

// Gson for Spotify API JSON parsing
implementation("com.google.code.gson:gson:2.11.0")

// WorkManager for background sync
implementation("androidx.work:work-runtime-ktx:2.10.0")
```

JitPack repository needed for NewPipeExtractor:
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

## Security Considerations

- Spotify Client ID/Secret stored in `BuildConfig` fields via `build.gradle.kts` `buildConfigField`
- Secrets not committed to VCS — use `local.properties` injection
- Downloaded audio files in internal storage (app-private)
- No `WRITE_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` permissions needed

## Estimated Sizes

- ~60-80 tracks across 3 playlists (assuming 20-25 tracks each)
- ~3-4 MB per track at 128kbps AAC
- **Total: ~200-300 MB** for all 3 playlists
- NewPipeExtractor adds ~2 MB to APK
- OkHttp + Gson likely already pulled transitively

## Files to Create/Modify

### New files
- `data/spotify/SpotifyAuthService.kt` — Client Credentials token management
- `data/spotify/SpotifyMetadataService.kt` — Playlist and track metadata fetching
- `data/youtube/YouTubeDownloadService.kt` — YouTube search + audio download via NewPipeExtractor
- `data/download/SyncOrchestrator.kt` — Coordinates full sync pipeline
- `data/download/SyncWorker.kt` — WorkManager worker for background sync
- `data/download/DownloadProgress.kt` — Shared progress state (StateFlow)
- `data/db/SpotifyPlaylistEntity.kt` — Room entity
- `data/db/DownloadedTrackEntity.kt` — Room entity
- `data/db/DownloadDao.kt` — DAO for new tables
- `ui/download/DownloadBanner.kt` — Composable for download prompt/progress
- `ui/download/DownloadViewModel.kt` — ViewModel for download state

### Modified files
- `data/db/EraDatabase.kt` — Add new entities and DAOs
- `AppContainer.kt` — Wire new services
- `ui/EraNavHost.kt` — Integrate download banner, add synced playlist routes
- `playback/PlayerConnection.kt` — Add overload for DownloadedTrack playback
- `app/build.gradle.kts` — Add dependencies, BuildConfig fields
- `settings.gradle.kts` — Add JitPack repository
