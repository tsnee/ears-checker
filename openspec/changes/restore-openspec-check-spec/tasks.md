# Tasks

No change to the script's behavior, the hook, sources, or tests.
Every scenario check runs in a scratch copy of the repository, never
in `openspec/` itself.

## 1. Restore and confirm

- [x] 1.1 In `scripts/check-openspec`, change the header comment's "Shared by the Claude Code hook and the git pre-commit hook." to name only the Claude Code hook (CI's use comes with `add-github-actions-ci`); verify `sh -n scripts/check-openspec` passes and `git diff` shows only that comment line
- [x] 1.2 Confirm the restored requirements against today's behavior, one scenario each, in a scratch copy: a 600-character requirement description fails strict validation; an unchecked task in an archived change fails `--archived --strict`; "The scheduler MUST list upcoming events." fails with a weak-modal finding and a nonzero status; verify each run's output and exit status
- [x] 1.3 In the scratch copy, feed `scripts/claude-openspec-hook` a PostToolUse JSON for an edited spec whose requirement reads "The scheduler MUST list upcoming events." (the scenario's corrected example); verify it exits 2 and its message contains the weak-modal finding
- [x] 1.4 In the scratch copy, put a `java` wrapper first on the PATH that logs each start, run `scripts/check-openspec`, and verify the log shows exactly one checker start covering every active change and spec
- [x] 1.5 Update `CLAUDE.md`'s Layout so `openspec/` lists the `openspec-check` spec next to `ears-classifier` and `ears-cli`; verify by reading it with `mq`

## 2. Wrap-up

- [x] 2.1 Run `scripts/check-openspec` and `./mill __.test` in the repository; verify both pass, including `OwnSpecsSuite`
- [x] 2.2 Add a dated line to `docs/worklog.md`; verify by reading it with `mq`

## Workflow follow-up

- Commit the plan, then the implementation.
- Verify, then archive with a spec sync: archiving creates `openspec/specs/openspec-check/spec.md` from the ADDED requirements. Afterwards run `scripts/check-openspec` so the new main spec is EARS-checked, and commit the archive.
