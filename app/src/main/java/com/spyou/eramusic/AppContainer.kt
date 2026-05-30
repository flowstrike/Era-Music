package com.spyou.eramusic

import android.content.Context
import com.spyou.eramusic.data.MusicRepository
import com.spyou.eramusic.data.PlaylistRepository
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.data.db.EraDatabase
import com.spyou.eramusic.data.download.SyncOrchestrator
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

    private val spotifyMetadataService: SpotifyMetadataService by lazy {
        SpotifyMetadataService()
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
