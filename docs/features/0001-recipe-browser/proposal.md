# 0001 Read-only recipe browser proposal

Decision: Accepted

Delivery: In progress (tree navigation and category badges implemented locally; Android-device validation pending; startup refresh: v8; first release: v6)

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
- Refresh automatically on startup and by pulling down on the recipe tree; retain
  the cache and show a Spanish error on failure.
- One expandable tree containing the full recipe hierarchy, initially collapsed in each new browsing session; separate complete recipe details.
- Omit the redundant “Carpeta” subtitle. Capitalize the first character of list
  titles and screen-header titles for display, preserving remaining text and source paths.
- Automatic semantic icons and colored badges for every folder, with readable
  light/dark variants and stable neutral fallbacks for unknown categories.
- Preserve expansion and scroll while opening details, switching to shopping, rotating, or refreshing. Collapsing a branch resets its descendants.
- Full cache replacement after download and validation succeed.
- Use the same header layout for list, recipe detail and shopping list: title
  on the left, available action icons on the right. Detail shows the recipe title
  instead of its category; display that title once, in the header.
- Recipe refresh shows progress while active and messages only for errors.
  Successful startup and pull-to-refresh leave no persistent success banner.
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
- Manual refresh was replaced by startup refresh at the owner's request; a
  pull-to-refresh gesture is available on the recipe tree. No refresh button,
  polling, or refresh on background resume is provided.
- Keeping deleted recipes was rejected: successful refresh mirrors the source.
- App code in the recipe repository was rejected in favor of a separate repository.
- Android 8 support was replaced by Android 16+ support for the owner's Pixel 10.
- Full snapshot validation favors a consistent cache; one invalid recipe prevents
  the refresh from replacing any existing recipes.

## Tree navigation decisions

Tap an entire folder row to toggle its branch, with an arrow and Spanish
accessibility state. Multiple branches may remain open. Each level lists folders
before recipes using existing Spanish alphabetical ordering. Recipe titles open
existing details, including shopping actions and personal notes. No global
expand/collapse controls. Android Back returns from details to the same tree
position, and from the tree leaves the app. New sessions reset the tree; rotation
and destination changes preserve it. Refresh keeps surviving expansion; newly
added folders start collapsed and removed recipes return to the tree.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Simplify list labels | Owner requested removing “Carpeta” beneath folders and capitalizing the first letter of titles. Apply display capitalization without changing source data or ordering. |
| 2026-10-31 | Unify headers and remove recipe-refresh success banners | Owner requested matching list/detail/cart headers with actions on the right, recipe titles instead of category breadcrumbs, and recipe status messages only for errors. |
| 2026-10-31 | Accept category badges after grilling | Owner confirmed automatic semantic colors/icons, independent nested identities, distinct sibling combinations, neutral fallbacks, and light/dark readability. |
| 2026-10-31 | Accept initial scope | Owner confirmed the design after discussion. |
| 2026-10-31 | Begin with repository and documentation | App implementation is deferred until after this setup. |
| 2026-10-31 | Release on main pushes with integer tags | Replaces tag-triggered releases; tracked as scaffolding infrastructure. |
| 2026-10-31 | Resolve F1 implementation decisions | Verified source commit `494130cd4dc66f0ef3095040830426e2a839d898`: 27 recipes, text and decimal servings, nested folders, and `desayunos`. Use bounded ZIP snapshots and native Markdown rendering; preserve cache on every pre-commit failure. |
| 2026-10-31 | Refresh recipes on startup; remove Actualizar | Owner requested automatic refresh when opening the app. Cached recipes remain available during refresh and after failure. |
| 2026-10-31 | Add pull-to-refresh on folder lists; remove Atrás button | Owner requested a gesture for refreshing and relies on Android system back navigation. |
| 2026-10-31 | Accept expandable recipe tree | Owner confirmed inline expansion, new-session collapse, descendant reset, preserved return position, and refresh behavior after grilling. |

F1 is published in signed release [v6](https://github.com/miguellobato84/recipes-android/releases/tag/v6).
The startup-refresh revision is published in signed release
[v8](https://github.com/miguellobato84/recipes-android/releases/tag/v8). New host
and release checks passed; device validation remains pending.

See [the specification](spec.md) for behavior and acceptance criteria.
