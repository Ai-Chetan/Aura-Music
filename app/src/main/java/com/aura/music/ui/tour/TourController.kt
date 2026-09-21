package com.aura.music.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Ids for the UI elements the guided tour points at. Screens register the
 * real element with [tourAnchor]; steps reference the same id so the
 * overlay's cutout and arrow land on the live feature, not a mock.
 */
object TourAnchors {
    const val NAV_LIBRARY = "nav.library"
    const val MINI_PLAYER = "player.mini"

    const val HOME_CONTINUE = "home.continue"
    const val HOME_HITS = "home.hits"
    const val HOME_HIT_MENU = "home.hitMenu"
    const val HOME_FOR_YOU = "home.foryou"

    const val LIB_TABS = "lib.tabs"
    const val LIB_SEARCH = "lib.search"
    const val LIB_CONTROLS = "lib.controls"
    const val LIB_LIST = "lib.list"
    const val LIB_ADD = "lib.add"

    const val ADD_SEARCH = "add.search"
    const val ADD_LINK = "add.link"

    const val NP_QUEUE = "np.queue"

    const val SETTINGS_DATA = "settings.data"
    const val SETTINGS_BACKUP = "settings.backup"

    const val BACKUP_EXPORT = "backup.export"
}

/** Screen-provided actions the tour can trigger (revealing off-screen sections). */
object TourActions {
    const val HOME_SCROLL_TOP = "home.scrollTop"
    const val HOME_SCROLL_BOTTOM = "home.scrollBottom"
}

/**
 * Dummy previews shown when a tour step's real feature isn't on screen
 * yet (fresh install: no continue rail, no mini player, empty vault…).
 * Built from the app's own row/card components so they read as native.
 */
enum class TourMock {
    NONE,
    /** "Continue listening" rail of recently played cards. */
    CONTINUE_RAIL,
    /** Ranked top-hit row with the ⋮ actions menu. */
    HIT_ROW,
    /** "For you" recommendation rail. */
    FOR_YOU_RAIL,
    /** A downloaded song row (tap to play / swipe to queue). */
    SONG_ROW,
    /** The persistent mini player bar. */
    MINI_PLAYER
}

/**
 * One stop of the guided tour: a tooltip card, optionally anchored to a
 * live UI element via [anchorId].
 *
 * [optional] steps whose anchor never appears don't skip — they fall back
 * to [mock], a dummy preview of the feature, so the tour always shows
 * every concept even on a fresh install. [runBefore] runs when the step
 * activates: navigating to the right screen or scrolling a section into
 * view before the anchor is measured.
 */
class TourStep(
    val id: String,
    val title: String,
    val body: String,
    val anchorId: String? = null,
    val optional: Boolean = false,
    /** How long the engine waits for the anchor before falling back. */
    val anchorTimeoutMs: Long = 1_500,
    val mock: TourMock = TourMock.NONE,
    val runBefore: (TourNavigator) -> Unit = {}
)

/** Navigation surface the tour drives; implemented by the nav host. */
interface TourNavigator {
    fun goHome()
    fun goLibrary()
    fun goAdd()
    fun goNowPlaying()
    fun goSettings()
    fun goBackup()
    fun runAction(id: String)
}

/**
 * Shared state for the guided tour: the active step, the live bounds of
 * every anchored element (window coordinates), and screen-registered
 * actions. One instance is remembered at the nav-host level and provided
 * to screens via [LocalTour].
 */
class TourController {
    var active by mutableStateOf(false)
        private set
    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var stepIndex by mutableIntStateOf(-1)
        private set

    /** True once the final step advanced — the overlay shows the finish panel. */
    var atFinish by mutableStateOf(false)
        private set

    var navigator: TourNavigator? = null

    /** Live element bounds in window px; keys are [TourAnchors] ids. */
    val anchors = mutableStateMapOf<String, Rect>()
    private val actions = mutableStateMapOf<String, () -> Unit>()

    fun start(steps: List<TourStep>, navigator: TourNavigator) {
        anchors.clear()
        this.steps = steps
        this.navigator = navigator
        stepIndex = 0
        atFinish = false
        active = true
    }

    fun next() {
        if (!active || atFinish) return
        if (stepIndex >= steps.lastIndex) atFinish = true else stepIndex += 1
    }

    fun back() {
        if (!active) return
        if (atFinish) atFinish = false
        else if (stepIndex > 0) stepIndex -= 1
    }

    fun end() {
        active = false
        stepIndex = -1
        atFinish = false
        anchors.clear()
    }

    fun updateAnchor(id: String, rect: Rect) {
        anchors[id] = rect
    }

    fun removeAnchor(id: String) {
        anchors.remove(id)
    }

    fun registerAction(id: String, action: () -> Unit) {
        actions[id] = action
    }

    fun unregisterAction(id: String) {
        actions.remove(id)
    }

    fun runAction(id: String) {
        actions[id]?.invoke()
    }
}

val LocalTour = staticCompositionLocalOf { TourController() }

/**
 * Reports this element's window-space bounds to the tour so the overlay's
 * cutout tracks it live (scrolls, transitions, rotation). Rects are always
 * tracked — but nothing reads them unless a tour is running, so the steady
 * -state cost is one map write per layout change.
 */
fun Modifier.tourAnchor(id: String): Modifier = composed {
    val tour = LocalTour.current
    DisposableEffect(tour, id) {
        onDispose { tour.removeAnchor(id) }
    }
    Modifier.onGloballyPositioned { coords ->
        tour.updateAnchor(id, coords.boundsInWindow())
    }
}
