# Feature Backlog

## Done (shipped)

- Add song from YouTube URL (single video, best-available audio + metadata)
- Playlist import (preview, duplicate detection, optional playlist-title tag, per-video failure isolation, 50-song cap; dedicated background worker survives navigation)
- Tagging (create/assign/remove, many-to-many, per-song editor)
- Filter/browse library by tag(s), AND/OR toggle
- Full transport: play/pause/seek/next/prev, background playback
- Queue management (play queue, play next, add to end, play-at-index, remove, reorder; index-mismatch + sheet-height fixes)
- Shuffle + repeat (off/one/all)
- Media notification with working transport controls
- Lock-screen media panel with working transport controls
- Local search (title/artist)
- YouTube Music search with instant streaming or save-to-vault
- Combined Add section (Search + Paste link under one toggle)
- Skippable Getting Started guide with coach tips + starter-track batch downloads
- Library sort modes (recent/oldest/title/artist/duration) with snap-to-top + batched rendering
- Live download queue (collapsible overall-% card, per-song progress, cancel, starter-batch failure report with retry)
- YouTube throttling hardening (concurrency gate, backoff retries, staggered batches, friendly errors)
- Swipe artwork on Now Playing to change tracks
- Custom futuristic UI theme (ambient background, reactive waveform, glass cards, chips, badges)
- Library backup/export + import (JSON, tag-exclusion or explicit selection, tag merge on restore)
- Most/recently-played tracking (`playCount` / `lastPlayedAt` collected in DB)

## Should-have (next)

- Playback speed control
- Sleep timer
- Crossfade between tracks
- Gapless playback tuning
- Smart/dynamic playlists from tag combinations (schema already supports `isSmart`)
- Recently played / most played views (data already collected — needs UI)
- Home-screen widget (mini player)
- Waveform-style seek bar
- Volume normalization on import

## Could-have

- Android Auto support
- Batch tag editing (select multiple songs, apply/remove a tag at once)
- Duplicate detection on import (match by source URL — partially done via canonical URLs)
- Basic lyrics display (cached locally)
- Multiple selectable themes
- Equalizer
- M3U playlist import/export

## Won't-have (explicitly out of scope)

- Multi-user accounts / cloud sync
- Sharing or exporting downloaded audio to other people or platforms
- Any monetization, ads, or analytics
- Support for DRM-protected sources
- Bulk redistribution tooling of any kind

## Ideas parking lot (not scoped)

- Voice-based tagging via on-device speech recognition
- Automatic mood/genre tag suggestion from audio analysis
- Per-tag custom sort order / radio-style endless shuffle
- Listening streak / stats dashboard
