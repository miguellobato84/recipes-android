# 0003 Shopping list specification

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

Related: [proposal](proposal.md).

## Navigation

Provide a bottom navigation bar with **Recetas** and **Lista de la compra**.
The recipe destination includes F1's expandable recipe tree and recipe-detail screens.
The shopping-list destination shows ingredient contributions, included recipes,
their counts, and **Vaciar**. Switching destinations must not clear the list.

Keep the recipe tree expansion, tree scroll, and selected detail when returning
from shopping. Android Back from shopping returns to **Recetas**. F1 Back returns
from details to the preserved tree; Back from the tree leaves the app. A new
browsing session starts the tree fully collapsed.

## Add ingredients from a recipe

Add an icon-only shopping-cart button to the right of the recipe-detail header,
beside Share as specified
by [F5](../0005-recipe-sharing/spec.md). Preserve **Añadir a la lista de la compra**
as its accessibility label and long-press tooltip, with a full-size touch target.
One tap contributes all
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

Use the same header as recipe list/detail, titled **Lista de la compra**. On its
right, show a filled trash-icon button with spoken label and long-press tooltip
**Vaciar carrito**, and a touch target of at least 48 dp. This replaces the plain
**Vaciar** text action. Keep current loading/load-failure enablement and clear
behavior, including repeated taps while saves are queued.

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

- Shopping uses the shared header with the filled **Vaciar carrito** trash-icon
  button at the right, retaining spoken label, tooltip and loading/error guards.

- A bar or side menu switches between the recipe list/details and shopping list.
- All recipe ingredients are added from the recipe detail action.
- Adding the same unchanged recipe twice contributes every ingredient twice and
  displays one included-recipe summary with count **2x**.
- Adding another recipe preserves earlier contributions and shows both recipes.
- Distinct recipe paths with identical titles remain distinct contributions.
- Switching modes retains ingredient contributions, recipe counts, tree expansion,
  tree scroll, and selected detail within the current browsing session.
- **Vaciar** empties both ingredients and recipe summaries without changing recipes.
- Adding after **Vaciar** starts again at **1x**.

## Resolved implementation decisions and validation

Resolved implementation choices: recipe groups use first-addition order;
contributions append within their group. A path rename creates a distinct group.
Save and restore each destination’s Compose state while switching so
tree expansion, tree scroll, and detail scroll position survive within a session. Serialize every accepted add/clear; a tap during an ongoing save queues
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

## Shared-header revision validation (2026-10-31)

Implemented locally: the cart uses the shared header and a filled trash-icon
button on the right, with “Vaciar carrito” spoken label and tooltip. Its clear
callback, loading/load-failure guards, queuing and storage behavior are unchanged.
The recipe's add action now lives in the detail header. All 54 offline tests
(including ten shopping-list tests), release lint and debug/release builds passed.
Android interaction/accessibility checks remain pending because no device was
connected. See [F1 validation](../0001-recipe-browser/spec.md#shared-header-revision-validation-2026-10-31).

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Not started | Four requested behaviors documented; implementation details remain proposed. No F3 implementation or runtime tests. |
| 2026-10-31 | In progress | Accepted bottom navigation, saved browsing position, atomic local snapshots, source-path grouping, separate original ingredient contributions, latest-snapshot title, and immediate clear behavior before implementation. |
| 2026-10-31 | Icon-only detail action implemented locally | F5 replaces the detail text button with a cart icon, Spanish spoken label and tooltip; existing enabled predicate and addition callback are retained. Host shopping regression tests pass; Android interaction checks remain pending. |

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

### Tree navigation alignment (2026-10-31)

F1 now specifies inline folder expansion and session-scoped tree restoration.
The recipe destination now uses the browser session ID as part of its saved-state
key and retains tree scroll state while details are open. Destination switching
retains expansion and selected detail in that session; a new session cannot
reopen an old tree. All 47 offline tests (including ten shopping tests and five
new F1 tree tests), debug/release builds and release lint passed. No connected
Android device was available; actual destination/back/scroll/rotation restoration
and accessibility remain untested. Historical folder-navigation evidence above
does not establish revised tree behavior.
