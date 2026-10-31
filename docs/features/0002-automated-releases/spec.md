# 0002 Hello world and automated APK releases specification

Decision: Accepted

Delivery: In progress

## Scaffold

Application ID: `com.miguellobato84.recetas`. Minimum, compile, and target SDK:
API 36 (Android 16). Use Kotlin, Jetpack Compose, Java 17, and the Gradle wrapper.
Show **Hola mundo** centered in a simple screen with app name **Recetas**.
Respect system insets, system font sizing, and light/dark mode. There is no
network access, recipe content, or recipe UI in this scaffold.

## Trigger and version allocation

Only `push` events on `main` start the release workflow. No path filters: docs-only
pushes also release. Tags created by the workflow do not start another release.

Checkout the triggering commit and fetch all tags. Accept positive integer tags
matching `v[1-9][0-9]*`; ignore other tag formats. If that commit already has a
valid integer version tag, reuse it. Otherwise allocate one greater than the
highest matching tag, starting at `v1`, and push that tag at the triggering commit.
The suffix X maps to Android versionCode X and versionName X. Reject X greater
than Android's versionCode limit of 2100000000.

Use a single concurrency group with cancellation disabled and maximum queuing.
GitHub's queue limit still applies; version numbers follow allocation order.
Reruns reuse their existing version rather than consuming another number.
Never move or delete an existing tag. A build failure can leave a reserved tag;
rerun the failed workflow to finish that version.

## Build and publication

1. Run version-allocation tests and Android lint.
2. Decode the dedicated signing keystore into runner temporary storage.
3. Build an R8-optimized signed release APK with the allocated Android version.
4. Verify the APK signature and generate its SHA-256 checksum.
5. Upload `recetas-vX.apk` and `recetas-vX.apk.sha256` as a workflow artifact.
6. Publish a GitHub Release for `vX` with both files attached. Mark the numerically
   highest version as latest; retries of older versions must not regress latest.
7. Remove the temporary keystore even if a previous step failed.

On retries, reuse an existing release and replace its same-named assets. Publish
only after build and signature verification succeed. Upload failures fail the run.
The workflow token needs repository contents write permission for tags/releases.
Pin third-party Actions to verified commit hashes. Use the Ubuntu 24.04 runner's
preinstalled Android command-line tools; install platform 36 and build tools
35.0.0 without requesting the obsolete SDK `tools` package.

## Signing and recovery

Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. No credentials or private keys
belong in Git, workflow logs, artifacts, or releases.

Retain the dedicated key and credentials outside the checkout under the owner's
local `~/.local/share/recipes-android/signing/`, with directory mode 700 and files
mode 600. Back up that folder securely to recover the signing identity. Reuse the
same key for subsequent APKs. A lost signing identity prevents ordinary in-place
updates for existing installs.

## Acceptance criteria and validation

- The project builds an optimized release APK and passes Android lint.
- Version tests cover first release, numeric ordering, unrelated tags, retries,
  and versionCode overflow.
- A push to `main` creates `vX` on the exact triggering commit.
- The workflow succeeds with a downloadable artifact and GitHub Release assets.
- The release APK's package, minimum SDK, version, and signature are verified.
- Launching the APK displays **Hola mundo** on Android 16+.
- The next distinct main commit uses X+1; retries keep X.

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | In progress | Documentation created before scaffold and workflow implementation. |
| 2026-10-31 | In progress | Initial `v1` run reserved its tag, then failed because an SDK setup action requested the obsolete `tools` package. Switched to the runner's preinstalled SDK and current pinned Actions. |
