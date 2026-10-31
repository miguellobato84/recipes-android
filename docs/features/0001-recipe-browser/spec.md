# 0001 Read-only recipe browser specification

Decision: Accepted

Delivery: In progress (tree navigation and category badges implemented locally; Android-device validation pending; startup refresh: v8; first release: v6)

Related: [proposal](proposal.md)

## Platform and identity

- App name: **Recetas**; interface language: Spanish.
- Minimum Android version: Android 16; intended device: Pixel 10.
- App repository: `miguellobato84/recipes-android`, public.
- Kotlin and Jetpack Compose; persistent local file cache.

## First launch and cached browsing

Start without bundled recipes. Load the local cache first, then automatically
attempt one recipe refresh when a new browsing session starts. A session starts
when a new activity-scoped browser ViewModel is created. Rotation and Compose
recomposition reuse that ViewModel; returning from the background to the same
activity does not refresh again. Finishing the activity and reopening the app,
or starting a new process, starts another session and refresh attempt.

There is no **Actualizar** button. On a fresh install, show loading/progress until
the initial attempt finishes. If it fails, show a Spanish error and remain empty.
Cached recipes stay browsable while the startup refresh runs. No periodic
refresh or additional automatic retry occurs within a session.

After a successful refresh, browse only the persistent cache. Cached content
survives app restarts and normal APK updates and remains readable offline.
Uninstalling the app or clearing its data is outside that persistence guarantee.

## Navigation

Show the complete repository hierarchy in one scrollable, expandable tree on the
main **Recetas** screen. Preserve arbitrary nested folders, including `desayunos`
and `principales/carne`. Initially show top-level folders only. All folders start collapsed whenever
a new activity-scoped browser ViewModel starts a browsing session, including
process recreation. Rotation reuses the session and preserves navigation.

At each level, list folders before recipes, each alphabetically ordered using
Spanish collation and path tie-breaking. Use recipe titles as recipe labels.
Indent rows by nesting level. The entire folder row toggles expansion; show a
state arrow and expose Spanish expanded/collapsed state to accessibility.
Multiple branches may stay open. Closing a folder clears expansion for all its
descendants, so reopening shows immediate children with nested folders collapsed.
No global expand-all or collapse-all controls.

Tapping a recipe opens its existing separate detail screen, including shopping
actions and personal notes. Android Back returns directly to the tree with its
expansion and scroll position preserved. Back from the tree leaves the app;
it does not collapse branches. No in-app **Atrás** button. Switching to/from the
shopping list preserves tree expansion, tree scroll, and the selected detail.

Pulling down from the top of the tree starts a recipe refresh. The gesture is
unavailable on recipe details or the shopping list. Keep surviving folders
expanded and remove deleted folder paths from expansion state. Newly added
folders start collapsed. Preserve the selected recipe if its path still exists;
otherwise return to the tree. Cached content remains browsable during refresh
and after failure. Empty collections show the existing Spanish empty state.

There is no search, category filter, or recipe editor.

### Folder appearance

Every folder row has a 40 dp rounded badge containing a 24 dp category icon.
Folder names and the existing subtitle retain standard theme text colors. Keep
the full-row toggle, state arrow, indentation, and Spanish accessibility state.
The icon is decorative: the existing folder label identifies its meaning to
TalkBack. Color is never the only category or expansion cue.

Assign appearance automatically in the app; no picker, network lookup, source
metadata, or appearance preferences are required. Match the final folder name
case/accent insensitively, including nested categories, without inheriting the
parent's identity. Use these semantic mappings:

| Category | Color family | Icon |
| --- | --- | --- |
| desayunos | Amber | Sunrise |
| dulces | Pink | Cake |
| entrantes | Teal | Small plate |
| guarniciones | Olive | Bowl |
| masas | Wheat | Rolling pin |
| panificadora | Toast brown | Bread |
| principales | Burnt orange | Covered dish |
| carne | Brick red | Meat |
| pescado | Blue | Fish |
| vegetarianos | Green | Leaf |
| salsas | Purple | Sauce boat |

Unknown folder names receive a neutral folder icon with a muted, path-derived
badge. Resolve duplicate sibling icon/color combinations deterministically with
nearby shades, preserving the category color family. Assignment uses all folder
paths, including collapsed branches, and is stable across restarts for the same
hierarchy, input ordering, expansion, and recipe-content changes. Siblings have
distinct combinations; reuse across separate parents is allowed.

Provide explicit light/dark foreground and background pairs. Require at least
3:1 icon-to-badge contrast in both modes; retain theme-managed text contrast.
Exact light and dark shades can differ while retaining their conceptual hue.

## Recipe details and source format

Preserve the recipe's text and formatted Markdown, including headings, ingredient
and utensil bullets, numbered steps, notes, links, and inline code. Show servings
and tags as metadata; hide raw YAML. Open external links outside the app.

[F4 personal notes](../0004-recipe-notes/spec.md) adds a separate editable local
text box after the complete source recipe. Source recipe content stays read-only.

The current source contract is YAML schema version `1`, with fields
`version_esquema`, `titulo`, `categoria`, `etiquetas`, and `raciones`.
Treat servings as display text: existing values include nonnumeric descriptions.

Recipes have one H1 matching `titulo` and H2 sections `Ingredientes`, `Utensilios`,
`Pasos`, and `Notas`, in that order. Folder/category metadata must agree.
Ignore repository documentation and other non-recipe assets when importing.

Source conflict recorded during planning: the recipe repo's README and existing
files include `desayunos`, while its AGENTS.md category list omits it. Accepted
app behavior follows actual recipe folders and includes `desayunos`. This app
does not change source recipes or enforce ingredient measurement conventions.

## Startup refresh

1. Load cached recipes, then begin one refresh for the new browsing session.
2. Download one coherent snapshot of `miguellobato84/recipes`, branch `main`,
   using HTTPS without login or a device-side Git client.
3. Stage and validate the complete recipe set before replacing the active cache.
4. On success, replace the entire cache, including removing deleted recipes.
5. On download, parsing, validation, or storage failure, show an error in Spanish
   and preserve the previous active cache. On a first-refresh failure, remain empty.

Browsing must not read a partially downloaded or partially replaced snapshot.
There is no refresh button, periodic background refresh, or repository setting.
The tree pull gesture starts one refresh when no refresh is already
running. On failure, Spanish status explains that the cached recipes are kept
and the user can pull down to retry. No last-refresh timestamp is required.

## APK delivery

Pushes to `main` trigger a GitHub Actions workflow that creates the next integer
version tag (`v1`, `v2`, ...), builds and publishes a signed release APK to GitHub
Releases. Installation is manual; recipe changes
are fetched on startup without requiring a new APK.

Use a dedicated release signing key across updates, retain a local backup, and
store CI signing credentials in GitHub Actions secrets. Never commit credentials
or the private key. See [scaffolding](../../scaffolding.md) for release automation
and its delivery status.

## Acceptance criteria

- Fresh install automatically attempts to download recipes without any button.
- Cache load precedes the one startup refresh; cached recipes remain visible
  during a pending refresh and after an offline/error result.
- Rotation/recomposition/background resume of the existing browsing session
  does not trigger another attempt. A new session retries once.
- Pulling down at the top of the recipe tree starts a refresh; it is unavailable
  on recipe details and the shopping list. Repeated gestures during a refresh
  do not start concurrent refreshes.
- Android Back returns from details to the tree at its previous expansion and
  scroll position; Back from the tree leaves the app without collapsing folders.
- A new browsing session starts fully collapsed; rotation and destination changes
  preserve expansion. Multiple branches can open independently. Closing a branch
  resets all descendants; reopening does not restore nested expansion.
- Refresh preserves surviving expanded folders, starts new folders collapsed, and
  removes deleted paths. A deleted selected recipe returns to the tree.
- A successful startup refresh enables category browsing and recipe details.
- Folder hierarchy includes nested categories and `desayunos`; ordering follows
  the navigation rules.
- Every folder, including arbitrary nested/new categories, has an automatic
  icon/color badge matching the rules above. Sibling combinations are distinct.
- Category identities remain deterministic across restarts, input ordering,
  expansion, and recipe-only refreshes; unknown categories remain usable offline.
- Both light/dark icon pairs meet 3:1 contrast. Names retain theme text colors;
  badges do not add duplicate accessibility announcements or alter row actions.
- Metadata and Markdown content remain readable without exposing YAML.
- Airplane mode permits browsing after a successful refresh and app restart.
- Network failure and an invalid recipe each preserve the complete old cache
  and show an error; with no old cache, the app stays empty.
- Successful refresh reflects additions, changes, and deletions from the source.
- Interrupted refresh never exposes a partial active cache.
- An APK update signed with the same identity retains downloaded recipes.
- A push to `main` produces an integer version tag and an installable signed APK
  in GitHub Releases.

## Validation plan

Test cache-first startup, exactly one refresh per new session, pending-refresh
browsing, startup success/failure/cancellation, parsing against source fixtures,
folder ordering, full cache replacement, and failure recovery. Exercise first launch, offline restart, refresh, navigation,
and APK updates on an emulator and, where available, the owner's Pixel 10.
Measure release APK size, startup, and browsing responsiveness before claiming
performance results. Documentation setup alone does not validate app behavior.

## Resolved implementation decisions (2026-10-31)

- Source: `https://codeload.github.com/miguellobato84/recipes/zip/refs/heads/main`.
  A single complete ZIP represents one branch snapshot; no per-file branch reads.
  HTTPS only, no credentials. Connect/read timeouts: 15/30 seconds; total transfer
  elapsed limit: 90 seconds, checked between reads (each read also has a
  30-second timeout). Maximum ZIP 32 MiB, 5,000 entries, declared uncompressed
  total 64 MiB, recipe 256 KiB, and total recipe text 8 MiB. Exceeding any limit
  fails the refresh. No archive extraction to source-controlled or arbitrary paths.
- Read the central directory, reject unsafe/duplicate paths, verify recipe sizes
  and CRCs, decode strict UTF-8, then validate every candidate. Ignore root docs,
  hidden folders, nested README/AGENTS docs, and non-Markdown assets. Every other
  `.md` under a visible folder is a recipe candidate; malformed frontmatter must
  fail rather than silently disappear. Categories follow actual folder names,
  including future categories; `categoria` matches the first path component.
- SnakeYAML 2.7 safe construction rejects duplicate keys, custom objects, excessive
  nesting, and collection aliases. Require schema integer 1, nonempty title and
  category, a list of nonempty tag strings, and scalar nonempty servings. Preserve
  the scalar spelling of servings, including numeric values. Unknown keys and
  unsupported schema versions fail. CommonMark AST validation requires one H1
  equal to the title and the four required H2 sections in order, outside code. Reject Markdown trees deeper than 64 nodes before rendering.
- Markwon 4.6.2 core renders CommonMark text, lists, emphasis, code and links.
  Images show their alt text only; no embedded asset loading. Raw HTML is not
  executed. HTTP(S) links open outside the app. Relative links resolve against
  the recipe's GitHub `main` blob URL and open externally. Other schemes show a
  Spanish error. Link opening is a separate explicit action, never a refresh.
- Empty valid source replaces the cache with an empty collection. Empty UI says
  there are no recipes. Invalid local cache shows a Spanish error while startup
  refresh attempts recovery; success can restore a valid cache in that session.
- Keep the downloaded ZIP in private app files, sync its staged bytes, then use
  an atomic same-directory rename to replace the active ZIP. Never fall back to
  non-atomic replacement. Publish new in-memory recipes only after that commit.
  Orphan staging files are discarded on next load; a killed process sees the old
  or the new complete ZIP. APK updates retain private files.
- Lifecycle ViewModel 2.9.4 owns work across rotation. Load/cache work runs on IO;
  Markdown spans prepare off the UI thread. Its initialization runs cache load
  then one refresh; the tree pull gesture can request another refresh. Existing ViewModel
  retention prevents rotation/recomposition from starting duplicate work.
  Concurrent refresh calls across sessions serialize through a per-cache-directory
  lock. Loading the active cache does not wait for a previous session's download
  or delete its staging file. Orphan staging files are cleaned only when no
  refresh owns that directory. During refresh,
  cached recipes remain browsable. Progress and persistent Spanish error/success
  status appear above content. After success, keep the selected detail if
  its recipe still exists; return to the tree otherwise. Retain surviving expanded
  folder paths and drop deleted paths.
- Spanish locale collation at primary strength (case/accent insensitive), with
  path as deterministic tie-breaker; folders precede recipe titles. System back
  returns from details to the tree, and leaves the app from the tree. Keep tree
  expansion, scroll, and detail selection across rotation within the same session;
  saved state from an earlier session must not reopen branches or details. Honor system font scaling, insets and light/dark
  theme. Preserve existing launcher artwork and release/signing setup.

## Original manual-refresh release validation (2026-10-31)

- `VERIFY_LIVE_RECIPES=1 ./gradlew --no-daemon testDebugUnitTest lintRelease
  assembleRelease` passed with JDK 17.0.19 and SDK/build tools 36/35.0.0.
  Fourteen offline acceptance tests and one live-source smoke test passed.
- The fixture is pinned to source commit `494130cd4dc66f0ef3095040830426e2a839d898`.
  All 27 recipes validate, including `desayunos`, nested categories, decimal
  servings `2.5`, text servings, tags, and preserved Markdown/link source.
- Tests verify no download during empty load or offline restart; Spanish
  collation and folder-first ordering; parent fallback after deletion; complete
  addition/change/deletion and empty-source replacement; and byte-for-byte old
  cache preservation on network, malformed recipe, and simulated storage errors.
- Interrupted-staging recovery and reading the old complete ZIP while a new one
  downloads passed. Tests reject bad schema, category/title/section mismatch,
  duplicate YAML keys, unsafe YAML construction/aliases, excessive Markdown
  nesting, malformed UTF-8, bad CRC, duplicate/unsafe ZIP paths, oversized
  recipes, excessive entry counts, and a truncated archive. Link tests check
  HTTP(S), relative links, and rejected schemes.
- Live smoke check used the actual app downloader without credentials against
  codeload `main`; downloaded, validated, atomically cached, and reloaded all 27
  recipes. This is a host JVM check, not an Android network-stack device test.
- Lint passed with zero errors and seven warnings: available newer Gradle,
  Compose, Activity and Lifecycle versions; existing backup configuration and
  icon qualifier; and a URI KTX style suggestion. Existing scaffold versions
  remain pinned for compatibility. No R8 errors. Optimized unsigned development
  APK: 1,793,579 bytes. APK inspection verified package, Android 16 minimum/target,
  launch activity, Internet permission, and absence of bundled recipe fixtures.
- All five existing release-version allocation tests passed. Workflow, icon,
  signing setup and source recipe checkout are unchanged. Latest published
  release verified before implementation: `v5` at app commit `08baf564004f7740b60e5d05dbf22d6233c77e12`.
  During initial implementation, no commit, push, signing-key access, workflow
  trigger, or release publication was performed for F1. The existing release workflow does not yet run F1's
  JVM acceptance suite; the command above was run locally.

### Checks still required on Android

`adb devices` showed no connected devices, and no emulator was configured.
Therefore app launch/rendering, touch/back navigation, rotation/process restore,
TalkBack/font scaling/light-dark appearance, link intents, airplane-mode restart,
Android-filesystem atomic rename behavior, and same-signature APK upgrade cache
retention have not been exercised on Android. Startup and browsing latency on
Pixel 10 are unmeasured. Signed F1 release validation is recorded below.
Host tests and successful builds do not establish those device outcomes.

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Not started | Scope recorded; no app implementation or release. |
| 2026-10-31 | In progress | Resolved implementation decisions from verified source facts before coding. |
| 2026-10-31 | Implemented locally, not released | Fourteen acceptance tests, live 27-recipe import, five release-version tests, lint and optimized build passed. Device checks and publication remain pending. |

### First published F1 release

- Implementation commit: [`77371217c20997025a56a312d0c29b93acd18070`](https://github.com/miguellobato84/recipes-android/commit/77371217c20997025a56a312d0c29b93acd18070).
- Tag and release: [`v6`](https://github.com/miguellobato84/recipes-android/releases/tag/v6)
  points to that exact commit. [Actions run 41206097280](https://github.com/miguellobato84/recipes-android/actions/runs/41206097280)
  completed successfully, including version tests, Android lint, optimized signed
  build, signature/package checks, artifact upload, and release publication.
- Downloaded public APK and checksum. SHA-256 matches the checksum asset;
  `apksigner verify --print-certs` passes. Certificate SHA-256 equals the verified
  `v5` APK certificate. Package is `com.miguellobato84.recetas`, versionCode and
  versionName are `6`, minimum SDK is `36`. Signed APK size: 1,801,775 bytes.
- Release verification does not establish the outstanding device checks above.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Released | F1 commit `7737121`, signed release `v6`, successful Actions run `41206097280`; public APK checksum, signature identity, package and version verified. |

## Startup refresh revision (2026-10-31)

Decision: Accepted. Delivery: Released in v8.

Owner requested removing **Actualizar** and refreshing recipes on startup.
This revises F1 in place; APK self-updates remain a separate F2 proposal.
The original manual-refresh evidence above remains historical and does not
establish the new startup behavior. New checks and device gaps are recorded below.

Historical cross-reference conflict (resolved): the earlier F2 draft described an
explicit **Actualizar** action. This F1 revision superseded those references.
The current F2 proposal now matches startup recipe refresh, and F2 runtime
implementation is present in the app; it remains separate from recipe refresh.

### Startup revision validation

- `VERIFY_LIVE_RECIPES=1 ./gradlew --no-daemon testDebugUnitTest lintRelease
  assembleRelease` passed: 20 offline tests plus the live 27-recipe import.
- Six startup-flow tests verify cache visibility before a suspended download
  completes, one attempt per fresh session, empty-install automatic download,
  offline/invalid-snapshot cache retention, unreadable-cache recovery, and
  cancellation propagation without false error/retry states.
- The repository concurrency test now reopens through a second repository while
  a download is paused. It verifies immediate old-cache access and retention of
  the live staging file, followed by a complete replacement after commit.
- Source inspection confirms no refresh button/action or obsolete button-guidance
  text in the UI/ViewModel. Lint and the R8 optimized build passed. Unsigned
  development APK: 1,793,579 bytes. Existing launcher, workflow and signing
  setup are unchanged. F2 draft files are excluded from this revision.
- No Android device/emulator is connected (`adb devices` is empty). Actual startup
  UI, rotation/background lifecycle behavior, Android networking and device
  close/reopen behavior remain untested; host flow/concurrency tests do not
  establish those Android outcomes.

### Published startup refresh revision

- Implementation commit: [`b18785a5214b6a7cbcaf7cd7ca1a723d1c7c8dc9`](https://github.com/miguellobato84/recipes-android/commit/b18785a5214b6a7cbcaf7cd7ca1a723d1c7c8dc9).
- [`v8`](https://github.com/miguellobato84/recipes-android/releases/tag/v8) points
  to that exact commit. [Actions run 41207316516](https://github.com/miguellobato84/recipes-android/actions/runs/41207316516)
  passed release-version tests, lint, the optimized signed build, APK verification,
  artifact upload and release publication.
- Downloaded public APK SHA-256 matches its checksum. Signature verification
  passed; certificate SHA-256 equals the prior published signing identity.
  Package is `com.miguellobato84.recetas`; versionCode/versionName are `8`,
  minimum SDK is `36`, and signed APK size is 1,801,779 bytes.
- The published APK contains the new startup/error text and no obsolete refresh
  button guidance. Android lifecycle, visual and device checks remain pending.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Released | Startup refresh without a manual button, commit `b18785a`, signed release `v8`, successful Actions run `41207316516`; 21 host tests and public APK verification passed. |

### Pull-to-refresh revision (2026-10-31)

Owner requested removal of the in-app **Atrás** button and pull-to-refresh on
folder lists only. Android system back handling remains in place. Pull-to-refresh
invokes the existing repository snapshot refresh, keeps cached recipes on
failure, and cannot start a second refresh while one is active.

Implementation is local and not released. `git diff --check` passed. The debug
build could not run because no Android SDK location is configured in this
workspace; unit, lint, and device validation remain outstanding.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | In progress | Pull-to-refresh and navigation changes implemented locally; SDK unavailable for build verification. |

### Tree navigation revision (2026-10-31)

Owner confirmed the revised Navigation section and acceptance criteria after
grilling. F1 replaces folder drill-down with the tree; F3 retains destination
state. Implemented locally; not published. Validation:

- `./gradlew --no-daemon testDebugUnitTest lintRelease assembleDebug assembleRelease`
  passed using JDK 17 and the existing Android SDK platform 36. All 47 offline
  tests passed; the opt-in live-source test was skipped. No network importer
  behavior changed, so the pinned 27-recipe fixture supplies navigation evidence.
- Five new tree tests verify collapsed defaults and empty collections; independent
  inline expansion with Spanish folder-first ordering and path tie-breaking;
  descendant reset without collapsing sibling/prefix-matching folders; refresh
  retention/removal with new folders collapsed; and exactly one correctly titled
  and indented leaf for every recipe in the pinned fixture.
- Source review confirms tree scroll state stays allocated while details open,
  the recipe destination saves expansion/selection/scroll while shopping is shown,
  and its saved-state key includes the browser ViewModel session ID. Rotation
  reuses that ID; a new ViewModel cannot restore a previous session's tree.
- `git diff --check` passed. Release lint reported zero errors and eight warnings
  (dependency updates, backup/icon configuration, and existing URI style guidance).
- `adb devices` reported no connected devices. Actual taps, Back, scroll return,
  rotation/process recreation, TalkBack, font scaling and appearance have not
  been exercised on Android. Host tests/builds and source review do not establish
  those device outcomes. No release publication was requested.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Implemented locally, not released | 47 offline tests, debug/release builds and release lint passed. Tree/session navigation source reviewed; Android interaction/restoration/accessibility checks remain pending. |

### Category badge revision (2026-10-31)

Implemented the owner-approved semantic mappings as twelve local vector icons
(eleven categories plus neutral folder), with automatic path-based assignment,
explicit light/dark palettes, and deterministic sibling collision resolution.
Category matching uses the final path component with case/accent normalization.
Folder text, full-row actions, expansion state, and arrows retain their existing
behavior. Decorative icons have no separate accessibility description.

Validation:

- `ANDROID_HOME="$HOME/.cache/recipes-android-sdk" ./gradlew --no-daemon
  testDebugUnitTest lintRelease assembleDebug assembleRelease` passed with
  JDK 17.0.19, platform 36, and build tools 35.0.0. The shell initially had no
  configured SDK; the required packages were downloaded from Google's Android
  repository, checked against its published archive checksums, and installed
  outside the checkout. No project build settings or dependencies changed.
- 54 offline JVM tests passed; one opt-in live-source test was skipped. Five new
  tests cover semantic/nested/case/accent mappings, order and recipe-only refresh
  stability, hash and normalized-name collisions, more than 5,000 unknown sibling
  categories, light/dark icon contrast, and badges for every folder in the pinned
  27-recipe fixture. Contrast tests check 3:1 at worst-case per-channel shade
  endpoints, covering all possible generated collision shades.
- Release lint passed with zero errors and eight existing warnings (dependency
  versions, backup configuration, icon SDK qualifier, and URI style). Both APK
  builds passed; the unsigned release APK is 2,339,535 bytes.
- Rendered and visually inspected a two-theme resource preview generated from
  the actual vector paths and palette constants, available locally in
  `app/build/reports/folder-badges.png`. This is a resource preview, not an Android
  screenshot. `git diff --check` passed.
- `adb devices` lists no connected devices. Android rendering, touch/expansion,
  font scaling, and TalkBack behavior remain untested on-device. No release was
  published. Host tests and the resource preview do not establish device results.

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Implemented locally, not released | 54 offline tests, debug/release builds, lint, generated-shade contrast checks, and two-theme vector preview passed; Android-device validation pending. |
