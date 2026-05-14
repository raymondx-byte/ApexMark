# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.1] - 2026-05-14

### Added

- GitHub Actions workflow `build.yml`: on every push and pull request, runs `./gradlew assembleDebug test`.
- GitHub Actions workflow `release.yml`: on tags matching `v*`, builds signed `:app:assembleRelease`, uploads APK and `SHA256SUMS.txt`, and appends SHA-256 to the release description (configure repo **Secrets**: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
- `.github/ISSUE_TEMPLATE/` (`bug_report.md`, `feature_request.md`) and `.github/PULL_REQUEST_TEMPLATE.md`.
- JVM library module **`apex-link-core`** (`com.apexmark.link.core`): `ApexLinkMarkdownCore` + `StyleStyler` — Markdown / HTML string pipeline without Android clipboard APIs (same AGPL-3.0 as the repo; library use triggers copyleft like the app). **Not on Maven Central yet**; consume via Gradle `include` from this repo.

### Changed

- README: short “why try this” lead-in, paste-target guidance (**WeChat / chats → → HTML**, **WPS → → WPS**), CI badge, link to this changelog; trimmed inline release history in favor of this file.

## [1.1.0] - 2026-05-14

### Added

- Notification row: centered localized **“tap to convert”** hint (`notif_tap_convert_hint`); tap opens the same translucent secondary menu as the floating bubble (with type line).
- `ClipboardPeekActivity` registered in the manifest for bubble clipboard classification.
- Notification refresh coalesced with a short main-thread debounce; foreground path uses a single `startForeground` notification id.
- Shared `ConvertMenuUi` for bubble popup and notification menu; `Theme.NotificationMenu`.

### Changed

- Markdown tables: neutral transparent cells and black borders by default.
- Release build: R8 full mode (`android.enableR8.fullMode=true`); removed legacy `ic_notification` asset in favor of minimal `ic_notif_stat_silent.xml`.

### Removed

- In-app “clipboard debug inspector” entry point.
