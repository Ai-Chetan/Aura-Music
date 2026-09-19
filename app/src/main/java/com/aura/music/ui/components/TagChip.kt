package com.aura.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.music.ui.theme.TagColors

private val chipShape = RoundedCornerShape(percent = 50)
private val FallbackBlue = Color(0xFF38BDF8)

@Composable
fun TagChip(
    name: String,
    colorHex: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** False for read-only display chips (no tap handling, no button role). */
    enabled: Boolean = true
) {
    val baseColor = parseTagColor(TagColors.displayFor(name, colorHex))
    val containerColor = if (selected) baseColor else baseColor.copy(alpha = 0.12f)
    val contentColor = if (selected) readableTextColor(baseColor) else baseColor

    Text(
        text = name,
        color = contentColor,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier
            .clip(chipShape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = baseColor.copy(alpha = if (selected) 0f else 0.45f),
                shape = chipShape
            )
            .clickable(onClick = onClick, enabled = enabled, role = Role.Button)
            .defaultMinSize(minHeight = 30.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

fun parseTagColor(colorHex: String?): Color {
    if (colorHex.isNullOrBlank()) return FallbackBlue
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        FallbackBlue
    }
}

fun readableTextColor(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.55f) Color.Black else Color.White
}