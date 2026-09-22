# Project Overview — Aura

> Aura is open source (MIT) — contributors are welcome. See `../CONTRIBUTING.md` to report issues, improve docs, or open a PR.

## 1. What this is

**Aura** is a personal, single-user Android music player that:

1. Pulls songs from a pasted YouTube link (single video or playlist, up to 50 tracks) and saves the **best-available audio quality** locally on the device — no re-encoding, the stream bytes are stored as-is.
2. Lets you tag every song with one or more custom tags (`english`, `sad`, `party`, `romance`, etc.) and filter/browse your library by tag.
3. Gives you a full playback experience: queue, shuffle, repeat modes, playback speed-ready ExoPlayer core, background playback.
4. Has a futuristic, wavy, animated UI built with Jetpack Compose.
5. Shows a proper **media notification** and **lock-screen playback panel** (album art, title, seek bar, prev/play-pause/next) via Media3 `MediaSession`.
6. Runs **entirely locally** — no cloud account, no server, no analytics, no ads.
7. Supports **library backup/restore**: export songs + tags to JSON (by tag exclusion or explicit selection) and re-download later from the stored source URLs.

## 2. Who this is for

Anyone who wants a private, on-device music vault on Android: paste links, curate with tags, and listen with first-class system integration. Single local user, no login, no cloud sync in v1.

## 3. Goals

- **Own your library.** Every song you save lives in your phone's storage as a real audio file you control.
- **Organize your way.** Tags instead of rigid playlists/folders — a song can be `sad` + `english` + `romance` at once, and you can slice your library any way you want.
- **Feel premium.** A polished, smooth player: queue, shuffle, repeat, mini-player, now-playing screen with live audio-reactive visuals.
- **Respect the OS.** Proper background playback, notification controls, lock-screen controls via the standard `MediaSession` APIs.

## 4. Non-goals / constraints

- **Not a music owner or host.** Aura does not own, host, license, or distribute any songs. All music, artwork, and trademarks belong to their respective artists, labels, and rights holders. The app is a private player only — it keeps what you choose on your own device for offline listening.
- **Not a redistribution tool.** The app downloads audio for private listening only. It does not upload, share, or redistribute extracted audio.
- **Not truly "lossless" in the audiophile sense.** YouTube encodes all audio to Opus or AAC (typically 128–160 kbps). "No loss" here means: *grab the best native stream YouTube offers, and never re-encode or re-compress it afterward.* Tracks at ~150 kbps+ Opus are badged `HQ • BEST`, ≥128 kbps as `HQ`.
- **No official YouTube download API.** YouTube's Data API does not provide audio download; this app uses stream extraction (see `architecture.md`) the same way apps like NewPipe do. Automated downloading may conflict with YouTube's Terms of Service — use at your own discretion, for content you have the right to save, and do not redistribute.
- **No login system.** Single local user, no cloud sync.
- **No DRM circumvention.** Only freely-playable YouTube videos work — not paid or DRM-protected content from any platform.

## 5. Core features (v1)

| # | Feature | Notes |
|---|---|---|
| 1 | Paste YouTube URL → extract & save audio locally | Best available bitrate, correct title/artist/thumbnail |
| 2 | Playlist import (up to 50 songs) | Preview, duplicate count, optional tag-with-playlist; `watch?v=…&list=…` links offer the whole list in one tap |
| 3 | Tag songs (many-to-many) | Add/remove/create tags per song |
| 4 | Browse/filter library by tag(s) | Multi-tag filter (AND/OR toggle) |
| 5 | Full playback engine | Play/pause/seek/next/prev, background playback |
| 6 | Queue management | Add to queue, "play next", remove, play-at-index |
| 7 | Shuffle & repeat modes | Shuffle-all, repeat-one, repeat-all |
| 8 | Media notification | Album art, title/artist, transport controls |
| 9 | Lock-screen media panel | Native `MediaSession`-driven controls |
| 10 | Library backup & restore | JSON export/import with tag merge + re-download |
| 11 | Futuristic animated UI | Compose theme, ambient background (animated on Now Playing only), audio-reactive waveform |
| 12 | Local search | By title/artist |
| 13 | Getting Started guide | Skippable first-launch tour with coach tips + starter tracks that download in the background |
| 14 | YouTube Music search + instant streaming | Search songs, tap to stream without saving or save to the vault |
| 15 | Combined Add section | Search and paste-link under one segmented toggle |
| 16 | Library sort modes | Recent/oldest, title A–Z/Z–A, artist A–Z, longest/shortest; auto-scrolls to top |
| 17 | Live download queue | Collapsible overall-% card, per-song progress, cancel, starter-batch error report with retry |
| 18 | Navigation-proof playlist imports | Whole-playlist WorkManager import with live Library progress |
| 19 | Saved streaming bookmarks | Save without downloading; instant stream play, full tag/filter/sort tooling in the Saved tab |
| 20 | Home hub | Continue listening (downloads + Saved merged), top hits today, For You from your rotation |
| 21 | Recommendation engine | On-device taste model: replays, obsessions, skip/artist signals → ranked radio picks |
| 22 | Infinite radio | Top-hits/For-You/search taps start radio; playlists continue with recommendations at their dead end — playback never terminates |
| 23 | Library playlist controls | Shuffle play (filter-aware) + Spice-up switch pre-queueing engine picks |
| 24 | Swipeable mini player | Follow-the-finger drag with tilt, skip hints, fly-out page-turn animation |

Current screenshots live in `docs/screenshots/` and are shown in `README.md`.

## 6. Later milestones

- Playback speed control, sleep timer, crossfade/gapless tuning
- Smart/dynamic playlists from tag combinations
- Most-played stats / listening dashboard views
- Home-screen widget, waveform seek bar, volume normalization
- Android Auto, batch tag editing, duplicate detection, lyrics, themes, equalizer, M3U import/export

See `backlog.md` for the prioritized list.

## 7. Success criteria

- Pasting a YouTube link yields a playable local file with correct title/artwork within a reasonable time.
- Tagging works immediately and tag filters update live.
- Playback survives backgrounding, screen lock, and shows working transport controls on lock screen + notification shade.
- Shuffle/queue/repeat behave like mainstream music apps.
- The UI has a distinct, cohesive visual identity.

## 8. Document map

- `architecture.md` — tech stack, module breakdown, playback & extraction architecture
- `data-model.md` — database schema, entities, repository contracts
- `build-guide.md` — phased implementation plan
- `backlog.md` — prioritized feature list beyond v1

## 9. Author & rights

Developed by [@Ai-Chetan](https://github.com/Ai-Chetan) for the open-source community (MIT).

Aura claims no rights over any music — all songs and artwork belong to their respective owners. Please use the app for private listening to content you own or have permission to keep.
