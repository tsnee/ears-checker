# Tasks

All work is on branch `ci-run-record-workflows`. The owner pushes from their own shell.

## 1. The check

- [x] 1.1 First, with today's script and `--records` on a scratch copy of `docs/measurements/ci-runs.jsonl`: run 43140165200 (Dependabot) is recorded, so the copy changes (the failing case, before the fix)
- [x] 1.2 The check (decision 1); then, on a fresh scratch copy, run 43140165200 fails with a message naming "Dependabot Updates" and the copy is byte for byte unchanged (`cmp`), while 43085781522 (Release) and a recorded CI run pass the check and report "already recorded"; `sh -n` and `shellcheck` pass
- [x] 1.3 (Added after review.) `scripts/test-ci-run-record` (decision 2) with a stand-in `gh`: written before the fixes, it failed on the unfinished Dependabot run (told to wait) and on a refused run creating the records directory; after the workflow check moved first and the file's creation after the checks, all seven cases pass. CI's `check` job runs it, and each workflow's `name:` has a comment that the script relies on it; `actionlint`, `sh -n`, `shellcheck`

## 2. Documents

- [x] 2.1 The script's header comment; `CLAUDE.md`'s description of `ci-runs.jsonl` and `CONTRIBUTING.md`'s of the script say only CI and Release runs are recorded; the roadmap's Later item comes out; the work log; `prettier --check`

## 3. Checks, review, commits

- [x] 3.1 `scripts/check-openspec` passes (the change touches no Scala, so the rest of the gate has nothing to check; CI runs it all)
- [x] 3.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix. (Eight findings, all applied: the workflow checked first and nothing created before the checks; the existing "CI run records" requirement modified rather than contradicted; the Release scenario as an accepted run; the refusal split into two single-response requirements; the repeatable test and its CI step; comments beside the workflows' names; the header reflowed.)
- [ ] 3.3 Commit (plan, then implementation); the owner pushes; open a PR, whose CI runs. Report the run's result and minutes, from the records
- [ ] 3.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- After CI passes and verify is clean, archive the change (syncing `measurement-records`).
