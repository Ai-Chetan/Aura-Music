package com.aura.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.music.data.network.BlockReason
import com.aura.music.ui.components.AmbientBackground
import com.aura.music.ui.components.AuraSectionTitle
import com.aura.music.ui.components.AuraTopBar
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.theme.AuraSpacing
import com.aura.music.ui.tour.TourAnchors
import com.aura.music.ui.tour.tourAnchor

/**
 * The app's only data preference: everything is fully on by default, on any
 * connection. Turning mobile data off limits metered connections to the
 * downloaded vault — Discover, search, streaming and online artwork pause
 * until Wi-Fi (or the switch) returns.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val gate by viewModel.gateState.collectAsStateWithLifecycle()
    val useMobileData by viewModel.useMobileData.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                AuraTopBar(title = "Settings", onBack = { onBack() })
                Spacer(modifier = Modifier.height(AuraSpacing.Md))

                AuraSectionTitle(text = "Connection")
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md)
                ) {
                    ConnectionStatusRow(
                        online = gate.online,
                        onWifi = gate.onWifi,
                        blockedReason = gate.reason
                    )
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Xl))
                AuraSectionTitle(text = "Data usage")
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                GlassCard(
                    modifier = Modifier
                        .tourAnchor(TourAnchors.SETTINGS_DATA)
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setUseMobileData(!useMobileData) }
                            .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use mobile data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (useMobileData) {
                                    "Everything works everywhere — Discover, search and streaming."
                                } else {
                                    "Off: on mobile data only your downloads play."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                        Switch(
                            checked = useMobileData,
                            onCheckedChange = viewModel::setUseMobileData
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Md))
                Text(
                    text = "On Wi-Fi everything is always available. Downloads are started " +
                        "by you, so they are never restricted — and your downloaded " +
                        "vault always plays offline.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AuraSpacing.Lg)
                )

                Spacer(modifier = Modifier.height(AuraSpacing.Xl))
                AuraSectionTitle(text = "Library")
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                GlassCard(
                    modifier = Modifier
                        .tourAnchor(TourAnchors.SETTINGS_BACKUP)
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md)
                ) {
                    SettingsRow(
                        title = "Backup & restore",
                        subtitle = "Export your vault, re-download it anywhere.",
                        onClick = onBackupClick
                    )
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Xl))
                AuraSectionTitle(text = "Help")
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md)
                ) {
                    SettingsRow(
                        title = "Guided tour",
                        subtitle = "Walk through the app screen by screen.",
                        onClick = onGuideClick
                    )
                }

                Spacer(modifier = Modifier.height(AuraSpacing.Xl))
                AuraSectionTitle(text = "About")
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Sm)) {
                        Text(
                            text = "Aura — developed by Ai-Chetan for the open-source community.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aura owns no songs and claims no rights over them. " +
                                "All music, artwork and trademarks belong to their " +
                                "respective artists, labels and rights holders. " +
                                "Private listening only — please keep content you own " +
                                "or have permission to store.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Xxl))
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionStatusRow(
    online: Boolean,
    onWifi: Boolean,
    blockedReason: BlockReason?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AuraSpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = when {
            !online -> MaterialTheme.colorScheme.error
            onWifi -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        }
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(10.dp),
            onDraw = { drawCircle(color = dotColor) }
        )
        Spacer(modifier = Modifier.width(AuraSpacing.Sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    !online -> "Offline"
                    onWifi -> "Online — Wi-Fi"
                    else -> "Online — mobile data"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (blockedReason) {
                    null -> "Full experience"
                    else -> blockedReason.message()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (blockedReason == null) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
