# 0003 Shopping list proposal

Decision: Accepted

Delivery: In progress (implemented locally; Android-device validation pending)

## Problem and intended outcome

Prepare a shopping list from recipes without copying their ingredients manually.
Users can move between the existing recipe browser and their shopping list and
see which recipes contributed to the list, including repeated additions.

## Owner-requested scope

1. A navigation bar or side menu switches between recipe list/detail mode and
   shopping-list mode.
2. The shopping list has a **Vaciar** button that clears it.
3. A recipe action adds all its ingredients. Every tap adds them again; repeated
   additions are intentional and must not be deduplicated away.
4. The shopping list shows the included recipes and their addition counts, for
   example **2x Lentejas estofadas**.

## Decisions

- Use a bottom navigation bar with **Recetas** and **Lista de la compra**.
- Match the recipe screens' shared header. Place a filled trash-icon button on
  its right to clear the list; expose “Vaciar carrito” as spoken label and tooltip.
- Use an icon-only shopping-cart action in recipe details beside Share; retain
  its Spanish accessibility label and tooltip, as specified by [F5](../0005-recipe-sharing/spec.md).
- Preserve the recipe tree expansion, scroll, and selected detail while switching destinations and
  use Android back to return from shopping list to recipes.
- Persist shopping-list state in private app storage across restarts and ordinary
  APK updates. Store each addition as a snapshot of source path, displayed title,
  and the full Markdown in its `Ingredientes` section.
- Keep every ingredient contribution as added. Do not aggregate quantities or
  merge similar text. Group additions by source path; show the title from the
  most recent addition for that path. Earlier ingredient snapshots remain intact
  if the source title or ingredients later change.
- Make **Vaciar** clear immediately, with Spanish success or save-error feedback.
- Queue every tap, including taps during a save; disable changes only while
  loading or after an unreadable-store error. Empty ingredient sections report
  an error without adding a recipe count.
- Save each add or clear as one atomic replacement. On a failed save, retain the
  last complete in-memory and on-disk list and report failure.

## Boundaries and tradeoffs

F3 extends [F1](../0001-recipe-browser/spec.md) with local shopping-list state and
navigation. Recipe source files remain read-only. F3 does not change startup
recipe refresh, cached browsing, or F2's app-update behavior.

Ingredient normalization and automatic unit conversion would need rules for
free-text ingredients, optional quantities, and different units. They are not
implied by displaying a recipe count. Manual item editing, individual removal,
purchase checkboxes, sharing, and cloud synchronization are outside this scope.

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-10-31 | Match shared screen headers | Owner requested uniform headers with actions on the right and a clear-cart button; use a filled trash icon with accessible label and tooltip. |
| 2026-10-31 | Record F3 requirements | Owner requested shopping-list navigation, clear-all, repeatable ingredient additions, and included-recipe counts. |
| 2026-10-31 | Accept implementation decisions | Bottom navigation preserves recipe position; atomic private-file snapshots retain each source ingredient section and repeated addition across refresh and restarts. |
| 2026-10-31 | Align with F1 tree navigation | Returning from shopping preserves expanded branches, tree scroll, and selected detail within the same browsing session. |
| 2026-10-31 | Use an icon-only detail action with F5 | Owner requested a shopping-cart icon beside Share, retaining Spanish accessibility and tooltip text. Shopping behavior is unchanged. |

See [the specification](spec.md) for behavior, examples, and acceptance criteria.
