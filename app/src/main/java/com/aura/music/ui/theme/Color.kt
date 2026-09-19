package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

// Aura — Midnight Cyan palette.
// Interaction color is #38BDF8 ONLY: play/pause, selected nav,
// active controls, progress, primary buttons, key highlights.
// Surfaces stay navy; text stays near-white / slate.

val Background = Color(0xFF07131F)          // Deep Navy — primary background

val Surface = Color(0xFF102A3A)             // Dark Blue — cards / surfaces
val SurfaceVariant = Color(0xFF163548)      // Elevated — selected card, tracks, inputs
val SurfaceElevated = Color(0xFF163548)     // Elevated surface

val Border = Color(0xFF34495A)              // Blue Gray — borders

// Interaction accents — use sparingly, only for actions + active states.
val Primary = Color(0xFF38BDF8)             // Electric Cyan — primary action
val PrimaryContainer = Color(0xFF7DD3FC)
val OnPrimary = Color(0xFF07131F)

val Secondary = Color(0xFF22B8F0)           // Bright Cyan — hover/pressed, progress end
val SecondaryContainer = Color(0xFF38BDF8)  // Selected-chip fill, readable with dark text
val OnSecondary = Color(0xFF07131F)

val Accent = Color(0xFFA78BFA)
val AccentContainer = Color(0xFFC084FC)

// Text with proper contrast on #07131F
val TextPrimary = Color(0xFFF5F7FA)         // Almost White
val TextSecondary = Color(0xFF94A9BD)       // Slate Blue

// Glass + gradient tokens for frosted surfaces over the ambient background.
val GlassFill = Color(0xFF102A3A).copy(alpha = 0.6f)
val GlassBorder = Border.copy(alpha = 0.7f)
val GradientBlue = Color(0xFF38BDF8)
val GradientCyan = Color(0xFF22B8F0)

// Quality badges: BEST amber for top bitrate, HQ green otherwise.
val BadgeHq = Color(0xFF22C55E)             // Emerald — success
val BadgeBest = Color(0xFFF59E0B)           // Amber — warning

// Error — Coral Red, dark text for contrast on chips/buttons.
val Error = Color(0xFFF87171)
val OnError = Color(0xFF07131F)