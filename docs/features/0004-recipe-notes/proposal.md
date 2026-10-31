# 0004 Recipe notes proposal

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

## Problem and intended outcome

Keep personal cooking notes beside each recipe without changing its source file.
Provide one simple multiline plain-text box after the full recipe.

## Scope and decisions

- Label the box **Mis notas**, distinct from the source recipe's **Notas**.
- Save automatically while typing; no Save button. Empty text removes the note.
- Store notes in private app data, independently of the recipe cache and shopping
  list. Retain them offline, across restarts, recipe refreshes, cache clearing,
  and ordinary APK updates. Clearing app data or uninstalling removes them.
- Use the existing repository-relative recipe path as identity. Source text or
  title changes at that path retain the note; a renamed path has a separate note.
  Retain notes for removed paths in case the recipe returns.
- Preserve unsaved text in memory on save failure, report a Spanish error, and
  allow retry. Do not report an unsuccessful disk write as saved.

## Alternatives and tradeoffs

Android cache storage can be evicted, so private app files meet the confirmed
retention requirement. A separate atomic store prevents recipe refresh from
overwriting personal text. Source editing, Markdown formatting, sync, sharing,
and a separate notes manager are outside scope. A process killed before an
in-flight autosave completes can lose that unsaved edit.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Accept notes scope after grilling | Owner confirmed autosave, durable local app data, and a separate personal-notes box after the recipe. |

See [the specification](spec.md).
