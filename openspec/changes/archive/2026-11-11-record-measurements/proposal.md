# Proposal

## Why

The owner's global rules (2026-11-11) ask that every measured run be kept as structured records written by the tool itself, with the queries behind any reported figure. Here, `scripts/time-cli` prints a Markdown table that is pasted into `docs/performance.md`; coverage percentages are copied from console output into the work log by hand; and CI minutes are summed with an ad hoc query and only reported in conversation, never recorded. Only the Scala Native compile rounds (`docs/measurements/native-compile-rounds.jsonl`, with the query behind their table) meet the rule so far.

## What Changes

- `scripts/time-cli` also writes one JSON record per timed cell (date, commit, machine, runtime versions, build, input, size, seconds) to `docs/measurements/time-cli.jsonl`, or the file `--records` names; a kept query renders a run's Markdown table from the records, and the three runs already in `docs/performance.md` are backfilled.
- `scripts/coverage-records` writes one JSON record per measured module (date, commit, module, statements, statements invoked, statement and branch rates) from scoverage's own XML report, to `docs/measurements/coverage.jsonl`; the work log cites it instead of retyping percentages.
- `scripts/ci-run-record <run id>` writes one JSON record per job of a GitHub Actions run (run, commit, workflow, job, conclusion, start, end, minutes) to `docs/measurements/ci-runs.jsonl`, from `gh`; the runs on this branch are backfilled, and the minutes Claude reports come from a kept query over the file.
- CLAUDE.md and CONTRIBUTING.md say where measurements live and how they're queried.

## Capabilities

### New Capabilities

- `measurement-records`: the developer scripts' structured records of what they measure, and the queries that report from them.

### Modified Capabilities

- None.

## Impact

- `scripts/time-cli`, new `scripts/coverage-records` and `scripts/ci-run-record`, `docs/measurements/` (records and `.jq` queries), `docs/performance.md`, CLAUDE.md, CONTRIBUTING.md, the work log.
- Developer-only: nothing released, and no workflow changes.
