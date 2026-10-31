# Recetas

Personal, read-only Android app for browsing recipes from
[miguellobato84/recipes](https://github.com/miguellobato84/recipes).

## Behavior

- Spanish interface, designed for Pixel 10 and Android 16+.
- Empty on first launch. Tap **Actualizar** to download recipes from `main`.
- Browse cached recipes by the repository's folder hierarchy, alphabetically.
- Read formatted recipes with servings, tags, and external links.
- Refresh manually. Failed refreshes show an error and preserve the cache.
- Install signed APKs from GitHub Releases.

No search, filters, editing, login, or background refresh.

## Project status

Feature 0001 is released with Kotlin and Jetpack Compose: a Spanish
recipe browser with manual snapshot download, validated atomic cache replacement,
and offline reading. First F1 release: [v6](https://github.com/miguellobato84/recipes-android/releases/tag/v6).
Device rendering, installation,
APK upgrade persistence, and Pixel 10 responsiveness still require device checks.

## Build

Install JDK 17 and Android SDK platform 36, then run:

```sh
./gradlew assembleDebug
```

Run offline acceptance tests and lint with:

```sh
./gradlew testDebugUnitTest lintRelease
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

For an explicit live source smoke check, run
`VERIFY_LIVE_RECIPES=1 ./gradlew testDebugUnitTest --rerun-tasks`.
The live test downloads the public recipe snapshot; normal tests use test-only
fixtures and do not need network access after Gradle dependencies are installed.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Release builds use R8 code optimization and resource shrinking.

## Releases

Each push to `main` creates the next integer tag (`v1`, `v2`, ...), builds a signed
APK, uploads a workflow artifact, and publishes a
[GitHub Release](https://github.com/miguellobato84/recipes-android/releases).
Download `recetas-vX.apk` from its release and install it manually on Android 16+.
Android versionCode and versionName both use X. Retrying a workflow reuses its tag.

CI requires `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` repository secrets. Preserve the
signing identity for updates; private keys and credentials must stay outside Git.
See [scaffolding and release setup](docs/scaffolding.md).

## Feature history and specifications

See the [documentation index](docs/README.md) for feature proposals,
specifications, acceptance criteria, and delivery history.
