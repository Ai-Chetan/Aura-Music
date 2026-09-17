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
| Local database | Room (SQLite) | Songs, tags, playlists, play history — relational, Flow/Compose-friendly |
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

1. **Input**: user pastes a YouTube URL (video or `/playlist` link).
2. **Validate + canonicalise**: `YoutubeUrls` accepts watch, `youtu.be`, shorts, `music.youtube`, embed, `/live/`, and `/playlist?list=` links; everything is canonicalised to `watch?v=<id>` / `playlist?list=<id>` for duplicate detection and storage. A `watch?v=…&list=…` link (shared from inside a playlist) resolves as its single video, and the UI offers the whole playlist as a one-tap alternative.
3. **Resolve**: `NewPipeExtractionRepository` fetches `StreamInfo` (with 3-attempt retry), including available `AudioStream`s.
4. **Select best stream**: highest-bitrate Opus first, then AAC, then highest of anything else. No transcoding — bytes are stored as-is.
5. **Preview**: title, uploader, duration, thumbnail and quality badge are shown before download; duplicates already in the library are reported immediately.
6. **Download**: a WorkManager `CoroutineWorker` streams the audio URL to `getExternalFilesDir("songs")` via OkHttp with progress callbacks.
7. **Artwork**: highest-resolution thumbnail is downloaded and cached locally.
8. **Insert into Room**: a `SongEntity` pointing at the local file path, plus tags (user-added, or playlist-title tag on batch import).
9. **Playlist path**: playlist URLs resolve up to 50 video URLs (paginated); each is downloaded individually so one private/deleted video never aborts the batch.

## 5. Module / package structure (actual)

```
app/src/main/java/com/aura/music/
├─ AuraApp.kt / MainActivity.kt
├─ ui/
│   ├─ theme/           (colors, typography, Material3 color scheme)
│   ├─ library/         (song list, tag filter chips, search)
│   ├─ player/          (now-playing screen + queue sheet)
│   ├─ addsong/         (paste-link screen, preview, single + playlist progress)
│   ├─ songdetail/      (song page, tag editor)
│   ├─ backup/          (JSON export/import UI)
│   ├─ navigation/      (NavHost, bottom tabs, MiniPlayer shell, notch waveform)
│   └─ components/      (AlbumArt, AmbientBackground, AudioReactiveWaveform,
│                        GlassCard, MiniPlayer, QualityBadge, SongRow, TagChip)
├─ domain/
│   └─ repository/      (SongRepository, TagRepository, ExtractionRepository interfaces)
├─ data/
│   ├─ db/              (Room entities, DAOs, AppDatabase)
│   ├─ extraction/      (NewPipe wrapper, OkHttp downloader shim)
│   ├─ download/        (WorkManager DownloadAudioWorker)
│   ├─ repository/      (repository implementations)
│   ├─ backup/          (LibraryBackup JSON codec)
│   └─ DefaultTagSeeder.kt (starter tags on first launch)
├─ playback/
│   ├─ PlaybackService.kt            (MediaSessionService + ExoPlayer)
│   ├─ PlaybackController.kt         (interface + PlaybackUiState)
│   ├─ Media3PlaybackController.kt   (MediaController wrapper)
│   └─ AudioVisualizer.kt            (Visualizer → waveform buckets)
├─ di/                  (Hilt: database, network, repository, playback modules)
└─ util/                (YoutubeUrls, TimeFormatter)
```

## 6. Permissions

- `INTERNET` — extraction/download and thumbnail fetch.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — background playback with a media notification (Android 14+).
- `POST_NOTIFICATIONS` — playback notification (Android 13+, requested at runtime).
- No `READ/WRITE_EXTERNAL_STORAGE` — app-specific storage avoids scoped-storage issues.

## 7. UI/design direction

- Dark, near-black base (`#0C131B`) with a blue → cyan signature gradient used on interactive elements and the now-playing background.
- `AmbientBackground`: slowly drifting gradient wash + bottom wave; tints toward the album-art color on Now Playing.
- `AudioReactiveWaveform`: live `Visualizer` FFT buckets rendered as bars in the status-bar zone and behind the player.
- Frosted-glass cards (`GlassCard`), pill-shaped per-tag color chips (`TagChip`), quality badges (`HQ • BEST` amber / `HQ` green).
- Design tokens live in `ui/theme/` — reuse them instead of hardcoding values per screen.

## 8. Data flow example: "play a filtered set of tagged songs on shuffle"

1. UI: user selects tags `sad` + `english`, toggles shuffle on.
2. `LibraryViewModel` queries Room for songs matching both tags.
3. `Media3PlaybackController.playQueue()` builds `MediaItem`s (`localFilePath` → URI, title/artist/artwork metadata).
4. `ExoPlayer.shuffleModeEnabled = true`; Media3 handles shuffle order internally.
5. `MediaSession` updates the system notification and lock screen automatically.
