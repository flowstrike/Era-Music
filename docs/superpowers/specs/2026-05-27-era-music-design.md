# Era Music — Design Spec

Date: 2026-05-27
Package: `com.spyou.eramusic` · App label: "Era Music"

## Goal
An offline music player for Android with a minimal Material 3 design and playlist
features. Plays audio already on the device. No streaming. (An unused `INTERNET`
permission is added at the user's request, for testing only.)

## Stack & Architecture
- Kotlin + Jetpack Compose + Material 3 (existing scaffold).
- Single-Activity app, Compose Navigation, MVVM.
- **Playback:** Media3 (ExoPlayer) hosted in a `MediaSessionService`. Provides
  background playback, media-style notification, lockscreen/Bluetooth/headset
  controls, and audio-focus handling. UI talks to it via a `MediaController`
  (wrapped in a `PlayerConnection` holder exposed as state).
- **Library source:** `MediaStore` audio query via a `MusicRepository`.
- **Persistence:** Room for playlists, playlist-track join rows, and favorites
  (Favorites is a reserved playlist). Sort order + last sort prefs in DataStore.

## Permissions
- `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤ API 32) — runtime,
  with a permission rationale/request screen gating the library.
- `POST_NOTIFICATIONS` (API 33+) — runtime, for the playback notification.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — normal.
- `INTERNET` — declared but unused (testing only, per request).

## Screens (bottom navigation)
1. **Songs** — all tracks from MediaStore; tap to play. Per-item overflow: add to
   playlist, toggle favorite. Top app bar: search field + sort menu
   (title / date added / duration).
2. **Playlists** — user playlists + Favorites. Create / rename / delete. Opening a
   playlist shows its tracks with remove + move up/down (no full drag-reorder in v1).
3. **Now Playing** — full screen: artwork, title/artist, seek bar with elapsed/total,
   prev / play-pause / next, shuffle toggle, repeat (off → all → one), favorite
   toggle, sleep-timer entry.
4. **Mini-player** — persistent bar above the bottom nav on every screen; tap to
   expand to Now Playing.

## Features (v1)
- Core: scan device audio, play/pause/next/prev, background playback + notification,
  playlist CRUD + add/remove tracks, Now Playing.
- Search & sort (library).
- Favorites (heart toggle; reserved Favorites playlist).
- Shuffle + repeat (off / all / one).
- Sleep timer (stop after chosen duration; cancellable).

## Data Flow
- `MediaStore` → `MusicRepository` → `LibraryViewModel` → Songs UI.
- Room (`PlaylistDao`) → `PlaylistRepository` → `PlaylistsViewModel` → Playlists UI.
- `PlayerConnection` (bound `MediaController`) ↔ `MusicService`/ExoPlayer; player
  state (current item, position, isPlaying, shuffle, repeat) exposed to Now Playing
  + mini-player.

## Out of Scope (v1, YAGNI)
Equalizer, album/artist browse tabs, folder view, full drag-to-reorder, streaming,
lyrics, gapless/crossfade tuning, tag editing.

## Verification
1. `./gradlew assembleDebug` compiles.
2. Boot emulator (API 34), install debug APK.
3. Push sample audio to device storage, trigger MediaStore scan, grant audio +
   notification permissions.
4. Launch; screenshot Songs list and Now Playing to confirm playback wiring.

## Theme
Material You dynamic color on Android 12+, curated fallback palette below; light/dark
follows system. Minimal: generous spacing, few surfaces, rounded M3 components.
