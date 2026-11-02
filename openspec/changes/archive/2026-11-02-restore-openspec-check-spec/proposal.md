# Proposal

## Why

The repository's OpenSpec gate (`scripts/check-openspec` and the
Claude Code hook that runs it) has no main spec. Its requirements
were written as the `openspec-check` capability in two archived
changes, `add-ears-checker` and `batch-ears-check`, but
`openspec/specs/openspec-check/` didn't come over when the checker
was extracted into this repository. So the archives describe
behavior the specs don't, and a later change to the gate would have
no main spec to modify.

## What Changes

- **Restore `openspec-check`** as a main spec with the six
  requirements from the two archived deltas, word for word, and the
  Purpose from `add-ears-checker`. Every requirement was checked
  against today's script and hook, and all six still hold.
- **Correct one scenario's example.** "Report to Claude Code" said
  an edit to "The scheduler should list upcoming events." brings
  back the weak-modal finding. It doesn't: strict validation rejects
  a requirement with no SHALL or MUST, and the script stops there
  before EARS checking. The scenario now uses "MUST", which passes
  validation and gets the weak-modal finding, as the original
  change's own hook test did.
- **Fix a stale comment** in `scripts/check-openspec` that says a git
  pre-commit hook shares the script; this repository has none.
- **Update `CLAUDE.md`'s Layout** to list the restored spec.

No behavior changes: the script and the hook already do what the
restored requirements say.

## Capabilities

### New Capabilities

- `openspec-check`: the repository's OpenSpec gate: strict
  validation of every change and spec, archived task completion, one
  EARS checker run over every active change and spec, a failing
  status, and the report back to Claude Code. New to
  `openspec/specs/`; its requirements come from the archives.

### Modified Capabilities

None.

## Impact

- `openspec/specs/openspec-check/spec.md` at archive time.
- One comment in `scripts/check-openspec`, and `CLAUDE.md`.
- No code, test, or dependency changes.
