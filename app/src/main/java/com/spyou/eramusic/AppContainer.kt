package com.spyou.eramusic

import android.content.Context
import com.spyou.eramusic.data.MusicRepository
import com.spyou.eramusic.data.PlaylistRepository
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.data.db.EraDatabase
import com.spyou.eramusic.playback.PlayerConnection
import com.spyou.eramusic.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one instance lives on [EraApp] for the process lifetime. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database: EraDatabase by lazy { EraDatabase.get(appContext) }
    val musicRepository: MusicRepository by lazy { MusicRepository(appContext) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database.playlistDao()) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }
    val sleepTimer: SleepTimer by lazy { SleepTimer(appScope) }
}
