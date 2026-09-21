package com.aura.music.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.ui.components.AlbumArt
import com.aura.music.ui.components.AuraSearchField
import com.aura.music.ui.components.GlassCard
import com.aura.music.ui.components.RailCard
import com.aura.music.ui.components.StreamRow
import com.aura.music.ui.onboarding.StarterTrack
import com.aura.music.ui.onboarding.StarterTracks
import com.aura.music.ui.theme.AuraRadius
import com.aura.music.ui.theme.AuraSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Gap between the anchor's edge and the tooltip card (fits the pin pointer). */
private val TOOLTIP_GAP = 50.dp
/** Padding added around the anchor when cutting the scrim hole. */
private val CUTOUT_PADDING = 10.dp
private val CUTOUT_RADIUS = 18.dp

/** Cubic ease-out for one-shot entrances. */
private val EaseOutCubic = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }

/**
 * One-shot slide+fade entrance started on first composition (staggered via
 * [delayMs]). Returns the current (slide px, fade) values as observable
 * state so callers can plug them into graphicsLayer.
 */
@Composable
private fun rememberEntrance(
    delayMs: Int,
    slideFrom: Float = 40f,
    slideMs: Int = 500,
    fadeMs: Int = 400
): Pair<Float, Float> {
    val slide = remember { Animatable(slideFrom) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            delay(delayMs.toLong())
            slide.animateTo(0f, tween(slideMs, easing = EaseOutCubic))
        }
        launch {
            delay(delayMs.toLong())
            fade.animateTo(1f, tween(fadeMs))
        }
    }
    return slide.value to fade.value
}

/**
 * The guided tour, drawn above the real app: a scrim with a cutout and
 * pulsing border around the live feature, a single floating arrow bobbing
 * on the anchor's edge, and a tooltip card with step controls. The final
 * step swaps the whole layer for a finish panel: recommended starter
 * tracks plus a search bar that jumps straight into Add.
 */
@Composable
fun GuidedTourOverlay(
    controller: TourController,
    onSearchRequest: (String) -> Unit,
    onDownloadStarters: (List<StarterTrack>) -> Unit,
    onFinish: () -> Unit
) {
    if (!controller.active) return

    // Per-step readiness: false until the engine has settled the step
    // (anchor measured, or step skipped). Keyed reset keeps it in sync
    // with step changes driven from inside the engine effect below.
    var stepReady by remember(controller.stepIndex, controller.atFinish) {
        mutableStateOf(false)
    }

    // Step engine: run the step's setup (navigate/scroll), then wait for
    // the anchor to appear. If the real feature isn't on screen (fresh
    // install), the step falls back to a centered card with a dummy
    // preview instead of being skipped.
    LaunchedEffect(controller.stepIndex, controller.atFinish) {
        if (controller.atFinish) {
            stepReady = true
            return@LaunchedEffect
        }
        val step = controller.steps.getOrNull(controller.stepIndex)
        val nav = controller.navigator
        if (step == null || nav == null) {
            stepReady = true
            return@LaunchedEffect
        }
        step.runBefore(nav)
        val anchorId = step.anchorId
        if (anchorId == null || (controller.anchors[anchorId]?.width ?: 0f) > 0f) {
            stepReady = true
            return@LaunchedEffect
        }
        withTimeoutOrNull(step.anchorTimeoutMs) {
            snapshotFlow { controller.anchors[anchorId] }
                .filter { (it?.width ?: 0f) > 0f }
                .first()
        }
        // Anchor never appeared: instead of skipping, settle — the card
        // renders centered and shows the step's dummy preview (fresh
        // install: no continue rail, no mini player, empty vault…).
        stepReady = true
    }

    BackHandler {
        when {
            controller.atFinish -> controller.back()
            controller.stepIndex > 0 -> controller.back()
            else -> {
                controller.end()
                onFinish()
            }
        }
    }

    val step = controller.steps.getOrNull(controller.stepIndex)
    if (controller.atFinish) {
        FinishPanel(
            onSearch = onSearchRequest,
            onDownload = onDownloadStarters,
            onDone = onFinish
        )
    } else if (step != null) {
        CoachMarkLayer(
            step = step,
            stepIndex = controller.stepIndex,
            totalSteps = controller.steps.size,
            anchor = step.anchorId?.let { controller.anchors[it] }
                ?.takeIf { it.width > 0f && it.bottom > 0f },
            ready = stepReady,
            onBack = { controller.back() },
            onNext = { controller.next() },
            onSkip = {
                controller.end()
                onFinish()
            }
        )
    }
}

@Composable
private fun CoachMarkLayer(
    step: TourStep,
    stepIndex: Int,
    totalSteps: Int,
    anchor: Rect?,
    ready: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    // Animated orbiting particles around the cutout — composable-level so
    // the Canvas draw block only reads the angle.
    val particleTransition = rememberInfiniteTransition(label = "orbit-particles")
    val particleAngle by particleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle-orbit"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // The layer owns every touch: the app underneath must not react
            // while the tour is pointing at it (no accidental plays or
            // refreshes). Buttons live above this layer and still work.
            .pointerInput(Unit) { detectDragGestures { _, _ -> } }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val screenWpx = constraints.maxWidth.toFloat()
        val screenHpx = constraints.maxHeight.toFloat()
        val pulse by rememberInfiniteTransition(label = "tour-pulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tour-pulse-alpha"
        )

        // Dim scrim with a rounded cutout around the highlighted feature,
        // plus a pulsing accent border that draws the eye to it.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val full = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val cutout = anchor?.inflate(CUTOUT_PADDING.toPx())
            val scrimPath = if (cutout != null) {
                val hole = Path().apply {
                    addRoundRect(RoundRect(cutout, CornerRadius(CUTOUT_RADIUS.toPx())))
                }
                Path.combine(PathOperation.Difference, full, hole)
            } else {
                full
            }
            drawPath(scrimPath, Color.Black.copy(alpha = 0.85f))
            if (cutout != null) {
                // Outer glow ring
                drawRoundRect(
                    color = accent.copy(alpha = pulse * 0.15f),
                    topLeft = cutout.topLeft,
                    size = cutout.size,
                    cornerRadius = CornerRadius(CUTOUT_RADIUS.toPx()),
                    style = Stroke(width = 14.dp.toPx())
                )
                // Inner sharp ring
                drawRoundRect(
                    color = accent.copy(alpha = pulse),
                    topLeft = cutout.topLeft,
                    size = cutout.size,
                    cornerRadius = CornerRadius(CUTOUT_RADIUS.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Orbiting particles around the cutout (all Float math).
                val particleCount = 6
                val orbitGap = 30.dp.toPx()
                val phase = (particleAngle * PI / 180.0).toFloat()
                for (i in 0 until particleCount) {
                    val angle = ((particleAngle + i * (360f / particleCount)) * PI / 180.0).toFloat()
                    val radiusX = cutout.width / 2f + orbitGap
                    val radiusY = cutout.height / 2f + orbitGap
                    val px = cutout.center.x + radiusX * cos(angle)
                    val py = cutout.center.y + radiusY * sin(angle)
                    val particleAlpha = (0.4f + 0.4f * sin(angle + phase)) * pulse

                    drawCircle(
                        color = accent.copy(alpha = particleAlpha),
                        center = Offset(px, py),
                        radius = 3.5f
                    )
                }
            }
        }

        if (!ready) {
            // Settling (waiting for the anchor / deciding a skip): a quiet
            // dim beat instead of a jumping tooltip.
            if (step.anchorId != null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = accent
                )
            }
            return@BoxWithConstraints
        }

        // Tooltip placement: below the anchor when there is room, above
        // otherwise; centered only when unanchored. The card is 92% of the
        // screen width so it never overflows either edge.
        val cardWidthFraction = 0.92f
        var tipSize by remember { mutableStateOf<IntSize?>(null) }
        val marginPx = with(density) { AuraSpacing.Md.toPx() }
        val gapPx = with(density) { TOOLTIP_GAP.toPx() }
        val badgePx = with(density) { 36.dp.toPx() }
        val isLast = stepIndex >= totalSteps - 1

        if (anchor == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = AuraSpacing.Lg)
            ) {
                TooltipCard(
                    step = step,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    isLast = isLast,
                    // Fresh install: the real feature isn't on screen, so
                    // the centered card carries a dummy preview of it.
                    mock = step.mock,
                    onBack = onBack,
                    onNext = onNext,
                    onSkip = onSkip,
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .onGloballyPositioned { tipSize = it.size }
                )
            }
        } else {
            val tipHpx = tipSize?.height?.toFloat()
            val tipWpx = tipSize?.width?.toFloat() ?: (screenWpx * cardWidthFraction)
            var below = anchor.center.y < screenHpx * 0.5f
            if (tipHpx != null) {
                val fitsBelow = anchor.bottom + gapPx + tipHpx < screenHpx - marginPx
                val fitsAbove = anchor.top - gapPx - tipHpx > marginPx
                if (below && !fitsBelow && fitsAbove) below = false
                if (!below && !fitsAbove && fitsBelow) below = true
            }
            val x = (anchor.center.x - tipWpx / 2f)
                .coerceIn(marginPx, (screenWpx - tipWpx - marginPx).coerceAtLeast(marginPx))
            val y = if (below) anchor.bottom + gapPx
            else anchor.top - gapPx - (tipHpx ?: 0f)

            TooltipCard(
                step = step,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                isLast = isLast,
                onBack = onBack,
                onNext = onNext,
                onSkip = onSkip,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .fillMaxWidth(cardWidthFraction)
                    .graphicsLayer { alpha = if (tipSize == null) 0f else 1f }
                    .onGloballyPositioned { tipSize = it.size }
            )

            // The single map-pin pointer, tip touching the anchor's near
            // edge. Clamped so it never leaves the screen.
            FloatingPointer(
                pointingUp = below,
                xPx = anchor.center.x.coerceIn(badgePx, screenWpx - badgePx),
                yPx = if (below) anchor.bottom else anchor.top
            )
        }
    }
}

/**
 * The single pointer: a map-pin — tail tip touching the anchor's edge,
 * round body with a gradient fill and a clean ring outline, plus a soft
 * pulsing glow and a gentle bob toward the feature. Drawn once pointing
 * down; flipped vertically with graphicsLayer for the up case.
 */
@Composable
private fun BoxScope.FloatingPointer(pointingUp: Boolean, xPx: Float, yPx: Float) {
    val transition = rememberInfiniteTransition(label = "tour-pointer")
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tour-pointer-bob"
    )
    val glow by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tour-pointer-glow"
    )
    val density = LocalDensity.current
    val pinSize = with(density) { 44.dp.toPx() }
    val bobPx = bob * with(density) { 1.dp.toPx() } * if (pointingUp) -1f else 1f

    Box(
        modifier = Modifier
            .offset {
                // pointingUp: pin tip at the anchor's bottom edge (box top
                // = edge). pointingDown: pin tip at the top edge (box
                // bottom = edge).
                val top = if (pointingUp) yPx else yPx - pinSize
                IntOffset(
                    (xPx - pinSize / 2f).roundToInt(),
                    (top + bobPx).roundToInt()
                )
            }
            .size(44.dp)
            .graphicsLayer { if (!pointingUp) scaleY = -1f }
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val ringColor = MaterialTheme.colorScheme.surface
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val bodyR = size.width * 0.385f          // ~17dp body radius
            val bodyCy = size.height - bodyR          // body sits at the bottom
            val tipY = 0f
            val tailHalf = bodyR * 0.42f

            // Soft pulsing glow behind the body
            drawCircle(
                color = accent.copy(alpha = glow),
                radius = bodyR + 6.dp.toPx(),
                center = Offset(cx, bodyCy)
            )

            // Pin = circle ∪ tail, drawn as one union so the ring outline
            // follows the merged silhouette cleanly.
            val body = Path().apply {
                addOval(Rect(cx - bodyR, bodyCy - bodyR, cx + bodyR, bodyCy + bodyR))
            }
            val tail = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx - tailHalf, bodyCy - bodyR + 2.dp.toPx())
                lineTo(cx + tailHalf, bodyCy - bodyR + 2.dp.toPx())
                close()
            }
            val pin = Path.combine(PathOperation.Union, body, tail)

            drawPath(
                pin,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.85f),
                        accent
                    ),
                    startY = tipY,
                    endY = size.height
                )
            )
            drawPath(
                pin,
                color = ringColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Body center is at height - bodyR = 27dp; icon top = 27 - 8.
                .offset(y = 19.dp)
                .size(16.dp)
        )
    }
}

/** The coach tooltip: title, body, optional dummy preview, step controls. */
@Composable
private fun TooltipCard(
    step: TourStep,
    stepIndex: Int,
    totalSteps: Int,
    isLast: Boolean,
    mock: TourMock = TourMock.NONE,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val (slideIn, fadeIn) = rememberEntrance(
        delayMs = 60,
        slideFrom = 24f,
        slideMs = 350,
        fadeMs = 300
    )

    Column(modifier = modifier.graphicsLayer {
        translationY = slideIn
        alpha = fadeIn
    }) {
        Surface(
            shape = RoundedCornerShape(AuraRadius.Lg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(AuraSpacing.Md)) {
                // Accent bar at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.0f),
                                    accent.copy(alpha = 0.6f),
                                    accent.copy(alpha = 0.6f),
                                    accent.copy(alpha = 0.0f)
                                )
                            )
                        )
                )
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                // Title with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        accent.copy(alpha = 0.25f),
                                        accent.copy(alpha = 0.0f)
                                    )
                                )
                            )
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Body text
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                if (mock != TourMock.NONE) {
                    Spacer(modifier = Modifier.height(AuraSpacing.Sm))
                    MockPreview(kind = mock)
                }
                Spacer(modifier = Modifier.height(AuraSpacing.Sm))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(AuraSpacing.Xs))

                // Step controls: compact "n of m" progress + actions. Every
                // text is single-line so the row can never squeeze a button
                // into wrapping vertically.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stepIndex + 1} of $totalSteps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (stepIndex > 0) {
                        TextButton(onClick = onBack) {
                            Text(
                                "Back",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            if (isLast) "Get Started" else "Next",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dummy preview of a feature that isn't on screen yet (fresh install),
 * built from the app's own row/card components with placeholder art so it
 * reads as the real thing rather than a diagram.
 */
@Composable
private fun MockPreview(kind: TourMock) {
    Column {
        Text(
            text = "How it looks",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AuraSpacing.Xs))
        when (kind) {
            TourMock.CONTINUE_RAIL, TourMock.FOR_YOU_RAIL -> {
                val cards = if (kind == TourMock.CONTINUE_RAIL) {
                    listOf("Counting Stars" to "OneRepublic", "Viva La Vida" to "Coldplay")
                } else {
                    listOf("Perfect" to "Ed Sheeran", "Believer" to "Imagine Dragons")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)) {
                    cards.forEach { (title, artist) ->
                        RailCard(
                            title = title,
                            subtitle = artist,
                            thumbnailUrl = null,
                            onClick = {},
                            cardWidth = 132.dp
                        )
                    }
                }
            }

            TourMock.HIT_ROW -> StreamRow(
                title = "Blinding Lights",
                subtitle = "The Weeknd • 3:20",
                thumbnailUrl = null,
                onPlay = {},
                rank = 2,
                rankAccent = true,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            TourMock.SONG_ROW -> StreamRow(
                title = "Counting Stars",
                subtitle = "OneRepublic • 4:17",
                thumbnailUrl = null,
                onPlay = {},
                trailing = {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            TourMock.MINI_PLAYER -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(AuraSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(
                        thumbnailPath = null,
                        contentDescription = null,
                        size = 44.dp,
                        cornerRadius = AuraRadius.Sm
                    )
                    Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Blinding Lights",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "The Weeknd",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            TourMock.NONE -> {}
        }
    }
}

/**
 * Last stop of the tour: recommended starter tracks to download in bulk,
 * or a search bar that drops the user straight into Add with results.
 */
@Composable
private fun FinishPanel(
    onSearch: (String) -> Unit,
    onDownload: (List<StarterTrack>) -> Unit,
    onDone: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    // List (not Set) so it stays Bundle-saveable across rotation.
    val selected = rememberSaveable { mutableStateOf(listOf<String>()) }
    fun toggle(url: String) {
        selected.value = if (url in selected.value) selected.value - url
        else selected.value + url
    }

    val accent = MaterialTheme.colorScheme.primary
    // Staggered one-shot entrances: title → search → list → actions.
    val (titleSlide, titleFade) = rememberEntrance(delayMs = 100)
    val (searchSlide, searchFade) = rememberEntrance(delayMs = 250)
    val (listSlide, listFade) = rememberEntrance(delayMs = 400)
    val (bottomSlide, bottomFade) = rememberEntrance(delayMs = 550)

    val pulseTransition = rememberInfiniteTransition(label = "finish-pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = AuraSpacing.Md)
    ) {
        // Decorative top accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.0f),
                            accent.copy(alpha = 0.4f * pulseAlpha),
                            accent.copy(alpha = 0.4f * pulseAlpha),
                            accent.copy(alpha = 0.0f)
                        )
                    )
                )
        )

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        // Title with animation
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = titleSlide
                    alpha = titleFade
                }
        ) {
            Text(
                text = "You know Aura now",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AuraSpacing.Xs))
            Text(
                text = "Search for anything to download — or grab a few starters below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Lg)
            )
        }

        Spacer(modifier = Modifier.height(AuraSpacing.Md))

        // Search field with animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Md)
                .graphicsLayer {
                    translationY = searchSlide
                    alpha = searchFade
                }
        ) {
            AuraSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search any song or artist…",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val q = query.trim()
                        if (q.isNotEmpty()) onSearch(q)
                    }
                )
            )
        }

        // Recommended starters header with animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Xs)
                .graphicsLayer {
                    translationY = listSlide
                    alpha = listFade
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accent.copy(alpha = 0.2f),
                                    accent.copy(alpha = 0.0f)
                                )
                            )
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(AuraSpacing.Sm))
                Text(
                    text = "Recommended starters",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (selected.value.size == StarterTracks.tracks.size) {
                    TextButton(onClick = { selected.value = emptyList() }) { Text("Clear") }
                } else {
                    TextButton(
                        onClick = { selected.value = StarterTracks.tracks.map { it.url } }
                    ) { Text("Select all") }
                }
            }
        }

        // Starters list with animation
        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    translationY = listSlide
                    alpha = listFade
                }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = AuraSpacing.Md,
                    vertical = AuraSpacing.Xxs
                ),
                verticalArrangement = Arrangement.spacedBy(AuraSpacing.Sm)
            ) {
                items(StarterTracks.tracks, key = { it.url }) { track ->
                    val isSelected = track.url in selected.value

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp,
                                    accent,
                                    RoundedCornerShape(AuraRadius.Lg)
                                ) else Modifier
                            )
                            .clickable { toggle(track.url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = AuraSpacing.Xs,
                                end = AuraSpacing.Md,
                                top = AuraSpacing.Xxs,
                                bottom = AuraSpacing.Xxs
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { toggle(track.url) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom actions with animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Md, vertical = AuraSpacing.Sm)
                .graphicsLayer {
                    translationY = bottomSlide
                    alpha = bottomFade
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDone) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    enabled = selected.value.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    onClick = {
                        onDownload(
                            StarterTracks.tracks.filter { it.url in selected.value }
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AuraSpacing.Xs))
                    Text(
                        "Download ${selected.value.size}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
