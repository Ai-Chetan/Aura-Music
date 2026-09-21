package com.aura.music.ui.tour

/**
 * The guided walkthrough, in order: Home's sections (with the tour
 * scrolling to each one), the Library tab, Add (search & links), the
 * player + queue, Settings, and Backup & restore. The overlay's finish
 * panel (recommended starters + search) follows the last step.
 *
 * Anchored steps point at the live feature; steps whose feature isn't on
 * screen yet (fresh install, empty vault, nothing played) fall back to a
 * dummy preview of it, so the tour always shows every concept.
 */
fun buildAuraTour(nav: TourNavigator): List<TourStep> = listOf(
    TourStep(
        id = "welcome",
        title = "Welcome to Aura Music",
        body = "Explore our Features",
        anchorId = null,
        runBefore = { nav.goHome() }
    ),
    TourStep(
        id = "home.continue",
        title = "Continue listening",
        body = "Jump straight back into what you played recently. Tap a " +
            "card to resume — downloaded tracks play even offline.",
        anchorId = TourAnchors.HOME_CONTINUE,
        optional = true,
        anchorTimeoutMs = 600,
        mock = TourMock.CONTINUE_RAIL,
        runBefore = {
            nav.goHome()
            nav.runAction(TourActions.HOME_SCROLL_TOP)
        }
    ),
    TourStep(
        id = "home.hits",
        title = "Top hits today",
        body = "The biggest tracks right now. The #1 card plays instantly " +
            "with one tap — no saving needed.",
        anchorId = TourAnchors.HOME_HITS
    ),
    TourStep(
        id = "home.hitmenu",
        title = "Play, download or save",
        body = "Every hit has a ⋮ menu: stream now, download for offline, " +
            "add to queue, or play next. Saving bookmarks it to your library.",
        anchorId = TourAnchors.HOME_HIT_MENU,
        optional = true,
        anchorTimeoutMs = 1_500,
        mock = TourMock.HIT_ROW
    ),
    TourStep(
        id = "home.foryou",
        title = "For you",
        body = "Picks shaped by what you actually play and skip — the more " +
            "you listen, the sharper this rail gets.",
        anchorId = TourAnchors.HOME_FOR_YOU,
        optional = true,
        anchorTimeoutMs = 1_500,
        mock = TourMock.FOR_YOU_RAIL,
        runBefore = { nav.runAction(TourActions.HOME_SCROLL_BOTTOM) }
    ),
    TourStep(
        id = "nav.library",
        title = "Your library",
        body = "Everything you download or save lives one tap away. ",
        anchorId = TourAnchors.NAV_LIBRARY
    ),
    TourStep(
        id = "lib.tabs",
        title = "Downloaded vs Saved",
        body = "Downloaded is your offline vault. Saved streams almost " +
            "instantly but needs internet. Your counts show in each tab.",
        anchorId = TourAnchors.LIB_TABS,
        runBefore = { nav.goLibrary() }
    ),
    TourStep(
        id = "lib.search",
        title = "Search your vault",
        body = "Type a title or artist — results filter as you type. Tags " +
            "narrow it further once your library grows.",
        anchorId = TourAnchors.LIB_SEARCH
    ),
    TourStep(
        id = "lib.controls",
        title = "Shuffle & spice up",
        body = "Shuffle plays exactly the list you're looking at. Spice up " +
            "keeps recommended picks flowing behind your queue.",
        anchorId = TourAnchors.LIB_CONTROLS
    ),
    TourStep(
        id = "lib.list",
        title = "Tap to play",
        body = "Tap a song to play it instantly. Long-press for details, " +
            "tags and actions — or swipe the row right to queue it.",
        anchorId = TourAnchors.LIB_LIST,
        optional = true,
        anchorTimeoutMs = 600,
        mock = TourMock.SONG_ROW
    ),
    TourStep(
        id = "lib.add",
        title = "Add music",
        body = "This + searches all of YouTube — songs, videos or playlist " +
            "links. Next opens it with you.",
        anchorId = TourAnchors.LIB_ADD
    ),
    TourStep(
        id = "add.search",
        title = "Search to download",
        body = "Type any song or artist. Results stream instantly — save " +
            "them for streaming or download for offline.",
        anchorId = TourAnchors.ADD_SEARCH,
        runBefore = { nav.goAdd() }
    ),
    TourStep(
        id = "add.link",
        title = "Paste a link",
        body = "The link icon switches to link mode: videos, Shorts and " +
            "whole playlists (up to 50 tracks) in one go." +
            "You can import your YouTube Song Playlist from here!",
        anchorId = TourAnchors.ADD_LINK
    ),
    TourStep(
        id = "player.mini",
        title = "Mini player",
        body = "Whatever is playing rides here — tap it to open the full " +
            "player. Swiping it left skips a track.",
        anchorId = TourAnchors.MINI_PLAYER,
        optional = true,
        anchorTimeoutMs = 600,
        mock = TourMock.MINI_PLAYER
    ),
    TourStep(
        id = "np.queue",
        title = "Now Playing & queue",
        body = "The full player has your artwork, swipe-to-skip and the " +
            "queue icon — drag songs to reorder, swipe to remove.",
        anchorId = TourAnchors.NP_QUEUE,
        runBefore = { nav.goNowPlaying() }
    ),
    TourStep(
        id = "settings.data",
        title = "Data usage",
        body = "Everything is on by default. Turn mobile data off and " +
            "metered connections only play your downloads — Wi-Fi is never " +
            "restricted.",
        anchorId = TourAnchors.SETTINGS_DATA,
        runBefore = { nav.goSettings() }
    ),
    TourStep(
        id = "settings.backup",
        title = "Backup & restore",
        body = "Export your whole vault — every song and tag — to one " +
            "small file. Next shows you how.",
        anchorId = TourAnchors.SETTINGS_BACKUP
    ),
    TourStep(
        id = "backup.export",
        title = "Export & import",
        body = "Export saves a JSON you can import on any device to " +
            "rebuild your vault; Share sends it straight to Drive or chat.",
        anchorId = TourAnchors.BACKUP_EXPORT,
        runBefore = { nav.goBackup() }
    )
)
