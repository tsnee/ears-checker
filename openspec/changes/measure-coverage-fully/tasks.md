# Tasks

All work is on branch `batch-release-0.2.0`, the seventh change in the "release 0.2.0" theme
(draft PR #17). The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The clean step

- [x] 1.1 Add `./mill clean __.scoverage` before `./mill __.test` to CLAUDE.md's coverage notes and list of checks, and to CONTRIBUTING.md's (decision 1). Verify with `mq`, and that `./mill clean __.scoverage` followed by the measured tests gives the clean-build statement counts (ears 684, ears.cli 512, ears.npm 56, ears.linkCheck 294, ears.markdown 558, ears.releaseNotes 235)

## 2. Checks, review, commits

- [x] 2.1 Run the full gate as documented, the clean step included: `./mill __.checkFormat`, `./mill clean __.scoverage`, `./mill __.test`, every module's `validateCoverageMinimums`, and the five check scripts. Report coverage, and add a dated line to `docs/worklog.md`
- [x] 2.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [ ] 2.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `test-coverage`) for the owner
  to push.
