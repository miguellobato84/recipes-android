# Agent instructions

## Git workflow

Work, commit, and push directly on `main` in this repository. Use another branch
only when the user explicitly requests it. This repository rule overrides the
GitHub CLI skill's default restriction on committing to `main`.

## Feature driven development

All feature work must be driven by the feature documents in `docs/features/`.
Before implementing a feature or changing its behavior, read `docs/README.md`
and the relevant proposal and specification.

Scaffolding, build/release setup, and app branding are infrastructure rather than
numbered features. For those changes, read and update `docs/scaffolding.md`.

- **New feature:** create its numbered folder, `proposal.md`, and `spec.md`, and
  add it to the documentation index before writing implementation code. Define
  scope, observable behavior, and acceptance criteria first.
- **Existing feature:** replace outdated content in its existing proposal and
  specification with the revised behavior before changing implementation. Keep
  the same feature ID and paths; Git history preserves earlier document versions.
  Create a new numbered folder only for a distinct new feature.
- Resolve scope conflicts and implementation-blocking questions before coding
  the affected behavior. Implement only behavior covered by the feature docs.
- Before completion, verify the change against the documented acceptance
  criteria, update delivery status and validation evidence, and report checks
  that could not be run. Keep docs and implementation consistent in the same change.
