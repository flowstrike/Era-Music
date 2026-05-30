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
import java.net.URLEncoder

private object Routes {
    const val SONGS = "songs"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{id}"
    const val SYNCED_PLAYLIST_DETAIL = "synced_playlist/{playlistId}/{playlistName}"
    const val NOW_PLAYING = "now_playing"

    fun playlistDetail(id: Long) = "playlist/$id"
    fun syncedPlaylistDetail(id: String, name: String) =
        "synced_playlist/$id/${URLEncoder.encode(name, "UTF-8")}"
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

    var downloadPromptDismissed by remember { mutableStateOf(false) }
    var downloadBannerDismissed by remember { mutableStateOf(false) }

    val showDownloadPrompt = !downloadsInitialized
            && !downloadPromptDismissed
            && downloadProgress !is DownloadProgress.Syncing
            && downloadProgress !is DownloadProgress.Downloading

    val showDownloadProgressBanner = (!downloadBannerDismissed) && downloadProgress.let {
        it is DownloadProgress.Syncing || it is DownloadProgress.Downloading || it is DownloadProgress.Error || it is DownloadProgress.Complete
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
                    if (showDownloadPrompt) {
                        DownloadPromptBanner(
                            onDownload = {
                                downloadPromptDismissed = true
                                downloadBannerDismissed = false
                                downloadViewModel.startSync()
                            },
                            onDismiss = {
                                downloadPromptDismissed = true
                            },
                        )
                    }
                    if (showDownloadProgressBanner) {
                        DownloadBanner(
                            progress = downloadProgress,
                            onDismiss = { downloadBannerDismissed = true },
                            onStartDownload = {
                                downloadBannerDismissed = false
                                downloadViewModel.startSync()
                            },
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
