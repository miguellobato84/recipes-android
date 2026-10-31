# 0002 Automatic app updates on startup proposal

Decision: Accepted

Delivery: In progress

## Problem and intended outcome

The owner currently installs release APKs manually. On startup, Recetas should
check for a newer app version, download it, and install it automatically without
asking the user. App updates are separate from recipe refreshes.

## Requested scope

- Check the app repository's published GitHub Releases when the app starts.
- Compare integer app versions numerically and update only to a higher version.
- Download, validate, and install the release APK without an app confirmation.
- Preserve recipe cache and app data across updates.
- Keep cached browsing available when checking, downloading, or updating fails.
- Leave recipe refresh behavior to F1. F1's current accepted behavior refreshes
  recipes on startup; F2 never calls or changes that flow.

## Platform constraint and proposed handling

Android provides a conditional unattended self-update path, but install permission
and platform policy still apply. The full requirement of installation without any
user interaction is not yet validated on the owner's Pixel 10. See the sourced
platform details in [the specification](spec.md#android-installation-contract).

Resolved handling: if install permission is unavailable or Android requires user
action, leave the update pending, show a nonmodal Spanish status, and continue
with the installed version. Never open settings or an installation confirmation
automatically. Before unattended installation can work, the user must enable
“install unknown apps” for Recetas in Android Settings. The app will not launch
that screen or ask for permission. This honors the no-prompt requirement but
cannot guarantee every update installs.

## Alternatives and tradeoffs

- Manual APK installation remains a recovery option, but does not meet the goal.
- An Android installation prompt would improve update completion but conflicts
  with the requested unattended flow.
- Rooting, managed-device provisioning, Play Store distribution, and external
  updater apps are outside this proposal.
- Automatic startup checks introduce app-update network access; recipe data
  remains strictly controlled by the manual refresh button.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Record F2 request | Owner requested startup checks and automatic installation without asking. |
| 2026-10-31 | Accept conditional unattended updates | Owner reaffirmed no prompts. Android may require install-source permission or user action; preserve the installed app and report a pending update without opening system UI. |
