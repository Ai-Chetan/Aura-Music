# Contributing to Aura

Aura is open source (MIT) and **contributors are welcome** — code, docs, design, and bug reports all count.

Thanks for stopping by. Issues and pull requests are welcome.

## Setup

1. Use a recent stable Android Studio with JDK 17.
2. Clone, open the project, let Gradle sync — no API keys or backend needed.
3. Run the `app` configuration on a device or emulator (API 26+).

## Scope (please respect this)

Aura is a **personal-use** music vault. Aura owns no songs — all rights stay with their respective artists, labels, and rights holders. PRs adding any of the following will be declined:

- Sharing, uploading, or bulk-export/redistribution of downloaded audio
- Analytics, ads, telemetry, or extra network calls beyond extraction/metadata/artwork
- DRM circumvention or support for paid/protected sources

## Code guidelines

- Keep all stream-extraction calls behind `ExtractionRepository` (single module to swap when YouTube changes things).
- All "what plays next" logic lives in `UpNextManager` + `RecommendationEngine` (`data/stream/`, `data/recommendations/`) — screens start sessions, they never build queues themselves. All taste signals stay on-device.
- Reuse the design tokens in `ui/theme/` — no hardcoded colors/spacing per screen.
- Keep comments self-describing and public-facing (no references to private/internal code).
- Match the existing architecture: `ui/` → `domain/repository` interfaces → `data/` implementations → `playback/` → `di/`.
- Schema changes need an explicit Room migration in `DatabaseModule` (never rely on the destructive fallback — it only covers downgrades).

## Pull requests

- One focused change per PR, with a clear description and what you tested on (device/API level helps — notification and lock-screen behavior varies by OEM).
- Docs live in `docs/` — update them if your change alters behavior or scope.
