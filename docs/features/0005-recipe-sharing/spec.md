# 0005 Share recipe link specification

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

Related: [proposal](proposal.md), [recipe browser](../0001-recipe-browser/spec.md),
[shopping list](../0003-shopping-list/spec.md).

## Observable behavior and data contract

Recipe details use the shared screen header, with the recipe title on the left
and side-by-side icon-only shopping-cart and Share buttons on the right.
Expose labels “Añadir a la lista de la compra” and “Compartir receta” to
accessibility and through long-press tooltips. Touch targets are at least 48 dp.
Retry retains its current presentation. The cart header follows F3.

Tapping Share launches Android's standard share sheet using `ACTION_SEND`, MIME
type `text/plain`, and `EXTRA_TEXT` containing only
`https://github.com/miguellobato84/recipes/blob/main/<recipe-path>`. Encode the
repository-relative path as a URI path, preserving nested folders, accents,
spaces, percent signs, hashes and question marks. Use the same source URL
construction for relative Markdown links. Do not add a subject, title, recipe
body, personal notes, attachment, or extra message.

Sharing does not fetch source data, modify local state or depend on shopping-list
loading/saving/failure. Cached details can share offline. Links follow `main`;
they do not pin the cache's version or guarantee that a deleted file still exists.

## Cancellation and errors

Dismissal returns to the unchanged recipe without success/cancellation feedback.
If Android cannot launch the share sheet, show a Spanish toast:
“No se puede compartir esta receta.” Stay on details without an automatic fallback.
The app does not claim delivery to the destination app.

## Acceptance criteria

1. Every recipe detail exposes the two icon-only actions on the right of its
   title header, with Spanish spoken labels, long-press tooltips and at least
   48 dp touch targets.
2. Share opens the native chooser, with exactly the encoded GitHub main file URL
   as its text payload and no other shared content.
3. Nested and accented paths, spaces and reserved URI characters are preserved.
4. Sharing is available offline and while shopping is loading or unavailable.
5. Dismissing the chooser preserves detail and notes, without confirmation.
6. Launch failure shows the specified toast and preserves detail.
7. Shopping additions retain existing repeat-addition, disabled and persistence
   behavior. Retry remains unchanged; the cart header follows F3.

## Validation plan

Run host URL-encoding and existing recipe/shopping regression tests, Android lint,
and debug/release builds. Review intent contents and UI state dependencies.
On Android, verify chooser payload in a receiving app; offline sharing;
cancellation; simulated unavailable chooser; repeated shopping additions; TalkBack,
tooltips and touch targets. Record unavailable checks without claiming they passed.

## Open questions

None. Owner confirmed shared understanding and authorized implementation.

## Local validation (2026-10-31)

- `./gradlew --no-daemon testDebugUnitTest lintRelease assembleDebug assembleRelease`
  passed with JDK 17 and the existing Android SDK platform 36 supplied through
  `ANDROID_HOME`. The first attempt stopped at configuration because this checkout
  had no SDK path; rerunning with the discovered SDK completed successfully.
- 49 offline tests passed; the opt-in live-source smoke test was skipped. Two
  new tests cover nested GitHub main file URLs and round-trip encoding of Unicode,
  spaces, percent signs, hashes and question marks. Existing recipe, shopping,
  notes, startup, tree and release-catalog regressions passed.
- Release lint: zero errors, eight existing dependency/configuration/style warnings.
  Debug and optimized release APK builds passed.
- Source inspection confirms only `EXTRA_TEXT` is set on the `text/plain`
  `ACTION_SEND` intent wrapped with `Intent.createChooser`; there is no fetch,
  share-result callback or state mutation. Share has no shopping-state dependency.
  The cart retains its prior enabled predicate and callback. Both use Material 3
  `IconButton` touch targets, vector icons, Spanish descriptions and `PlainTooltip`.
  Launch exceptions produce the specified toast. Retry remains a text button.
- API direction checked against Android's [sharing documentation](https://developer.android.com/develop/ui/compose/sharing/send)
  and [tooltip documentation](https://developer.android.com/develop/ui/compose/components/tooltip),
  and the tooltip API was verified in the pinned Material 3 1.4.0 artifact.
- `git diff --check` passed. No release was published.
- `adb devices` listed no connected device. Actual chooser payload reception,
  offline/cancellation/failure interactions, repeated cart taps, tooltip gestures,
  TalkBack, measured touch targets, font scaling and light/dark rendering remain
  unverified on Android. Host tests and source inspection do not establish those
  runtime outcomes.

## Shared-header revision validation (2026-10-31)

Implemented locally: Share and Add to shopping list now occupy the right side
of the recipe-title header, outside the scrollable recipe body. Intent payload,
failure handling, accessibility descriptions, tooltips and enablement remain
unchanged. All 54 offline tests, including both URL tests, release lint and
debug/release builds passed. Android interaction/accessibility checks remain
pending because no device was connected. See [F1 validation](../0001-recipe-browser/spec.md#shared-header-revision-validation-2026-10-31).

## Delivery history

| Date | State | Evidence |
| --- | --- | --- |
| 2026-10-31 | In progress | Accepted specification recorded before implementation. |
| 2026-10-31 | Implemented locally, not released | 49 offline tests, debug/release builds, release lint and whitespace checks passed; Android interaction/accessibility validation pending. |
