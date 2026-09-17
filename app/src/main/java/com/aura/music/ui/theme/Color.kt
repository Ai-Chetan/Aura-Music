package com.aura.music.ui.theme

import androidx.compose.ui.graphics.Color

// Aura dark theme: deep dark base + vivid blue/cyan accent.
val Background = Color(0xFF0C131B)
val Surface = Color(0xFF141F2B)
val SurfaceVariant = Color(0xFF1C2A3A)
val SurfaceElevated = Color(0xFF24344A)

// Vibrant accent colors
val Primary = Color(0xFF38BDF8)      // Sky blue accent
val PrimaryContainer = Color(0xFF7DD3FC)
val OnPrimary = Color(0xFF04121A)

val Secondary = Color(0xFF00D2FF)    // Cyan end of the signature blue→cyan gradient
val SecondaryContainer = Color(0xFF38BDF8) // Sky blue — selected-chip fill, readable with dark text
val OnSecondary = Color(0xFF04121A)

val Accent = Color(0xFFEC4899)       // Pink/Hot Pink
val AccentContainer = Color(0xFFF472B6)

// Text with proper contrast
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)
val TextMuted = Color(0xFF475569)

// Background variations
val BackgroundLighter = Color(0xFF121E2B)
val BackgroundDarkest = Color(0xFF070D14)

// Glass + gradient tokens for frosted surfaces over the ambient background.
val GlassFill = Color(0xFF000000).copy(alpha = 0.4f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)
val GradientBlue = Color(0xFF38BDF8)
val GradientCyan = Color(0xFF22D3EE)

// Quality badges: BEST amber for top bitrate, HQ green otherwise.
val BadgeHq = Color(0xFF22C55E)
val BadgeBest = Color(0xFFF59E0B)

// Error
val Error = Color(0xFFEF4444)
val OnError = Color(0xFFFFFFFF)