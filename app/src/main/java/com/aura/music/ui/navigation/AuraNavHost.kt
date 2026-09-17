package com.aura.music.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.music.ui.addsong.AddSongScreen
import com.aura.music.ui.backup.BackupScreen
import com.aura.music.ui.components.AudioReactiveWaveform
import com.aura.music.ui.components.MiniPlayer
import com.aura.music.ui.library.LibraryScreen
import com.aura.music.ui.player.NowPlayingScreen
import com.aura.music.ui.player.NowPlayingViewModel
import com.aura.music.ui.songdetail.SongDetailScreen

object Routes {
    const val LIBRARY = "library"
    const val ADD_SONG = "add_song"
    const val NOW_PLAYING = "now_playing"
    const val BACKUP = "backup"
    const val SONG_DETAIL = "song/{songId}"

    fun songDetail(songId: Long): String = "song/$songId"
}

@Composable
fun AuraNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    playerViewModel: NowPlayingViewModel = hiltViewModel()
) {
    val playback by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val waveform by playerViewModel.waveform.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Full-screen player has its own chrome; every other destination gets the
    // standard app shell: content + MiniPlayer + bottom tabs.
    val showShell = currentRoute != Routes.NOW_PLAYING

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showShell) return@Scaffold
            Column {
                if (playback.currentSong != null) {
                    MiniPlayer(
                        state = playback,
                        onOpenPlayer = { navController.navigate(Routes.NOW_PLAYING) },
                        onTogglePlay = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::skipToNext
                    )
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY ||
                            (currentRoute?.startsWith("song/") == true),
                        onClick = {
                            navController.navigate(Routes.LIBRARY) {
                                popUpTo(Routes.LIBRARY) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = "Library"
                            )
                        },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ADD_SONG,
                        onClick = {
                            navController.navigate(Routes.ADD_SONG) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download"
                            )
                        },
                        label = { Text("Download") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY
            ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onSongClick = { songId ->
                    navController.navigate(Routes.songDetail(songId))
                },
                onAddSongClick = {
                    navController.navigate(Routes.ADD_SONG)
                },
                onPlaySong = {
                    navController.navigate(Routes.NOW_PLAYING)
                },
                onBackupClick = {
                    navController.navigate(Routes.BACKUP)
                }
            )
        }

        composable(Routes.BACKUP) {
            BackupScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_SONG) {
            AddSongScreen(
                onBack = { navController.popBackStack() },
                onSongAdded = { songId ->
                    navController.popBackStack(Routes.LIBRARY, inclusive = false)
                    navController.navigate(Routes.songDetail(songId))
                },
                onPlaylistDone = {
                    navController.popBackStack(Routes.LIBRARY, inclusive = false)
                }
            )
        }

        composable(
            route = Routes.SONG_DETAIL,
            arguments = listOf(
                navArgument("songId") { type = NavType.LongType }
            )
        ) {
            SongDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
            }
        }
    }

        // Live waveform glowing strictly inside the status-bar / notch zone
        // while music plays — never spilling into app content. No touch
        // handling — taps pass straight through.
        val density = LocalDensity.current
        val notchHeight = with(density) {
            WindowInsets.statusBars.getTop(density).toDp()
        }
        AnimatedVisibility(
            visible = playback.isPlaying && showShell,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            AudioReactiveWaveform(
                magnitudes = waveform,
                isPlaying = true,
                barCount = 64,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(notchHeight)
            )
        }
    }
}
