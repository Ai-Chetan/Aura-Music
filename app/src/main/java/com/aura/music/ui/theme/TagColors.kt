package com.aura.music.ui.theme

/**
 * Controlled tag palette.
 *
 * Known names get their semantic color (case-insensitive).
 * Anything new cycles through [Palette] one-by-one instead of
 * defaulting to the same blue — sequential by tag count in the
 * repository, hash fallback for display-only paths.
 */
object TagColors {
    val Palette = listOf(
        "#38BDF8", // Chill — Electric Cyan
        "#60A5FA", // Dark
        "#A78BFA", // Emotional
        "#22D3EE", // English
        "#818CF8", // Focus
        "#FBBF24", // Hindi
        "#C084FC", // Party
        "#2DD4BF", // Retro
        "#8B5CF6", // Sad
        "#FB923C", // Workout
        "#FB7185"  // Romance
    )

    private val Semantic = mapOf(
        "chill" to "#38BDF8",
        "dark" to "#60A5FA",
        "emotional" to "#A78BFA",
        "english" to "#22D3EE",
        "focus" to "#818CF8",
        "hindi" to "#FBBF24",
        "party" to "#C084FC",
        "retro" to "#2DD4BF",
        "sad" to "#8B5CF6",
        "workout" to "#FB923C",
        "romance" to "#FB7185"
    )

    /** Semantic color for a known name, or null. */
    fun semanticFor(name: String): String? =
        Semantic[name.trim().lowercase()]

    /**
     * Next color in rotation for a brand-new tag.
     * Known names keep their semantic color; everything else
     * takes Palette[existingCount % size] so consecutive tags differ.
     */
    fun nextFor(name: String, existingCount: Int): String {
        semanticFor(name)?.let { return it }
        val size = Palette.size
        val idx = ((existingCount % size) + size) % size
        return Palette[idx]
    }

    /**
     * Display-only fallback (no DB access): semantic first,
     * then a stable hash pick so legacy null-color rows still vary.
     */
    fun displayFor(name: String, colorHex: String?): String {
        if (!colorHex.isNullOrBlank()) return colorHex
        semanticFor(name)?.let { return it }
        val key = name.trim().lowercase()
        val idx = (key.hashCode() and 0x7fffffff) % Palette.size
        return Palette[idx]
    }
}
