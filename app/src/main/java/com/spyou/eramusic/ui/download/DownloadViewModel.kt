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
    private val workManager: WorkManager,
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
        workManager.enqueueUniquePeriodicWork(
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
