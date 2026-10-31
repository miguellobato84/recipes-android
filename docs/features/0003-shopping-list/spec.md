# 0003 Shopping list specification

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

Related: [proposal](proposal.md).

## Navigation

Provide a bottom navigation bar with **Recetas** and **Lista de la compra**.
The recipe destination includes F1's folder listing and recipe-detail screens.
The shopping-list destination shows ingredient contributions, included recipes,
their counts, and **Vaciar**. Switching destinations must not clear the list.

Keep the current recipe folder or detail visible when returning from shopping
list. Android back from shopping list returns to **Recetas**; standard F1 back
navigation continues within recipe folders/details.

## Add ingredients from a recipe

Add **Añadir a la lista de la compra** to recipe details. One tap contributes all
items from that recipe's **Ingredientes** section, including their quantities
and notes. Utensils, steps, and other sections are not shopping items.

Each completed addition is a distinct contribution. Adding the same recipe twice
adds its complete ingredient set twice and increases its displayed count to two.
Do not treat a repeat tap as a duplicate request to discard. Add the complete set
and update its recipe count together; a failure must not leave a partial addition.

Show Spanish success or error status without leaving the recipe.

## Shopping-list contents

Display both the ingredients to buy and the included recipes with their counts.
Use counts of recipe additions, not servings: **2x Lentejas estofadas** means the
recipe was added twice and does not redefine its original servings.

Each addition stores the complete original Markdown from the recipe's
`Ingredientes` section as a separate immutable contribution. The shopping list
renders that Markdown, retaining text, quantities, notes, and list structure.
Contributions are grouped for display by stable source path. A recipe summary's
title is the title from its most recent addition. Earlier ingredient snapshots
are not rewritten when a later addition captures a renamed or edited recipe.
Two different paths with the same title remain distinct groups.

Example, with a hypothetical recipe containing **200 g de lentejas** and
**1 cebolla**:

- After one addition, show **1x Lentejas estofadas** and one contribution of each
  ingredient.
- After two additions, show **2x Lentejas estofadas** and two separate
  contributions of each ingredient. Do not multiply quantities or merge rows.
- Adding a different recipe retains these contributions and adds its own recipe
  count and ingredients.

Track recipes by stable source path rather than title alone, so different files
with the same title do not accidentally share a count. Display titles to users.

Do not multiply quantities, convert units, fuzzy-match, or merge contributions.

## Clear the list

**Vaciar** removes every ingredient contribution and included-recipe count.
The resulting screen shows an empty state such as **La lista de la compra está
vacía**. Clearing an already-empty list leaves it empty. Adding a recipe after
clearing starts that recipe at **1x**.

Clearing affects only shopping-list state. It never deletes source recipes,
the recipe cache, navigation categories, or app-update state.

## Storage and recipe refresh handling

Persist the list locally for offline use and retain it across process restarts
and normal same-app APK upgrades. Uninstall/data clearing removes local state.
Store all changes atomically and show a Spanish error if saving fails, retaining
the last complete list rather than claiming an addition or clear succeeded.

Snapshot each addition's source path, displayed title, and ingredient text.
A recipe refresh, edit, rename, or deletion must not silently remove or rewrite
already-added contributions. Adding a recipe after a source edit captures the
new title and ingredients; earlier contributions retain their original
ingredients. Contributions for the same path remain one group, whose displayed
title comes from its most recent addition.

## Acceptance criteria for requested scope

- A bar or side menu switches between the recipe list/details and shopping list.
- All recipe ingredients are added from the recipe detail action.
- Adding the same unchanged recipe twice contributes every ingredient twice and
  displays one included-recipe summary with count **2x**.
- Adding another recipe preserves earlier contributions and shows both recipes.
- Distinct recipe paths with identical titles remain distinct contributions.
- Switching modes retains ingredient contributions and recipe counts.
- **Vaciar** empties both ingredients and recipe summaries without changing recipes.
- Adding after **Vaciar** starts again at **1x**.

## Resolved implementation decisions and validation

Resolved implementation choices: recipe groups use first-addition order;
contributions append within their group. A path rename creates a distinct group.
Save and restore each destination’s Compose state while switching so
folder/detail scroll position survives. Serialize every accepted add/clear; a tap during an ongoing save queues
rather than disappearing. Disable mutations only until initial load completes or
if stored data cannot be read. A load failure preserves the unreadable file and
shows an error; reopening retries. An empty ingredient section cannot contribute
items and reports a Spanish error. Extract actual Markdown headings (including
setext headings), ignoring heading-like text inside code.

Storage uses a versioned binary private file with UTF-8 fields, at most 20,000
additions and 256 KiB per field. Limit violations fail the whole save with the
same Spanish save error. A staged file is synced before atomic replacement; an
interrupted uncommitted stage is ignored on load. No recipe-source writes occur.

Test repeated additions, complete ingredient preservation, recipe counts, identical
titles at different paths, clear-all, and switching destinations. If persistence
and snapshots are adopted, test offline restart, source changes/deletions, and
storage failure recovery. Verify navigation, readability, and accessibility on
Android; record unavailable device checks explicitly.

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Not started | Four requested behaviors documented; implementation details remain proposed. No F3 implementation or runtime tests. |
| 2026-10-31 | In progress | Accepted bottom navigation, saved browsing position, atomic local snapshots, source-path grouping, separate original ingredient contributions, latest-snapshot title, and immediate clear behavior before implementation. |

## Local validation (2026-10-31)

Base: fetched `origin/main` at `3365b5f`, branch `codex/shopping-list`, in the
existing recipes-android checkout. The starting checkout contained uncommitted
F3 documents, their index entry, and a partial ShoppingList.kt; implementation
continues those files in place. Initial validation preceded authorization to
commit and push F3. No source-recipe writes or signing-material access occurred. During validation,
another chat committed F2 release documentation as `f747e81`; that concurrent
commit was retained. It contains documentation changes only, so the tested app
base remains `3365b5f` plus the local F3 implementation.

- `./gradlew --no-daemon testDebugUnitTest lintRelease assembleDebug assembleRelease`
  passed using JDK 17 and the existing Android SDK Platform 36 / Build Tools 35.0.0.
  Set `ANDROID_HOME` to the existing SDK before running Gradle. Release output is
  an optimized unsigned local APK; no signing configuration was changed.
- All 34 offline JVM tests passed, including ten F3 tests. The opt-in live-source
  test was skipped; F3 uses the pinned source fixture and has no recipe-network
  changes. F1's six startup-flow tests and fourteen parser/cache tests passed;
  F2's four release-catalog tests passed.
- F3 tests cover exact original ingredient text/notes/uncertainties, code containing
  fake section headings, setext/closing-hash headings, all 27 pinned source recipes
  added twice (54 complete contributions), repeat counts independent of servings,
  equal titles at distinct paths, source edits/deletion/path changes, offline
  reopen, clear/reopen/add-again, taps queued during a paused save, save failures
  retaining byte-identical disk state and complete memory, interrupted staging,
  corrupt-load protection, empty sections, and field-size limits.
- Five release-version Python tests passed. Android lint passed with zero errors
  and eight warnings (dependency updates, backup configuration, icon qualifier,
  and the existing URI KTX suggestion). No dependency versions were changed.
- Navigation source review confirms separate saved-state providers, saved selected
  destination and recipe location, Android back from shopping to recipes, and
  existing folder/detail back behavior. Switching destinations does not call
  add/clear or recreate the activity-scoped shopping ViewModel. Both destinations
  and shared Markdown rendering compile in debug and optimized release builds.
- `git diff --check` passed. F1 startup code, F2 updater code, launcher resources,
  manifest, signing configuration, and main-triggered release workflow were not
  changed by F3. Obsolete cross-feature manual-refresh references were corrected.

Android checks unavailable: `adb devices` returned no devices; no emulator binary
or AVD is configured in the available SDK. Touch/back navigation, scroll and
rotation/process state restoration, rendering/readability, TalkBack, large fonts,
light/dark layout, Android-filesystem interruption behavior, airplane-mode restart,
and retention through a same-signature APK upgrade remain untested on Android.
Host tests establish saved snapshots on the host filesystem, not those device
outcomes. No release or Pixel performance claim is made.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Implemented locally, not released | Ten F3 acceptance tests plus existing offline suites, five version tests, debug/release builds and lint passed. Navigation source reviewed; Android interaction/restoration/accessibility and APK-upgrade checks remain pending. |

| 2026-10-31 | Feature-branch push authorized | Owner requested pushing the verified local implementation. Release and Android-device checks remain separate. |
