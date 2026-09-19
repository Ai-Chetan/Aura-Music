package com.aura.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spacing, corners and motion.
 * Every screen uses these — no ad-hoc dp values.
 */
object AuraSpacing {
    val Xxs = 4.dp
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 20.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val BottomListPadding = 160.dp
}

object AuraRadius {
    val Sm = 10.dp
    val Md = 14.dp
    val Lg = 16.dp
    val Xl = 20.dp
}

val AuraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)
