# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Jokguboard: a jokgu (족구)/Futnet scoreboard. It's a single static web app (no build system, no npm/package.json) plus a thin Android WebView wrapper. Web: https://jokguboard.vercel.app (`/play` route). Repo: https://github.com/golee75git/Jokguboard.git. Most docs, comments, and UI strings are in Korean.

**Scope guard:** this repo is `C:\Jokguboard` only. Sibling repos `C:\Score`, `C:\PickleBoard`, `C:\sports_scoreboard` are never modified from here — patterns from `Score` (e.g. `applyDelta`, `historyStack`/undo, BLE GATT packet format) are independently reimplemented in this repo, not copied.

## Commands

- **Local web preview:** `./serve.ps1` (PowerShell; optional `-Port`) — serves the repo root at `http://127.0.0.1:8080/jokgu_scoreboard.html` via `python -m http.server`. No dev server/bundler exists otherwise; just open the HTML files directly if Python isn't available.
- **Regression test (win/serve-rotation rules):** `node scripts/check-win-rules.js` — plain Node script (no test framework/deps), exits non-zero and prints failures on mismatch, prints `win-rules ok` on success. Run this after touching any scoring/deuce/sideout logic.
- **Android debug build:** from `android/`, `./gradlew assembleDebug` (or `gradlew.bat` on Windows). Output debug APKs are manually copied into `downloads/` and named `app-debug-MMDD_HHMM-<short-desc>.apk` (KST).
- **Deploy:** Vercel static hosting, no build step (`vercel.json` only rewrites `/play` → `/jokgu_scoreboard.html`). Pushing to the linked branch/`vercel deploy` is sufficient.

There is no linter, formatter, or JS/TS build pipeline in this repo.

## Architecture

### The web app is one file
`jokgu_scoreboard.html` (~4800 lines) contains all markup, CSS, and JS for the scoreboard inline — there's no module system, framework, or bundler. `index.html` and `help.html`/`help_en.html`/`help_zh.html` are separate static landing/help pages.

### `JK_*` marker convention
Every feature area in `jokgu_scoreboard.html` (and the Android Kotlin sources) is wrapped in a comment marker like `/* JK_CORE: ... 되돌리: ... */`, naming the feature and how to cleanly revert just that block. When editing, prefer extending/adding a new marked block over intermixing logic across markers, and note the revert instructions when you add one. `docs/jokgu-plan.md` keeps the current list of active markers (`JK_CORE`, `JK_DELTA`, `JK_ROTATE`, `JK_APK_LR`, `JK_HID_PAD`, `JK_COURT_DISC`, `JK_SCORE_SKIN_CSS`, etc.).

### Duplicated HTML copies must stay in sync
The Android app does **not** read the root HTML files directly. It bundles its own copies under `android/app/src/main/assets/www/` (`jokgu_scoreboard.html`, `help.html`, `help_en.html`, `help_zh.html`), loaded via `WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/www/...` (see `MainActivity.kt`). Any change to a root HTML file must be mirrored into `android/app/src/main/assets/www/` before cutting a new APK — there is no build step that copies them automatically.

### Scoring rules live in JS and are mirrored in the test script
Rule logic (`isGameWin`, `isFutnetSetWon`, `isSetWon`, sideout/serve-index rotation) is implemented inline in `jokgu_scoreboard.html` under markers `JK_SPORT_RULES`/`JK_CORE`/`JK_ROTATE`/`JK_DELTA`. `scripts/check-win-rules.js` reimplements the same functions standalone (no import, since the HTML has no module boundary) purely to regression-test them. **If you change the win/deuce/rotation logic in the HTML, update the mirrored copy in `check-win-rules.js` too**, or the test will silently stop reflecting reality.

Current rules (see `docs/jokgu-prd.md` for full detail):
- **jokgu**: 4 players/side, 15 points win-by-2, deuce from 14:14, best-of-3 sets. Rally scoring; sideout rotates only the team that just lost serve, 1→2→3→4.
- **futnet**: 11 points win-by-2, capped at 15 (15:14 ends it), always best-of-3. Scoring team always serves next (no forced rotation). Singles/doubles/triples supported.

State persists to `localStorage` under key `jokgu_scoreboard_state_v1`.

### Android app is a thin native shell
`android/app/src/main/java/com/jokgu/scoreboard/`:
- `MainActivity.kt` — hosts the `WebView`, wires JS-to-Android bridges, and intercepts hardware input (external Bluetooth volume keys, media keys, a USB/BT pointer device) to drive the board without touching on-screen UI. Bridges exposed via `addJavascriptInterface`:
  - `AndroidShell` — liveness ping.
  - `AndroidTTS` — score/serve/set/game speech (`speak`/`isReady`/`setSpeechRate`), backed by `android.speech.tts.TextToSpeech`.
  - `AndroidBleScore` — pushes left/right game+set scores to `BleScoreSpikeServer` for BLE GATT notify.
  - `AndroidPad` — persists per-device screen-position calibration (`SharedPreferences` "jokgu_pad_marks") so an external remote's hit zones can be aligned; only exposed/used inside the APK, not on web.
  - These bridges are `undefined` on web — any board JS calling them must feature-detect first (existing code does this via `typeof Android... !== 'undefined'` checks).
- `BleScoreSpikeServer.kt` — GATT server broadcasting a fixed-format score packet (`[version, leftGame, rightGame, leftSet, rightSet]`, currently v2) using the same service/characteristic UUIDs as the sibling `Score` project's watch client, so existing score-watch hardware can pair with this app unmodified.

### Docs and change-logging convention
- `docs/jokgu-prd.md` — current product rules/scope, kept up to date (not historical).
- `docs/jokgu-plan.md` — implementation status table, file map, and the authoritative `JK_*` marker list.
- `docs/jokgu-changelog.md` — dated narrative history, oldest-first per day.
- `docs/jokgu-apk.md` — one row per shipped debug APK with its git commit hash; "current" APK filename is echoed in `index.html`/help pages and referenced in `docs/jokgu-prd.md`.
- `수정기록.txt` — detailed Korean per-change log (what changed, license/security notes, prior-art/patent-caution notes). Existing entries are a useful template for the level of detail expected on a new entry.

When shipping a new APK, the convention is to update all of: the APK's own filename embedded in `index.html`/`help*.html`, `docs/jokgu-apk.md`, `docs/jokgu-plan.md`, `docs/jokgu-prd.md`'s "현재 설치 APK" line, and append to `docs/jokgu-changelog.md` and `수정기록.txt`.

### Licensing constraints (enforced by convention, not tooling)
No new npm dependencies, no GPL-licensed code. Fonts are Google Fonts CSS only (SIL OFL 1.1: Noto Sans KR, Jua, Do Hyeon, Orbitron) with system-font fallback when offline — see `LICENSE-FONTS.txt`. Android deps are limited to AndroidX core-ktx/appcompat/webkit (Apache-2.0). Team names are rendered via `textContent` (no HTML injection from user input). No camera/microphone permissions.
