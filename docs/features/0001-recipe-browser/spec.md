# 0001 Read-only recipe browser specification

Decision: Accepted  
Delivery: Not started  
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
or the private key. See [feature 0002](../0002-automated-releases/spec.md) for
release automation and its delivery status.

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

## Open implementation questions

- Application ID and exact dependency/toolchain versions.
- Snapshot endpoint, limits, timeout, concurrent refresh handling, and durable
  cache replacement mechanism.
- Handling an empty source, unknown future schema versions, and unsupported
  relative links or embedded assets.
- Precise alphabetical collation, theme, launcher icon, and refresh progress UI.
- Signing backup recovery procedure; release details belong to feature 0002.

Resolve these before dependent implementation; they are not accepted defaults.

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | Not started | Scope recorded; no app implementation or release. |
