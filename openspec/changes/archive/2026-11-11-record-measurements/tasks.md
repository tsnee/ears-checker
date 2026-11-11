# Tasks

All work is on branch `batch-release-0.2.0`. The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. CI run records

- [x] 1.1 `scripts/ci-run-record` (decisions 1 and 2) and `docs/measurements/ci-runs.jq` (minutes per run); `sh -n`, `shellcheck`
- [x] 1.2 The spec's scenarios: run 43065132303 gives ten records summing to 45 minutes; recording it again changes nothing
- [x] 1.3 Backfill every finished run on this branch (decision 3)

## 2. Coverage records

- [x] 2.1 `scripts/coverage-records` and `docs/measurements/coverage.jq` (the latest record per module, as a table)
- [x] 2.2 After `./mill clean __.scoverage` and `./mill __.test`, six records whose figures match each module's `consoleReport`

## 3. Timing records

- [x] 3.1 `time-cli --records`, writing one record per cell, and its table rendered by `docs/measurements/time-cli.jq` from the run's records
- [x] 3.2 Backfill the three runs in `docs/performance.md`; `time-cli.jq` renders each identically
- [x] 3.3 A real run (`--builds jvm --copies 8`): two records, and the printed table matches them

## 4. Docs

- [x] 4.1 CLAUDE.md (layout, and that measurements are records plus queries), CONTRIBUTING.md, `docs/performance.md` (where its tables come from), the roadmap, and the work log; verify with `mq`

## 5. Checks, review, commits

- [x] 5.1 Run the gate's affected checks (`sh -n` and `shellcheck` on every changed script, `scripts/check-openspec`), and record this change's own CI run with the new script
- [x] 5.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 5.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes, from the records
- [x] 5.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- After CI passes and verify is clean, archive the change (creating `measurement-records`).
