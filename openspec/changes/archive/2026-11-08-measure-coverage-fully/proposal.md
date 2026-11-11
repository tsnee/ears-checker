# Proposal

## Why

On 2026-11-08 a local coverage report said `ears.cli` had 100% statement and branch coverage, and
the minimum check passed, while the report counted 12 statements, all in `OpenSpecInput.scala`,
of the module's 512. By the code review's reading of the compiler, that happens only when the
instrumented copy's statement list (`scoverage.coverage`) was missing or damaged as an incremental
compile ran (design); nothing records how. CI builds from a
fresh checkout, so its numbers were right; locally, a "done" change could report coverage it
didn't have.

## What Changes

- Coverage is measured from a clean slate, as CI does: the documented checks before a change is
  done (CLAUDE.md and CONTRIBUTING.md) run `./mill clean __.scoverage` before `./mill __.test`,
  removing every measured module's instrumented copy and its coverage data, so each report comes
  from a full compile and fresh measurements. It costs about 10 s.
- The coverage requirement names `ears.releaseNotes`, which has been measured since
  add-release-notes but wasn't listed.
- No build change: an override making the instrumented compile non-incremental was tried and
  reviewed, and dropped as more than the owner's simpler rule needs (design).

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `test-coverage`: coverage is recorded for all six modules.

## Impact

- CLAUDE.md and CONTRIBUTING.md: the clean step.
- `openspec/specs/test-coverage`: the requirement names `ears.releaseNotes`, and its scenario
  starts with the clean step.
- `docs/worklog.md`.
