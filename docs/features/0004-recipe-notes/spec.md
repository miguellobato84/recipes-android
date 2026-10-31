# 0004 Recipe notes specification

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

Related: [proposal](proposal.md), [recipe browser](../0001-recipe-browser/spec.md).

## Observable behavior

After the complete source Markdown, including its original **Notas**, show a
multiline plain-text input labeled **Mis notas**. Preserve Unicode, whitespace,
and line breaks exactly. New recipes start with an empty box. Typing updates
the visible text immediately and starts autosave without a Save button.
Clearing all text removes that recipe's saved note.

Notes remain available when changing recipes or switching to the shopping list,
and across activity rotation. Saves belong to an activity-scoped ViewModel, so
leaving the editor does not cancel them. Show Spanish loading, pending-save,
saved, or failure feedback. While loading, disable editing. A load error protects
the existing file from replacement and offers retry. A save error retains the
current draft in memory and the last complete disk snapshot; retry or another
edit attempts to save the latest draft. Only a successful disk commit counts as
saved. Force-stopping/killing the process during a pending save can lose that
unsaved edit; previously committed notes remain complete.

## Data and integration

Store a versioned UTF-8 JSON snapshot at private `filesDir/recipe-notes.json`,
mapping repository-relative recipe paths to nonempty strings. Titles do not form
part of identity. A changed recipe at the same path retains its note. A renamed
path is a separate identity; removed paths retain their notes for restoration
if the recipe returns at the same path. Recipe refresh and shopping **Vaciar**
never write this store. Notes never change source Markdown or shopping snapshots.

Serialize disk writes off the UI thread. Coalesce edits made during a pending
write into the next latest snapshot; an older completion must never replace a
newer visible draft. Sync a same-directory staged file and atomically replace the
active file; ignore uncommitted staging on load. Reject unsupported or malformed
active snapshots without overwriting them. No new dependencies are needed.

Private app data survives Android cache clearing, restart, offline use, and
normal same-app APK updates. App-data clearing and uninstall remove the notes;
there is no backup or cross-device sync guarantee.

## Acceptance criteria

- One accessible multiline **Mis notas** input follows the complete recipe.
- Plain text, Unicode, whitespace, and line breaks round-trip exactly.
- Edits save automatically; navigating away retains drafts and allows saves to finish.
- Rapid edits, including edits during a save, persist the latest value.
- Notes stay isolated by path, including recipes with identical titles.
- Empty text deletes only that recipe's note; other notes remain intact.
- Reopening offline restores saved notes independently of recipe refresh.
- Recipe title/content changes and shopping-list clearing preserve notes.
- Cache clearing does not target the notes file.
- Failed saves preserve the prior disk snapshot and allow retry of the draft.
- Invalid loads and interrupted stages cannot silently erase committed notes.

## Validation and delivery history

Test store round-trips, empty deletion, path isolation, restart, staged writes,
failure/retry, load protection, and edits arriving during IO. Build debug and
optimized release variants and run Android lint plus existing offline tests.
Check input placement, keyboard scrolling, navigation, rotation, accessibility,
cache clearing, process restart, and APK-upgrade retention on Android when a
device is available. Record unavailable checks explicitly.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | In progress | Owner confirmed scope; proposal, specification, and index created before implementation. |

## Local validation (2026-10-31)

- `./gradlew --no-daemon testDebugUnitTest lintRelease assembleDebug assembleRelease`
  passed with existing JDK 17.0.19 and SDK Platform 36. The SDK was selected with
  `ANDROID_HOME`; no dependency, signing, or release configuration changed.
- All 42 offline JVM tests passed, including eight notes tests. The existing
  opt-in live-source network test was skipped; no recipe-network code changed.
- Notes tests verify exact Unicode/whitespace/multiline plain-text round-trip,
  immediate drafts, independent source-path keys, offline reopen, empty deletion,
  retention through a real fixture recipe-cache refresh and shopping-list clear,
  old disk preservation and draft retry after a simulated atomic-commit failure,
  latest-edit coalescing during paused IO, corrupt-load edit protection and retry,
  interrupted stages, unsupported versions, and invalid stored entries.
- Source review confirms the input follows the complete Markdown, including its
  source **Notas**. An activity-scoped ViewModel owns drafts and pending writes;
  stable `key(recipe.path)` prevents source-content refresh from recreating the
  editor unnecessarily. Keyboard insets use `imePadding`; the input is multiline
  with a visible label and status uses a polite accessibility live region.
- Android cache retention is established structurally by storing notes in
  `application.filesDir`, never `cacheDir`. A host sibling-cache deletion test
  preserves the note file; Android Settings cache-clearing remains untested.
- Android lint passed with zero errors and eight warnings: existing pinned
  dependency updates, backup configuration, icon qualifier, and URI KTX style.
  `git diff --check` passed. Debug and optimized unsigned release APKs built.

`adb devices` returned no connected devices; this SDK has no emulator binary and
no AVD is configured. Input/keyboard scrolling, navigation, rotation, TalkBack,
large fonts, light/dark appearance, Android process restart/force-stop, actual
cache clearing, filesystem interruption behavior, and same-signature APK-upgrade
retention remain untested on Android. Host storage tests do not establish those
device outcomes. Implementation is local; no release has been published.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Implemented locally, not released | Eight notes tests and all existing offline suites, lint, debug/release builds passed. Android-device checks remain pending. |
