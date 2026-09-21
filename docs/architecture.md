# Architecture & Tech Stack

## 1. High-level architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Android App                          │
│                                                               │
│  ┌───────────────┐   ┌────────────────┐   ┌───────────────┐ │
│  │   UI Layer    │   │  Domain Layer   │   │  Data Layer   │ │
│  │  (Compose)    │◄─►│ (ViewModels,    │◄─►│ (Room DB,     │ │
│  │               │   │  Repositories*) │   │  File Storage)│ │
│  └───────────────┘   └────────────────┘   └───────────────┘ │
│         ▲                     ▲                    ▲         │
│         │                     │                    │         │
│  ┌───────────────┐   ┌────────────────┐   ┌───────────────┐ │
│  │ MediaSession / │   │  Extraction     │   │  Download     │ │
│  │ Notification   │   │  (NewPipe-      │   │  (WorkManager │ │
│  │ (Media3)       │   │   Extractor)    │   │   + OkHttp)   │ │
│  └───────────────┘   └────────────────┘   └───────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

Everything runs on-device. No backend server, no cloud database.

> \* Repository *interfaces* live in `domain/repository/`; implementations live in `data/repository/`. ViewModels live next to their screens under `ui/`.

## 2. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Standard for modern Android |
| UI | Jetpack Compose + Material 3 (customized) | Canvas-based waveform/ambient animations, glass surfaces |
| Playback engine | **Media3 (ExoPlayer + media3-session)** | Gapless playback, `MediaSession` integration powering notification + lock-screen panel |
| Background service | `MediaSessionService` (Media3) | Keeps playback alive when backgrounded |
| Stream extraction | **NewPipeExtractor** (`com.github.TeamNewPipe:NewPipeExtractor`) | Actively maintained extractor library; resolves direct audio stream URLs from YouTube on-device, no API key |
| Local database | Room (SQLite) | Songs, tags, saved bookmarks, play/skip stats, queue state — relational, Flow/Compose-friendly |
| Async/background jobs | Kotlin Coroutines + Flow | Standard, integrates with Room and Compose |
| Download queue | WorkManager | Reliable background downloads that survive app kill/reboot |
| Dependency injection | Hilt | ViewModels / repositories / playback wiring |
| File storage | App-specific external storage (`getExternalFilesDir`) | No storage permissions needed, private to the app |
| Image loading (thumbnails) | Coil | Compose-friendly artwork loading (file, content URI, remote URL) |
| Networking | OkHttp | Extraction (via NewPipe downloader shim) + audio/thumbnail download |
| Backup | Versioned JSON (`LibraryBackup`) | Export songs + tags; re-download from stored source URLs on import |

## 3. Why Media3 instead of a plain MediaPlayer

Plain `MediaPlayer` does not give you rich notification/lock-screen controls, queue awareness, or gapless playback out of the box. Media3 provides:

- `MediaSession` → system notification and lock-screen media panel with play/pause/seek/next/prev.
- Native queue (`MediaItem` list) with shuffle and repeat modes — maps directly onto the app's shuffle/queue/repeat model.
- Gapless transitions via ExoPlayer.
- The same session API Android Auto expects, keeping that door open.

`PlaybackController` is a thin, testable wrapper around a `MediaController` bound to the `MediaSessionService`, exposing `StateFlow<PlaybackUiState>`. It also queues `playQueue()` calls that arrive before the controller connects, and falls back to session metadata if the in-memory song cache missed after a process restart.

## 4. Extraction pipeline (how a pasted link becomes a saved file)

1. **Input**: a YouTube Music search hit, or a pasted YouTube URL (video or `/playlist` link).
2. **Validate + canonicalise**: `YoutubeUrls` accepts watch, `youtu.be`, shorts, `music.youtube`, embed, `/live/`, and `/playlist?list=` links; everything is canonicalised to `watch?v=<id>` / `playlist?list=<id>` for duplicate detection and storage. A `watch?v=…&list=…` link (shared from inside a playlist) resolves as its single video, and the UI offers the whole playlist as a pinned one-tap alternative.
3. **Resolve**: `NewPipeExtractionRepository` fetches `StreamInfo` through the shared politeness layer — max 3 concurrent YouTube operations (`YtGate`) plus exponential-backoff retries with jitter (`ytRetry`), so bursts don't trip 400/403/429 throttling. Search uses the YouTube Music song filter.
4. **Select best stream**: highest-bitrate Opus first, then AAC, then highest of anything else. No transcoding — bytes are stored as-is.
5. **Preview**: title, uploader, duration, thumbnail and quality badge are shown before download; duplicates already in the library are reported immediately. Search hits can alternatively stream instantly (transient queue entry, never saved).
6. **Download**: a WorkManager `CoroutineWorker` streams the audio URL to `getExternalFilesDir("songs")` via OkHttp (ranged requests) with progress callbacks; shared `SongFileDownloader` keeps single and playlist paths identical.
7. **Artwork**: highest-resolution thumbnail is downloaded and cached locally.
8. **Insert into Room**: a `SongEntity` pointing at the local file path, plus tags (user-added, or playlist-title tag on batch import). Inserts use `IGNORE` on the unique `sourceUrl` index so concurrent duplicates resolve instead of crashing.
9. **Playlist path**: playlist URLs enqueue a dedicated `PlaylistImportWorker` (up to 50 videos, paginated) that survives navigation and process trims; each video downloads individually so one private/deleted video never aborts the batch. Starter-track batches are staggered, tracked per-song (`trackStarterBatch`), and report every failure's reason with retry.

## 5. Module / package structure (actual)

```
app/src/main/java/com/aura/music/
├─ AuraApp.kt / MainActivity.kt
├─ ui/
│   ├─ theme/           (colors, typography, Material3 color scheme, tokens)
│   ├─ home/            (continue listening, top hits, For You rails)
│   ├─ library/         (song list, tag filter chips, search, sort, download queue,
│   │                    shuffle + spice-up controls, Saved tab)
│   ├─ player/          (now-playing screen + queue sheet)
│   ├─ add/             (combined Search / Paste-link section)
│   ├─ addsong/         (paste-link panel: preview, single + playlist progress)
│   ├─ search/          (YouTube Music search panel hosted by Add: stream or save)
│   ├─ tour/            (guided tour over the live UI: anchors, overlay, steps)
│   ├─ onboarding/      (starter tracks + view model backing the tour finish panel)
│   ├─ songdetail/      (song page, tag editor)
│   ├─ backup/          (JSON export/import UI)
│   ├─ settings/        (data-usage gate and app preferences)
│   ├─ navigation/      (NavHost, bottom tabs, MiniPlayer shell)
│   └─ components/      (AlbumArt, AmbientBackground, AudioReactiveWaveform,
│                        GlassCard, MiniPlayer (swipeable), QualityBadge, SongRow,
│                        MediaRowShell, Sections (rails/hero), TagChip, splash,
│                        shared chrome)
├─ domain/
│   └─ repository/      (SongRepository, TagRepository, SavedTrackRepository,
│                        ExtractionRepository interfaces)
├─ data/
│   ├─ db/              (Room entities, DAOs, AppDatabase v8 + migrations)
│   ├─ extraction/      (NewPipe wrapper, OkHttp downloader shim, YtGate + ytRetry)
│   ├─ download/        (DownloadAudioWorker, PlaylistImportWorker, SongFileDownloader)
│   ├─ stream/          (StreamResolver + cache, TransientTrackFactory, UpNextManager —
│   │                    the "what plays next" session brain)
│   ├─ recommendations/ (RecommendationEngine, RecommendationsRepository,
│   │                    ListenStatsStore, TrackDedup)
│   ├─ network/         (NetworkGate / ConnectivityMonitor — data-usage gate)
│   ├─ repository/      (repository implementations)
│   ├─ backup/          (LibraryBackup JSON codec)
│   ├─ prefs/           (DataStore: onboarding, playback [spice-up], data usage)
│   └─ DefaultTagSeeder.kt (starter tags on first launch)
├─ playback/
│   ├─ PlaybackService.kt            (MediaSessionService + ExoPlayer + play counts)
│   ├─ PlaybackController.kt         (interface + PlaybackUiState)
│   ├─ Media3PlaybackController.kt   (MediaController wrapper + skip detection)
│   └─ AudioVisualizer.kt            (Visualizer → waveform buckets)
├─ di/                  (Hilt: database, network, repository, playback modules)
└─ util/                (YoutubeUrls, TimeFormatter, DownloadFormat)
```

## 6. Permissions

- `INTERNET` — extraction/download and thumbnail fetch.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — background playback with a media notification (Android 14+).
- `POST_NOTIFICATIONS` — playback notification (Android 13+, requested at runtime).
- No `READ/WRITE_EXTERNAL_STORAGE` — app-specific storage avoids scoped-storage issues.

## 7. UI/design direction

- Dark, near-black base (`#0C131B`) with a blue → cyan signature gradient used on interactive elements and the now-playing background.
- `AmbientBackground`: gradient wash + bottom wave; animated on Now Playing only, static elsewhere (per-frame redraws are gated by the `animate` flag).
- `AudioReactiveWaveform`: live `Visualizer` FFT buckets rendered as bars; collects its flow inside the Canvas node so 10Hz emissions don't recompose the player screen.
- Playback-driven UI collects track-only slices (`currentTrack`) where position isn't needed, so the 500ms progress ticks don't recompose the nav shell or library rows.
- Frosted-glass cards (`GlassCard`), pill-shaped per-tag color chips (`TagChip`), quality badges (`HQ • BEST` amber / `HQ` green).
- Design tokens live in `ui/theme/` — reuse them instead of hardcoding values per screen.

## 8. Recommendation & radio engine ("what plays next")

Every queue in the app is kept alive by `UpNextManager` (`data/stream/`), which manages two session kinds:

| Session kind | Started by | Behaviour |
|---|---|---|
| **Playlist** | Saved / Downloads / continue-listening taps, Library shuffle | The list continues in order behind the tapped track. At the dead end, radio recommendations take over — a playlist never terminates. The Library **Spice up** switch pre-queues engine picks (playlist order always wins first). |
| **Radio** | Top-hits / For-You / search taps | The tapped track plays; *everything after it* is generated by the engine — never "the next row of the list". |

A session-less vault queue behaves like radio at its tail (repeat modes still loop natively; radio only engages on repeat OFF). Whenever the engine finds nothing, every track change re-arms the search — a new current track means a new related graph, so "exhausted" is never final.

`RecommendationEngine` (`data/recommendations/`) is the taste model, entirely on-device:

1. **Signals.** Plays and replays (`playCount`/`lastPlayedAt` in Room for vault + Saved; `ListenStatsStore` DataStore for transient streams with no DB row) and **skips** (`skipCount` columns + per-artist/per-URL stats). `Media3PlaybackController` detects skips on every `SEEK` track transition: leaving a song under 70% played (and 15s+ short; under 2 min when duration is unknown) counts as a skip — for the track itself and for its artist.
2. **Obsession seeds.** Everything known is scored: recency-weighted with a ~2-day half-life, scaled by play frequency, divided by a skip factor. Top seeds + the currently playing track become radio seeds.
3. **Expansion.** The related-tracks graph of the current track (strongest weight) and of the top seeds is fetched in parallel, network-gated, through the shared extraction layer.
4. **Candidates.** Discovery picks are scored by graph proximity + artist affinity (replayed artists get a bonus, skip-heavy artists are nearly silenced) + jitter for variety; high-scoring owned songs compete as replays (vault picks append straight from the local file — offline, no resolve step). Anything queued, recently radio-played, or already owned as a stream duplicate is excluded (`TrackDedup` normalizes upload-type noise).
5. **Fallbacks.** Cached For-You / trending pools keep radio alive when the network misbehaves.

`NetworkGate` (`data/network/`) gates every streaming action (radio included) behind the user's data-usage preferences; vault replays and offline playback are never gated.

## 9. Data flow example: "play a filtered set of tagged songs on shuffle"

1. UI: user selects tags `sad` + `english`, toggles shuffle on.
2. `LibraryViewModel` queries Room for songs matching both tags.
3. `Media3PlaybackController.playQueue()` builds `MediaItem`s (`localFilePath` → URI, title/artist/artwork metadata).
4. `ExoPlayer.shuffleModeEnabled = true`; Media3 handles shuffle order internally.
5. `MediaSession` updates the system notification and lock screen automatically.
6. When the shuffled list runs dry on repeat OFF, `UpNextManager` keeps radio going with engine picks (vault replays append as local files, streams resolve on demand).

## 10. Data flow example: "tap a search result"

1. UI resolves the tapped track to a transient queue entry and starts it instantly.
2. `UpNextManager.startRadio()` marks the session as radio — the rest of the search results are *not* queued.
3. On every tail, the engine appends one ranked pick (related graph of what's playing → obsession seeds → cached pools).
4. Skips and plays feed straight back into `ListenStatsStore` / Room, so the next pick already reflects the tap you just made.
