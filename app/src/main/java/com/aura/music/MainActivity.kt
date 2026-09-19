package com.aura.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.compose.LocalImageLoader
import androidx.compose.runtime.CompositionLocalProvider
import com.aura.music.data.DefaultTagSeeder
import com.aura.music.ui.components.AuraSplash
import com.aura.music.ui.navigation.AuraNavHost
import com.aura.music.ui.theme.AuraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var defaultTagSeeder: DefaultTagSeeder

    /** Data-saver-aware artwork loader (blocks remote art when gate is closed). */
    @Inject
    lateinit var coilImageLoader: ImageLoader

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Result is not critical; playback still works without the notification permission,
        // the system just may not show the media notification on Android 13+.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                defaultTagSeeder.seedIfEmpty()
            } catch (_: Exception) {
                // Seeding is cosmetic; a DB hiccup at cold start must not crash the app.
            }
        }

        requestNotificationPermissionIfNeeded()

        enableEdgeToEdge()
        setContent {
            AuraTheme {
                CompositionLocalProvider(LocalImageLoader provides coilImageLoader) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by rememberSaveable { mutableStateOf(true) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        AuraNavHost()
                        // All motion lives inside AuraSplash (appear → hold → expand);
                        // the shell just drops the already-invisible layer.
                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                        ) {
                            AuraSplash(onDone = { showSplash = false })
                        }
                    }
                }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(permission)
        }
    }
}
