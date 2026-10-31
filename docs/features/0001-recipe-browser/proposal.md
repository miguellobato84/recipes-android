# 0001 Read-only recipe browser proposal

Decision: Accepted  
Delivery: Not started

## Problem

The personal recipe collection lives in Markdown on GitHub. Its owner wants a
simple Android reader that remains usable offline after an explicit download.

## Intended outcome

Browse the same category hierarchy as the recipe repository and read recipes on
a Pixel 10. The recipe repository remains the canonical source; the app is a reader.

## Scope

- Spanish interface, Android 16+, app name **Recetas**.
- Manual download from public `miguellobato84/recipes`, branch `main`.
- Empty first launch; persistent cache after a successful refresh.
- Alphabetical folder navigation and complete recipe details.
- Full cache replacement after download and validation succeed.
- Signed APK distribution through GitHub Releases, triggered by pushes to `main`.
  The workflow creates sequential integer tags (`v1`, `v2`, ...).

No recipe editing, search, filters, authentication, repository settings,
background refresh, or Play Store publication in this version.

## Technical direction

Use native Kotlin and Jetpack Compose, few dependencies, a local file cache,
and optimized release builds. Measure APK size and responsiveness during delivery;
no numeric performance target or benchmark result has been agreed yet.

Use HTTPS to retrieve one coherent repository snapshot rather than installing a
Git client on the phone. Keep recipe refresh independent of APK releases.

## Alternatives and tradeoffs

- Bundled recipes were rejected: the first download must be user initiated.
- Automatic refresh was rejected: network access for recipes must be explicit.
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
| 2026-10-31 | Release on main pushes with integer tags | Replaces tag-triggered releases; implemented by feature 0002. |

See [the specification](spec.md) for behavior and acceptance criteria.
