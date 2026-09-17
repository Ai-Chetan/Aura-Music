# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Instead, use
GitHub's **private vulnerability reporting** (Security tab → Report a
vulnerability) or open a minimal issue asking for a contact channel.

Include what you can: affected version/commit, steps to reproduce, and impact.
You can expect an initial response within a few days.

## Scope notes

- Aura is a local-only app with no accounts, servers, or analytics. Its only
  network activity is YouTube stream extraction plus audio/thumbnail download
  over HTTPS.
- Downloaded audio files and the Room database live in app-specific storage;
  the JSON backup contains source URLs and tags only (no file paths).
