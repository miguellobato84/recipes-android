# 0005 Share recipe link proposal

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

## Problem and intended outcome

Share a recipe from its detail screen through Android's standard share sheet.
The receiving app gets only the recipe's GitHub file URL.

## Scope and decisions

- Place icon-only Share and Add to shopping list buttons side by side at the
  start of recipe details. Use a share symbol and a shopping-cart symbol.
- Keep Spanish accessibility labels, long-press tooltips and full-size touch targets.
- Share the public GitHub file page on `main`, consistent with existing source
  links. Sharing remains available for cached recipes offline and independently
  of shopping-list loading or errors.
- Cancelling sharing leaves the recipe unchanged, without confirmation feedback.
- If sharing cannot launch, show “No se puede compartir esta receta.” and remain
  on the recipe. Do not fall back automatically.
- Keep Retry and actions on other screens unchanged.

## Alternatives and tradeoffs

A pinned commit URL would preserve the cached recipe version, but the cache
stores repository-relative paths rather than commit identities. The accepted
`main` link follows source updates and may differ from an older cached recipe.
GitHub availability and the receiving app's handling of the link are external.
Sharing recipe text, attachments, titles, notes, shopping-list contents or an
extra message is outside this feature.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Accept F5 after grilling | Owner confirmed native sharing of only the GitHub main file link, icon-only detail actions, accessibility, offline behavior and failure handling. |

See [the specification](spec.md).
