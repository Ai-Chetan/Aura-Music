package com.aura.music.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.music.ui.theme.BadgeBest
import com.aura.music.ui.theme.BadgeHq

/**
 * Small quality chip. YouTube's ceiling is ~160kbps Opus —
 * labelled HQ/BEST, never lossless.
 */
@Composable
fun QualityBadge(
    codec: String?,
    bitrateKbps: Int?,
    modifier: Modifier = Modifier
) {
    val label = when {
        codec.equals("opus", ignoreCase = true) && (bitrateKbps ?: 0) >= 150 -> "HQ • BEST"
        (bitrateKbps ?: 0) >= 128 -> "HQ"
        bitrateKbps != null && bitrateKbps > 0 -> "${codec?.uppercase() ?: "AUDIO"} ${bitrateKbps}k"
        !codec.isNullOrBlank() -> codec.uppercase()
        else -> "AUDIO"
    }
    val tint = if (label.contains("BEST")) BadgeBest else BadgeHq
    Text(
        text = label,
        color = tint,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier
            .border(width = 1.dp, color = tint.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
