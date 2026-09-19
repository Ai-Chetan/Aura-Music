package com.aura.music.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.music.ui.add.AddScreen
import com.aura.music.ui.backup.BackupScreen
import com.aura.music.ui.components.MiniPlayer
import com.aura.music.ui.home.HomeScreen
import com.aura.music.ui.library.LibraryScreen
import com.aura.music.ui.settings.SettingsScreen
import com.aura.music.ui.onboarding.OnboardingScreen
import com.aura.music.ui.onboarding.OnboardingViewModel
import com.aura.music.ui.player.NowPlayingScreen
import com.aura.music.ui.player.NowPlayingViewModel
import com.aura.music.ui.songdetail.SongDetailScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ADD = "add"
    const val ADD_PATTERN = "add?initialUrl={initialUrl}&mode={mode}"
    const val NOW_PLAYING = "now_playing"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val SONG_DETAIL = "song/{songId}"

    fun songDetail(songId: Long): String = "song/$songId"

    /** Add destination: mode 0 = Search, 1 = Paste link, null = auto. */
    fun add(mode: Int? = null): String =
        if (mode == null) ADD else "add?initialUrl=&mode=$mode"
}

@Composable
fun AuraNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    playerViewModel: NowPlayingViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Full-screen player + onboarding have their own chrome; every other
    // destination gets the standard app shell: content + MiniPlayer + tabs.
    val showShell = currentRoute != Routes.NOW_PLAYING &&
        currentRoute != Routes.ONBOARDING

    // DataStore loads async — hold the graph until we know whether to
    // start on onboarding (first launch) or home.
    if (onboardingCompleted == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    val startDestination =
        if (onboardingCompleted == false) Routes.ONBOARDING else Routes.HOME

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showShell) return@Scaffold
            Column {
                AnimatedVisibility(
                    visible = currentTrack != null,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    if (currentTrack != null) {
                        MiniPlayer(
                            onOpenPlayer = {
                                navController.navigate(Routes.NOW_PLAYING) {
                                    launchSingleTop = true
                                }
                            },
                            onTogglePlay = playerViewModel::togglePlayPause,
                            onNext = playerViewModel::skipToNext,
                            onPrevious = playerViewModel::skipToPrevious
                        )
                    }
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LIBRARY ||
                            (currentRoute?.startsWith("song/") == true),
                        onClick = {
                            navController.navigate(Routes.LIBRARY) {
                                popUpTo(Routes.HOME) { inclusive = false }
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
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { slideInHorizontally(initialOffsetX = { it / 6 }) + fadeIn() },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 6 }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 6 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 6 }) + fadeOut() }
            ) {
        composable(Routes.ONBOARDING) {
            // Guide finish returns to wherever the guide was opened from:
            // first launch has no HOME to pop to (fresh start there), while a
            // replay from Settings pops straight back out — no duplicate
            // HOME, no stranded SETTINGS.
            fun finishGuide() {
                if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            OnboardingScreen(
                onFinish = {
                    onboardingViewModel.complete()
                    finishGuide()
                },
                onDownloadSelected = { tracks ->
                    onboardingViewModel.downloadStarterTracks(tracks)
                    onboardingViewModel.complete()
                    finishGuide()
                }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onSongClick = { songId ->
                    navController.navigate(Routes.songDetail(songId))
                },
                onAddSongClick = {
                    navController.navigate(Routes.ADD)
                },
                onPlaySong = {
                    navController.navigate(Routes.NOW_PLAYING) {
                        launchSingleTop = true
                    }
                },
                onDiscoverClick = {
                    navController.navigate(Routes.add(0))
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onPlayStarted = {
                    navController.navigate(Routes.NOW_PLAYING) {
                        launchSingleTop = true
                    }
                },
                onOpenLibrary = {
                    navController.navigate(Routes.LIBRARY) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBackupClick = { navController.navigate(Routes.BACKUP) },
                onGuideClick = { navController.navigate(Routes.ONBOARDING) }
            )
        }

        composable(
            route = Routes.ADD_PATTERN,
            arguments = listOf(
                navArgument("initialUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                },
                navArgument("mode") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val modeArg = backStackEntry.arguments?.getInt("mode")?.takeIf { it in 0..1 }
            AddScreen(
                initialUrl = backStackEntry.arguments?.getString("initialUrl"),
                initialMode = modeArg,
                onSongAdded = { songId ->
                    // Return to wherever Add was opened from, then show the song.
                    navController.popBackStack()
                    navController.navigate(Routes.songDetail(songId))
                },
                onPlaylistDone = {
                    navController.popBackStack()
                },
                onPlayResult = {
                    navController.navigate(Routes.NOW_PLAYING) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onBack = { navController.popBackStack() }
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
    }
}
