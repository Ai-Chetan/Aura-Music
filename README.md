# Aura — Personal Music Vault for Android

A private, on-device music player: paste a YouTube link, keep the best-available audio, organize everything with tags, and listen through a futuristic animated UI with full system playback integration.

> **Responsible-use note:** Aura saves audio for your own private listening only. Automated downloading may conflict with YouTube's Terms of Service — only save content you have the right to keep, and never redistribute downloaded files. The app contains no sharing or bulk-export features by design.

## Screenshots

| Library | Download | Now playing |
|---|---|---|
| ![Library](docs/screenshots/library.png) | ![Download](docs/screenshots/download.png) | ![Now playing](docs/screenshots/now-playing.png) |

| Song detail | Backup | Lock screen |
|---|---|---|
| ![Song detail](docs/screenshots/song-detail.png) | ![Backup](docs/screenshots/backup.png) | ![Lock screen](docs/screenshots/lock-screen.png) |

## Features

- **YouTube → local audio** — paste any link: single videos, Shorts, playlists (up to 50 tracks), or a video shared from inside a playlist (offers the whole list in one tap). Preview, progress, and per-video failure isolation. Best stream kept as-is (Opus/AAC, no re-encode); `HQ • BEST` / `HQ` quality badges.
- **Tag-powered library** — many-to-many tags, AND/OR multi-tag filter, title/artist search, per-song tag editor, starter tags on first launch.
- **Real player** — ExoPlayer via Media3 `MediaSessionService`: queue, play-next, reorder, shuffle, repeat off/one/all, background playback, media notification + lock-screen controls.
- **Animated UI** — Compose + Material 3 with an ambient gradient background, live audio-reactive waveform, glass cards, tag chips, mini-player shell.
- **Backup & restore** — versioned JSON export (full / exclude-tags / selected songs) with share sheet, plus validated import that re-downloads and merges tags.

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose + Material 3 |
| Playback | Media3 (ExoPlayer + MediaSession) |
| Extraction | NewPipeExtractor (on-device, no API key) |
| Database | Room (songs, tags, playlists, queue state) |
| Background work | WorkManager + Coroutines/Flow |
| DI | Hilt |
| Storage | App-specific external storage (no storage permission needed) |
| Images / network | Coil, OkHttp |

Full details: [`docs/architecture.md`](docs/architecture.md) · [`docs/data-model.md`](docs/data-model.md)

## Getting started

**Prerequisites:** Android Studio (recent stable), JDK 17, an Android device or emulator (API 26+).

```bash
git clone https://github.com/Ai-Chetan/Aura-Music.git
cd Aura-Music
```

1. Open the project in Android Studio and let Gradle sync.
2. Run the `app` configuration on your device/emulator.
3. Paste a YouTube link on the Download tab → preview → download → tag it in the song page.

No API keys, no backend, no accounts. Everything runs on-device.

## Project structure

```
app/src/main/java/com/aura/music/
├─ ui/          theme, library, player, addsong, songdetail, backup, navigation, components
├─ domain/      repository interfaces
├─ data/        Room db, NewPipe extraction, WorkManager download, repo impls, backup codec
├─ playback/    MediaSessionService, MediaController wrapper, audio visualizer
├─ di/          Hilt modules
└─ util/        YouTube URL handling, time formatting
```

## Docs

- [`docs/overview.md`](docs/overview.md) — what Aura is, goals, constraints
- [`docs/architecture.md`](docs/architecture.md) — tech stack, extraction pipeline, package layout
- [`docs/data-model.md`](docs/data-model.md) — Room schema, repository + playback contracts, backup format
- [`docs/build-guide.md`](docs/build-guide.md) — build phases and what's next
- [`docs/backlog.md`](docs/backlog.md) — shipped vs planned features

## Permissions

- `INTERNET` — extraction, downloads, artwork
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — background playback
- `POST_NOTIFICATIONS` — playback notification (asked at runtime on Android 13+)

## Roadmap

Next up: playback speed, sleep timer, crossfade, smart tag-playlists, most/recently-played views, home-screen widget. See [`docs/backlog.md`](docs/backlog.md).

## Contributing

Issues and PRs are welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

MIT — see [`LICENSE`](LICENSE). Note that third-party dependencies carry their own licenses (in particular, NewPipeExtractor is GPL-3.0).
