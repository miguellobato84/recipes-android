# 0001 Read-only recipe browser specification

Decision: Accepted

Delivery: Released (first release: v6)

Related: [proposal](proposal.md)

## Platform and identity

- App name: **Recetas**; interface language: Spanish.
- Minimum Android version: Android 16; intended device: Pixel 10.
- App repository: `miguellobato84/recipes-android`, public.
- Kotlin and Jetpack Compose; persistent local file cache.

## First launch and cached browsing

Start without bundled recipes. Show an empty state with **Actualizar**.
Opening or restarting the app does not download recipes.

After a successful refresh, browse only the persistent cache. Cached content
survives app restarts and normal APK updates and remains readable offline.
Uninstalling the app or clearing its data is outside that persistence guarantee.

## Navigation

Reflect recipe folders, including `desayunos` and nested folders such as
`principales/carne`. List folders before recipes, with each group alphabetically
ordered. Use recipe titles as recipe labels. Standard back navigation returns
to the containing folder or previous screen.

There is no search, category filter, or recipe editor.

## Recipe details and source format

Preserve the recipe's text and formatted Markdown, including headings, ingredient
and utensil bullets, numbered steps, notes, links, and inline code. Show servings
and tags as metadata; hide raw YAML. Open external links outside the app.

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

## Manual refresh

1. The user taps **Actualizar**.
2. Download one coherent snapshot of `miguellobato84/recipes`, branch `main`,
   using HTTPS without login or a device-side Git client.
3. Stage and validate the complete recipe set before replacing the active cache.
4. On success, replace the entire cache, including removing deleted recipes.
5. On download, parsing, validation, or storage failure, show an error in Spanish
   and preserve the previous active cache. On a first-refresh failure, remain empty.

Browsing must not read a partially downloaded or partially replaced snapshot.
There are no background or launch-time refreshes and no repository settings.
No last-refresh timestamp is required by the accepted scope.

## APK delivery

Pushes to `main` trigger a GitHub Actions workflow that creates the next integer
version tag (`v1`, `v2`, ...), builds and publishes a signed release APK to GitHub
Releases. Installation is manual; recipe changes
require only **Actualizar**, not a new APK.

Use a dedicated release signing key across updates, retain a local backup, and
store CI signing credentials in GitHub Actions secrets. Never commit credentials
or the private key. See [scaffolding](../../scaffolding.md) for release automation
and its delivery status.

## Acceptance criteria

- Fresh install opens empty without a recipe network request.
- A successful manual refresh enables category browsing and recipe details.
- Folder hierarchy includes nested categories and `desayunos`; ordering follows
  the navigation rules.
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

Test parsing against source fixtures, folder ordering, full cache replacement,
and failure recovery. Exercise first launch, offline restart, refresh, navigation,
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
  there are no recipes and offers **Actualizar**. Invalid local cache shows a
  Spanish error and remains empty until a successful explicit refresh.
- Keep the downloaded ZIP in private app files, sync its staged bytes, then use
  an atomic same-directory rename to replace the active ZIP. Never fall back to
  non-atomic replacement. Publish new in-memory recipes only after that commit.
  Orphan staging files are discarded on next load; a killed process sees the old
  or the new complete ZIP. APK updates retain private files.
- Lifecycle ViewModel 2.9.4 owns work across rotation. Load/cache work runs on IO;
  Markdown spans prepare off the UI thread. Refresh is disabled while loading or
  refreshing; concurrent repository refresh calls serialize. During refresh,
  cached recipes remain browsable. Progress and persistent Spanish error/success
  status appear above content. After success, keep the current folder/detail if
  it still exists; return to its nearest surviving parent otherwise.
- Spanish locale collation at primary strength (case/accent insensitive), with
  path as deterministic tie-breaker; folders precede recipe titles. System back
  and **Atrás** go to the parent. Keep folder/detail position across rotation and
  saved instance restoration. Honor system font scaling, insets and light/dark
  theme. Preserve existing launcher artwork and release/signing setup.

## Validation evidence (2026-10-31)

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
