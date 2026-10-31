# 0001 Read-only recipe browser proposal

Decision: Accepted

Delivery: In progress (startup refresh revision; first release: v6)

## Problem

The personal recipe collection lives in Markdown on GitHub. Its owner wants a
simple Android reader that refreshes on startup and remains usable offline.

## Intended outcome

Browse the same category hierarchy as the recipe repository and read recipes on
a Pixel 10. The recipe repository remains the canonical source; the app is a reader.

## Scope

- Spanish interface, Android 16+, app name **Recetas**.
- Automatic startup download from public `miguellobato84/recipes`, branch `main`.
- No bundled recipes; load the persistent cache first, then refresh on startup.
- No refresh button; retain the cache and show a Spanish error on failure.
- Alphabetical folder navigation and complete recipe details.
- Full cache replacement after download and validation succeed.
- Signed APK distribution through GitHub Releases, triggered by pushes to `main`.
  The workflow creates sequential integer tags (`v1`, `v2`, ...).

No recipe editing, search, filters, authentication, repository settings,
periodic background refresh, or Play Store publication in this version.

## Technical direction

Use native Kotlin and Jetpack Compose, few dependencies, a local file cache,
and optimized release builds. Measure APK size and responsiveness during delivery;
no numeric performance target or benchmark result has been agreed yet.

Use HTTPS to retrieve one coherent repository snapshot rather than installing a
Git client on the phone. Keep recipe refresh independent of APK releases.

Use Markwon 4.6.2 core (CommonMark native text rendering), SnakeYAML 2.7
with safe construction, and Lifecycle ViewModel 2.9.4. Download the public codeload
ZIP of `main`; validate before atomically renaming the staged file over the cache.
All disk, network, parsing, and rendering preparation run off the UI thread.

## Alternatives and tradeoffs

- Bundled recipes remain excluded: the first startup downloads the public source.
- Manual refresh was replaced by startup refresh at the owner's request; no
  button, polling, or refresh on background resume is provided.
- Keeping deleted recipes was rejected: successful refresh mirrors the source.
- App code in the recipe repository was rejected in favor of a separate repository.
- Android 8 support was replaced by Android 16+ support for the owner's Pixel 10.
- Full snapshot validation favors a consistent cache; one invalid recipe prevents
  the refresh from replacing any existing recipes.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Accept initial scope | Owner confirmed the design after discussion. |
| 2026-10-31 | Begin with repository and documentation | App implementation is deferred until after this setup. |
| 2026-10-31 | Release on main pushes with integer tags | Replaces tag-triggered releases; tracked as scaffolding infrastructure. |
| 2026-10-31 | Resolve F1 implementation decisions | Verified source commit `494130cd4dc66f0ef3095040830426e2a839d898`: 27 recipes, text and decimal servings, nested folders, and `desayunos`. Use bounded ZIP snapshots and native Markdown rendering; preserve cache on every pre-commit failure. |
| 2026-10-31 | Refresh recipes on startup; remove Actualizar | Owner requested automatic refresh when opening the app. Cached recipes remain available during refresh and after failure. |

F1 is published in signed release [v6](https://github.com/miguellobato84/recipes-android/releases/tag/v6).
The startup-refresh revision is in progress. Previous host/release checks cover
the manual-refresh release; new validation is recorded in the specification.

See [the specification](spec.md) for behavior and acceptance criteria.
