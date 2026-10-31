# 0002 Automatic app updates on startup specification

Decision: Accepted

Delivery: Released in v11 (device validation pending)

Related: [proposal](proposal.md).

## Requested behavior

On app startup, check for a newer release of `miguellobato84/recipes-android`.
If one exists, download and install it automatically without asking the user.
Do not require tapping an update button or approving an app-owned dialog.

The app never opens an installation confirmation or settings screen. Android may
still require user action; in that case the update stays pending and the installed
version remains usable. Unattended installation on the owner's Pixel 10 remains
unverified.

## Startup and release selection

- Run one asynchronous check per application process startup, not on every
  recomposition, activity recreation, or return from the background.
- Display the existing app and cached recipes while checking. No periodic polling
  or background update schedule is part of F2.
- Use the fixed public app repository without embedding GitHub credentials.
- Select the highest published stable integer release `vX` with a matching
  `recetas-vX.apk` and `recetas-vX.apk.sha256`. Exclude drafts, prereleases, malformed
  version tags, and incomplete releases. Reserved tags without releases do not count.
- Compare X numerically with the installed Android versionCode. Equal or lower
  versions do nothing; `v10` is newer than `v9`.
- Use HTTPS, bounded requests/downloads, and one update attempt at a time.

Implementation limits: request at most ten pages of 100 GitHub releases, with a
2 MiB maximum response per page and a 90-second total release-list deadline.
Use 15-second connect and 30-second read timeouts. Limit APK downloads to 100 MiB,
checksum assets to 256 bytes, and each asset transfer to 90 seconds. Reject an
oversized or incomplete response; do not select a result from a truncated release
list.

## Download and validation

Download the APK and its checksum into private temporary storage. Before starting
installation, require all of the following:

- The download is complete and its SHA-256 matches the checksum asset.
- The APK has application ID `com.miguellobato84.recetas`.
- Its versionCode matches release X and exceeds the installed versionCode.
- Its SDK requirements are compatible with the device.
- Its signing identity matches the trusted installed app. The release checksum
  alone does not establish publisher identity.

Use `PackageManager.getPackageArchiveInfo()` to inspect the staged APK. Compare
the incoming signing certificate history against the currently installed signer;
for multiple signers require an exact signer-set match.
[Android SigningInfo reference](https://developer.android.com/reference/android/content/pm/SigningInfo).

Reject an invalid or incompatible update, discard its staged files, and retain
the installed app and recipe cache. Never download private signing keys.

## Android installation contract

Use a `PackageInstaller` self-update session. Android documents unattended updates
with `USER_ACTION_NOT_REQUIRED`, the declared `UPDATE_PACKAGES_WITHOUT_USER_ACTION`
permission, granted install permission, eligible target SDK, and a qualifying
installer relationship, including self-update. Android 16 requires the incoming
app to target at least API 34 for this path. The API still requires handling
`STATUS_PENDING_USER_ACTION`; unconditional silent installation is not guaranteed.
[Android PackageInstaller reference](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).

The no-user-action permission is a normal permission.
[Android permission reference](https://developer.android.com/reference/android/Manifest.permission#UPDATE_PACKAGES_WITHOUT_USER_ACTION).

Declare `REQUEST_INSTALL_PACKAGES` and check
`PackageManager.canRequestPackageInstalls()` before creating an installer session.
The user must enable “install unknown apps” for Recetas in Android Settings before
unattended installation can work. The app never requests this through a prompt,
opens settings, or launches an installer confirmation. If permission is missing,
it leaves the newer release pending and shows a nonmodal Spanish status.
[PackageManager reference](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

Accepted no-prompt fallback:

1. If install permission is unavailable, skip installation and record a pending update.
2. If Android returns pending user action, leave the update pending and release
   installer resources without launching the supplied confirmation intent.
3. Show only a nonmodal Spanish status, such as **Actualización pendiente: permite
   instalar aplicaciones desde Recetas en Ajustes** or **Actualización pendiente:
   Android requiere autorización**. Do not open dialogs or settings.
4. Never bypass Android policy or repeatedly retry during the same startup.

Ordinary execution must not depend on root, device-owner privileges, accessibility
automation, or a companion installer.

## Success, interruption, and failure

- Let Android replace the installed APK while preserving app data and recipe cache.
  Verify the installed version on the next launch before recording completion.
- Do not promise uninterrupted browsing or automatic reopening during replacement.
- Offline access, timeouts, rate limits, incomplete downloads, signature mismatch,
  insufficient storage, and installation rejection leave the existing app usable.
- Keep network and disk work off the UI thread. Clean up staged downloads and
  abandoned sessions; prevent duplicate attempts across activity recreation.
- Keep recipe refresh behavior unchanged: startup app-update checks never fetch
  or replace recipes.

## Acceptance criteria

- Each process startup initiates at most one check without blocking cached browsing.
- Equal, lower, malformed, draft, prerelease, and incomplete versions do not install.
- A newer valid signed APK is downloaded and submitted without an app prompt.
- On an eligible Android 16 device with required permission, installation completes
  without interaction; the next launch reports the new installed version.
- Missing permission and pending-user-action results follow the agreed fallback
  without launching a confirmation or settings screen.
- Invalid signatures/checksums, offline operation, and rejected installs preserve
  the installed app and cached recipes.
- F1's accepted startup recipe refresh remains unchanged; the app-update check
  never calls the recipe repository or changes its cache.

## Validation plan and resolved decisions

Test numeric release selection, partial releases, integrity checks, duplicate-startup
prevention, failure cleanup, and installer callbacks. Test end to end with two
consecutive APKs signed by the same release identity on Android 16 and the owner's
Pixel 10, covering permission denied, permission granted, and required user action.

Resolved implementation choices:

- Pending permission and pending-user-action outcomes are nonmodal statuses. The
  owner must enable the install-source setting manually in Android Settings; the
  app does not navigate there.
- Release requests use HTTPS, the limits listed above, and only complete stable
  integer releases with both named assets qualify.
- Debug installs and mismatched signer identities are rejected. No automatic
  reopen after installation is required; the next launch verifies the installed
  version normally.

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | In progress | Android PackageInstaller, permission, and pending-user-action contracts verified against current Android API references. No-prompt pending fallback accepted; Pixel 10 behavior remains untested. |
| 2026-10-31 | Implemented locally | Release parsing, numeric selection, SHA-256 parsing, APK identity/version/SDK/signing checks, PackageInstaller submission, and nonmodal pending statuses added. Four release-catalog JUnit tests pass in a direct Kotlin/JUnit run. Local Gradle tests, lint, and release build were blocked because Android SDK Platform 36 and Build Tools 35 required license acceptance. Device checks were unavailable because `adb` was not installed. |
| 2026-10-31 | v10 reserved, not released | Workflow run [41211109315](https://github.com/miguellobato84/recipes-android/actions/runs/41211109315) created tag `v10`, then failed Kotlin compilation because `ApplicationInfo.maxSdkVersion` is unavailable. The tag remains on its original commit; no v10 release or APK was published. |
| 2026-10-31 | Released in v11 | Commit [`3365b5f`](https://github.com/miguellobato84/recipes-android/commit/3365b5f562a9c25d40bfe9fc8f13c16e66473a6c), tag [v11](https://github.com/miguellobato84/recipes-android/releases/tag/v11), and successful [workflow run 41211316266](https://github.com/miguellobato84/recipes-android/actions/runs/41211316266). CI version tests, lint, signed release build, APK package/version/minimum-SDK checks, signature verification, artifact upload, and release publication passed. Published APK SHA-256 matches its checksum asset; signer SHA-256 `c6cfcbe03fef93ffa42c759058a804ef8009ff68e9afbaf9e39f06ca3c0d1ef9` matches v9. Device/Pixel 10 unattended-install checks remain untested. |

### Subsequent local build verification (2026-10-31)

The SDK blocker in the earlier implementation record was resolved by using the
existing SDK from the F1 workspace (Platform 36 / Build Tools 35.0.0). At fetched
`origin/main` base `3365b5f`, with F3 additions, all four release-catalog tests,
the full offline JVM suite, `lintRelease`, `assembleDebug`, and optimized
`assembleRelease` passed. No updater or signing code was changed by F3. The
available `adb` reports no connected devices and no emulator is configured;
PackageInstaller callbacks, permission handling, unattended installation, signer
validation on-device, and same-signature upgrade persistence still require Android
validation. Earlier SDK/adb limitations above describe that earlier run only.
