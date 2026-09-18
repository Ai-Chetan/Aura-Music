# Build Guide

How Aura was built — and how to extend it. Each phase builds on the previous one; the checklist at the end of every phase is the definition of done.

> Read `overview.md`, `architecture.md`, and `data-model.md` first. This guide assumes the core decisions (Kotlin, Compose, Media3, Room, NewPipeExtractor, Hilt, WorkManager) are fixed.

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
- Skippable **Getting Started guide**: floating coach tips over the UI plus multi-select starter tracks that download in the background with per-song error reporting + retry.
- **Live download queue** in Library: collapsible overall-% card, per-song/playlist progress, cancel; batched list rendering (20-row windows) for large vaults.
- **Library sort modes** (recent/oldest/title/artist/duration) with snap-to-top; swipe artwork on Now Playing to change tracks; fixed-height queue sheet; queue index-mismatch fix.
- **Navigation-proof playlist imports** via dedicated `PlaylistImportWorker` (shared `SongFileDownloader`, per-video failure isolation, live progress).
- **Throttling hardening**: `YtGate` (max 3 concurrent YouTube ops), `ytRetry` backoff+jitter everywhere, staggered batch starts, friendly error messages.
- **DB v2**: unique `sourceUrl` index + non-destructive migration; duplicate-proof inserts.
- **Perf passes**: track-only playback slices (no 500ms recompose storms), waveform redraws scoped to its Canvas, backup I/O off Main, ambient animation on Now Playing only.

**Done when**: search → stream/save works; a 40-track starter batch completes in the background with every failure explained; leaving the Add screen mid-playlist loses nothing.

---

## Phase 8 — Next (Spotify parity & hardening)

Independently testable, in suggested order:

1. Playback speed control (`player.setPlaybackParameters`)
2. Sleep timer (cancellable coroutine countdown → `pause()`)
3. Crossfade / gapless tuning on ExoPlayer transitions
4. Smart playlists from tag queries (`PlaylistEntity.isSmart`, resolved at play time)
5. Recently played / most played views (`playCount` / `lastPlayedAt` are already collected)
6. Home-screen widget (Glance) with mini transport controls
7. Storage-full / network-loss hardening during download; no `ExoPlayer`/`MediaSession` leaks on process death; foreground service stops when playback fully stops.

---

## Notes for contributors

- NewPipeExtractor's YouTube handling changes as YouTube changes its client checks — pin a tested version, and keep all extractor calls behind `ExtractionRepository` so swaps touch one module.
- Keep extraction/download decoupled from tagging/library: a song entity only records `sourcePlatform` / `isLossless`, never its origin story.
- No telemetry, analytics, ads, or network calls beyond extraction/metadata/artwork.
