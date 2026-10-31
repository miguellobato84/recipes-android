# 0002 Hello world and automated APK releases proposal

Decision: Accepted

Delivery: Released

## Problem and outcome

Establish an installable Android scaffold and a working release pipeline before
implementing recipe browsing. Every push to `main` should produce a versioned APK
without manually creating a tag or release.

## Scope

- Minimal Kotlin and Compose app named Recetas showing **Hola mundo** on Android 16+.
- Pushes to `main` allocate sequential integer tags: `v1`, `v2`, and so on.
- Build and sign an optimized release APK, upload a workflow artifact, and publish
  the APK and its SHA-256 checksum in a GitHub Release for the tag.
- Keep the release signing identity stable across builds using Actions secrets.

Recipe import, cache, and browsing remain feature 0001 and are not implemented here.
“Image” in this request means the installable Android APK, not a container image.

## Decisions and tradeoffs

One integer is used for the tag suffix, Android versionCode, and versionName.
Workflow retries reuse the tag already assigned to the same commit. Failed builds
may leave a reserved tag without a release; retries complete that version.
Serialize release runs to avoid allocating the same version twice. Queue runs
rather than canceling an earlier build when a new push arrives.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Accept main-triggered integer releases | Explicit owner request; replaces the previous tag-triggered plan. |

See [the specification](spec.md) for acceptance criteria and delivery evidence.
