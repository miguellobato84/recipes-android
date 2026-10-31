# Feature history and specifications

Each feature has a permanent, sequential ID and a folder under `features/`.
Keep proposals and specifications in Git alongside implementation.

Scaffolding, app branding, and release infrastructure are documented in
[scaffolding](scaffolding.md), outside the numbered product features. Feature 0001
was first released in [v6](https://github.com/miguellobato84/recipes-android/releases/tag/v6).

| ID | Feature | Decision | Delivery | Documents |
| --- | --- | --- | --- | --- |
| 0001 | Read-only recipe browser | Accepted | In progress (startup revision) | [Proposal](features/0001-recipe-browser/proposal.md), [specification](features/0001-recipe-browser/spec.md) |

## Workflow

1. Before implementing a new feature, create `features/NNNN-short-name/` with
   `proposal.md` and `spec.md`, and add it to this index.
2. In the proposal, describe the problem, intended outcome, scope, alternatives,
   tradeoffs, decision, and dated decision history.
3. In the specification, describe observable behavior, data contracts, errors,
   acceptance criteria, validation, open questions, and delivery history.
4. Keep decision status separate from delivery status. Accepted does not mean shipped.
5. Resolve open questions before implementing dependent behavior.
6. Before modifying an existing feature, replace outdated content in its current
   proposal and specification with the revised behavior. Retain its ID and paths;
   create a new numbered folder only for a distinct new feature. Record material
   decisions with their date and rationale. Git history preserves earlier versions.
7. When shipped, add the release tag, implementation PR or commit, and validation
   evidence to delivery history and update this index.

Decision statuses: Proposed, Accepted, Rejected, Superseded.
Delivery statuses: Not started, In progress, Released.

The current documents describe the latest specified behavior. Git preserves their
previous versions. The dated history records product decisions and release
references; it is not a duplicate commit log.
