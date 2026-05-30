package com.spyou.eramusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.spyou.eramusic.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Binds the UI to [MusicService] via a Media3 [MediaController] and surfaces player
 * state as [StateFlow]s. A single instance lives in the AppContainer for the app's lifetime.
 */
class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var controller: MediaController? = null

    private val _currentSongId = MutableStateFlow<Long?>(null)
    val currentSongId: StateFlow<Long?> = _currentSongId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist.asStateFlow()

    private val _artworkUri = MutableStateFlow<Uri?>(null)
    val artworkUri: StateFlow<Uri?> = _artworkUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _hasItem = MutableStateFlow(false)
    val hasItem: StateFlow<Boolean> = _hasItem.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState(player)
    }

    /** Idempotent; connects the controller and starts position polling on first call. */
    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(listener)
                syncState(c)
            }
            startPositionUpdates()
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        scope.launch {
            while (true) {
                controller?.let { c ->
                    _positionMs.value = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    if (d > 0) _durationMs.value = d
                }
                delay(500)
            }
        }
    }

    private fun syncState(player: Player) {
        _isPlaying.value = player.isPlaying
        _shuffle.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
        val item = player.currentMediaItem
        _hasItem.value = item != null
        _currentSongId.value = item?.mediaId?.toLongOrNull()
        _title.value = item?.mediaMetadata?.title?.toString().orEmpty()
        _artist.value = item?.mediaMetadata?.artist?.toString().orEmpty()
        _artworkUri.value = item?.mediaMetadata?.artworkUri
        val d = player.duration
        _durationMs.value = if (d > 0) d else 0
        _positionMs.value = player.currentPosition.coerceAtLeast(0)
    }

    fun setQueue(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        c.prepare()
        c.play()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit

    fun previous() = controller?.seekToPreviousMediaItem() ?: Unit

    fun seekTo(ms: Long) = controller?.seekTo(ms) ?: Unit

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = controller?.stop() ?: Unit

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .build()
            )
            .build()
}
