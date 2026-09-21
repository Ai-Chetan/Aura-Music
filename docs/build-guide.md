# Build Guide

How Aura was built — and how to extend it. Each phase builds on the previous one; the checklist at the end of every phase is the definition of done.

> Read `overview.md`, `architecture.md`, and `data-model.md` first. This guide assumes the core decisions (Kotlin, Compose, Media3, Room, NewPipeExtractor, Hilt, WorkManager) are fixed.

## Building from the terminal

The system `JAVA_HOME` may point to a JDK the Kotlin compiler can't parse (e.g. JDK 25 — the build dies with `java.lang.IllegalArgumentException: 25.0.1`). Always point `JAVA_HOME` at **Android Studio's bundled JBR** before invoking Gradle.

PowerShell (verified working):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cmd /c "gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain > build.log 2>&1"
```

Git Bash equivalent:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug --offline --console=plain
```

`--offline` is safe for incremental builds once dependencies are cached; drop it for a clean checkout. For a fast syntax check, `:app:compileDebugKotlin` skips packaging.

---

## Phase 0 — Project setup ✅ done

- Android project: Kotlin, min SDK 26, target/compile SDK 35.
- Dependencies: Media3 (exoplayer, session, ui), Room + KTX, Hilt (+ navigation-compose, work), WorkManager, Coil, NewPipeExtractor via JitPack, Coroutines.
- Hilt application class + DI modules (`DatabaseModule`, `NetworkModule`, `RepositoryModule`, `PlaybackModule`).
- Manifest permissions: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (runtime request on Android 13+).
- Package structure per `architecture.md` §5.

**Done when**: project builds and runs, showing the themed Compose shell.

---

## Phase 1 — Local database & tagging ✅ done

- All Room entities and DAOs from `data-model.md`.
- `SongRepository` / `TagRepository` implementations.
- Library screen: song list with tags, search, tag-filter chips with AND/OR toggle.
- Tag management: create tag, assign/remove per song (`SongDetailScreen`).
- `DefaultTagSeeder` inserts starter tags on first launch (languages, moods, activities — tags only, never fake songs).

**Done when**: with seeded tags and imported songs, filtering by one or more tags returns correct live results.

---

## Phase 2 — Extraction pipeline ✅ done

- `NewPipeExtractionRepository` wraps NewPipeExtractor (OkHttp downloader shim):
  - Resolves `StreamInfo` for a canonical watch URL (3-attempt retry for expiring player responses).
  - Picks highest-bitrate Opus → AAC → anything; records codec + bitrate.
  - Resolves playlists (paginated, capped at 50 videos).
- `DownloadAudioWorker` (WorkManager): streams bytes to `getExternalFilesDir("songs")`, caches the top-resolution thumbnail, inserts the `SongEntity`.
- Add-song UI: paste link → validation → preview card (title/thumbnail/quality/duplicate warning) → progress (resolving → downloading → tagging → done) → tag prompt. Playlist links get their own preview (title, count, duplicates) + batch progress. A `watch?v=…&list=…` link previews its single video with a "Download whole playlist instead" option, so every link shape leads somewhere useful.
- Failure states: invalid URL, private/region-locked video, no audio stream, network failure — each surfaces a specific message.

**Done when**: pasting a real YouTube URL (or playlist link) yields playable local files with correct metadata, taggable immediately; one bad video never aborts a playlist batch.

**Note**: this pipeline saves audio for private listening only. Do not add sharing, bulk-export, or redistribution features.

---

## Phase 3 — Playback engine & queue ✅ done

- `PlaybackService : MediaSessionService` wrapping ExoPlayer.
- `Media3PlaybackController`: binds `MediaController`, exposes `StateFlow<PlaybackUiState>` + live `waveform` buckets; buffers `playQueue()` until connected.
- Shuffle / repeat / next / prev / seek / play-at-index / remove / reorder.
- `AudioVisualizer` feeds the reactive waveform from the player audio session.

**Done when**: a filtered set of songs plays on shuffle in the background (screen off, app backgrounded); next/prev respect shuffle; repeat modes behave correctly.

---

## Phase 4 — Notification & lock-screen panel ✅ done

- Media3's default notification provider driven by the active `MediaSession` (title/artist/artwork from `MediaMetadata`).
- Audio focus + media-button handling via Media3 defaults — verify on a real device, since OEM skins vary.
- Runtime `POST_NOTIFICATIONS` permission request on Android 13+.

**Done when**: with the phone locked, the lock screen shows title, artist, artwork, progress, and working prev/play-pause/next controls in sync with the app.

---

## Phase 5 — UI pass ✅ done

- Design tokens in `ui/theme/` (dark base + blue→cyan accent, type scale, glass tokens).
- `AmbientBackground`, `AudioReactiveWaveform`, `GlassCard`, `TagChip`, `SongRow`, `MiniPlayer`, `AlbumArt`, `QualityBadge`.
- Library → song detail → now-playing (+ queue sheet) navigation with a persistent mini-player shell and a notch-zone live waveform.
- Empty states for library, queue, and search.

**Done when**: the app has a cohesive visual identity — not stock Material 3 — with every screen on the same tokens.

---

## Phase 6 — Backup & restore ✅ done

- `LibraryBackup` JSON codec (versioned, source-URL keyed).
- Export: full library, tag-exclusion filter, or explicit song selection → save/share via system sheet.
- Import: validate + preview (total / duplicates / invalid), then re-download each entry and merge tags with per-song progress.

**Done when**: a JSON export from one install fully restores songs + tags on another.

---

## Phase 7 — Discovery, onboarding & hardening ✅ done

- Combined **Add section**: YouTube Music search (stream instantly or save) + paste-link panels under one segmented toggle with shared chrome.
- Skippable **Getting Started guide**: multi-select starter tracks that download in the background with per-song error reporting + retry. (Superseded in Phase 10 by the live-UI guided tour.)
- **Live download queue** in Library: collapsible overall-% card, per-song/playlist progress, cancel; batched list rendering (20-row windows) for large vaults.
- **Library sort modes** (recent/oldest/title/artist/duration) with snap-to-top; swipe artwork on Now Playing to change tracks; fixed-height queue sheet; queue index-mismatch fix.
- **Navigation-proof playlist imports** via dedicated `PlaylistImportWorker` (shared `SongFileDownloader`, per-video failure isolation, live progress).
- **Throttling hardening**: `YtGate` (max 3 concurrent YouTube ops), `ytRetry` backoff+jitter everywhere, staggered batch starts, friendly error messages.
- **DB v2**: unique `sourceUrl` index + non-destructive migration; duplicate-proof inserts.
- **Perf passes**: track-only playback slices (no 500ms recompose storms), waveform redraws scoped to its Canvas, backup I/O off Main, ambient animation on Now Playing only.

**Done when**: search → stream/save works; a 40-track starter batch completes in the background with every failure explained; leaving the Add screen mid-playlist loses nothing.

---

## Phase 8 — Vault hardening & UI refinement ✅ done

- **Saved streaming bookmarks**: metadata-only `saved_tracks` table + Saved tab; taps resolve a pre-warmed stream URL and play near-instantly through the ExoPlayer disk cache. Same tag/filter/sort vocabulary as downloads.
- **Home hub**: continue listening (recently played, offline-first), top-hits hero + ranked rows, For You recency-weighted from the live rotation.
- **Shared row chrome** (`MediaRowShell`) unifying Downloaded/Saved rows; fixed-height queue sheet; playback fixes.
- **DB v3–v7**: saved tracks, their tags, their play stats, queue-state anchor.

**Done when**: bookmarked streams start instantly, both library tabs read as one design, and recommendations follow the current rotation.

---

## Phase 9 — Recommendation engine & infinite radio ✅ done

- **Listening telemetry**: `skipCount` columns on `songs`/`saved_tracks` (DB v8, migration 7→8) + `ListenStatsStore` (DataStore) tracking plays/skips per URL and per artist — including transient streams with no DB row. Skip detection lives in `Media3PlaybackController`: a `SEEK` track transition before 70% played (15s+ short; <2 min when duration unknown) counts as a skip.
- **`RecommendationEngine`** (`data/recommendations/`): recency-weighted obsession seeds (≈2-day half-life, replay-scaled, skip-damped) over vault + Saved + streams; parallel related-graph expansion of the currently playing track and top seeds; candidate scoring with graph proximity, artist affinity (replayed up, skip-heavy silenced), and jitter; owned replays compete as vault picks (offline, no resolve); cached For-You/trending fallbacks so radio never goes silent.
- **`UpNextManager` sessions**: *playlist* (Saved/Downloads/continue-listening — order first, radio at the dead end) vs *radio* (Top hits/For You/search — engine fills everything). Radio exhaustion re-arms on every track change; repeat modes still loop natively.
- **Library controls**: Shuffle play per tab (filter-aware) and the Spice-up switch (pre-queue engine picks behind the playing playlist; playlist order always wins). Spice-up moved out of the queue sheet per UX decision.
- **Swipeable mini player**: card follows the finger with tilt, skip hints fade in on the revealed edges, committed swipes fly out and the next track slides back in.

**Done when**: tapping a top hit/search result starts an endless engine-driven queue; a Saved playlist continues with recommendations after its last song; skipping an artist measurably demotes it in later picks.

---

## Phase 10 — Guided tour over the live UI ✅ done

The old mock-based coach pages (`OnboardingScreen`) are replaced by a tour that walks the **real app**: a scrim with a cutout + pulsing border around each feature, a bobbing floating arrow, and a tooltip with Back/Next/Skip.

- `ui/tour/TourController.kt`: shared state — step list, active index, `MutableMap` of live element bounds (`Rect`, window coordinates) keyed by `TourAnchors` ids, plus screen-registered actions (e.g. Home scroll-to-section). Screens attach bounds with the `Modifier.tourAnchor(id)` extension; nothing reads the map while no tour runs.
- `ui/tour/GuidedTourOverlay.kt`: the overlay. Step engine runs `TourStep.runBefore(nav)` (navigate / scroll), waits for the anchor to measure in, then renders. **Optional steps** (Continue listening, mini player, first song row…) are skipped when their anchor never appears — the tour adapts to what the user actually has. Anchors are tracked via `onGloballyPositioned`, so cutouts follow scrolling and transitions. The overlay consumes taps/drags so the app underneath can't react; tooltip buttons still work. System back = Back / dismiss.
- `ui/tour/TourSteps.kt`: the ordered script — welcome → Home (Continue listening, Top hits, hit ⋮ menu, For you — the tour scrolls the list) → Library (tabs, search, shuffle/spice-up, rows, + button) → Add (search mode, link mode) → mini player → Now Playing queue → Settings (data usage, backup) → Backup (export/import).
- **Finish panel** (last stop): search bar that routes into Add with the query pre-filled and the search pre-run (`Routes.addSearch`, `AddScreen(initialQuery=…)`), plus the multi-select recommended-starter download (reuses `StarterTracks` + `OnboardingViewModel.downloadStarterTracks`).
- Wiring lives in `AuraNavHost`: `LocalTour` provides the controller, a `TourNavigator` implementation drives `navController`, the tour auto-starts after the splash on first launch (replacing the onboarding route — `onboardingCompleted == false` now means "tour not yet seen"), and Settings → Help → **Guided tour** replays it. Completing or dismissing the tour marks onboarding complete.

**Done when**: first launch shows the tour over the real screens with working arrows/cutouts, optional steps skip gracefully on a fresh install, and the finish panel can search-and-download or bulk-download starters.

---

## Phase 11 — Next (Spotify parity & hardening)

Independently testable, in suggested order:

1. Playback speed control (`player.setPlaybackParameters`)
2. Sleep timer (cancellable coroutine countdown → `pause()`)
3. Crossfade / gapless tuning on ExoPlayer transitions
4. Smart playlists from tag queries (resolved at play time)
5. Most-played stats / listening dashboard (play + skip data are already collected)
6. Home-screen widget (Glance) with mini transport controls
7. Storage-full / network-loss hardening during download; no `ExoPlayer`/`MediaSession` leaks on process death; foreground service stops when playback fully stops.

---

## Notes for contributors

- NewPipeExtractor's YouTube handling changes as YouTube changes its client checks — pin a tested version, and keep all extractor calls behind `ExtractionRepository` so swaps touch one module.
- Keep extraction/download decoupled from tagging/library: a song entity only records `sourcePlatform` / `isLossless`, never its origin story.
- No telemetry, analytics, ads, or network calls beyond extraction/metadata/artwork.
