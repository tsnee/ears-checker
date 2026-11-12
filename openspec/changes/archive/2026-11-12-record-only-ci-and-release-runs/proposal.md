# Proposal

## Why

`scripts/ci-run-record` records whatever run it's given. On 2026-11-11 it recorded run 43140165200, a "Dependabot Updates" run, as if it were CI: one job, one minute, mixed into the records `docs/measurements/ci-runs.jq` reports minutes from. It was caught by its odd size and removed by hand. Nothing stops it happening again, since GitHub lists Dependabot's runs beside CI's on the same branch.

## What Changes

- `scripts/ci-run-record` refuses a run of any workflow but "CI" and "Release" (the two this repository records, the second for its release runs), before writing or creating anything, with a message naming the run's workflow.
- `scripts/test-ci-run-record` tests that choice with a stand-in `gh`, and CI runs it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `measurement-records`: "CI run records" covers CI and Release runs only; new requirements refuse other workflows' runs and leave the records file untouched for them.
- `continuous-integration`: a new requirement that CI runs the recorder's test.

## Impact

- `scripts/ci-run-record` (the workflow checked first, the file created after the checks) and the new `scripts/test-ci-run-record`.
- `.github/workflows/ci.yml` (a step, and a comment by `name:`), `release.yml` (a comment by `name:`).
- `CLAUDE.md`'s gate list, CI description, scripts, and `ci-runs.jsonl`; `CONTRIBUTING.md`'s scripts; the roadmap's Later item comes out.
