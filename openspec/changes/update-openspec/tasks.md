# Tasks

All work is on branch `batch-release-0.2.0`, in the "release 0.2.0" theme (draft PR #17). The
owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The owner's machine

- [x] 1.1 The owner runs `npm uninstall -g @fission-ai/openspec` and `openspec config set delivery skills` in their own terminal (decisions 1 and 2). Check that `command -v openspec` is `/opt/homebrew/bin/openspec`, that it reports 1.17.0, and that `openspec config get delivery` is `skills`

## 2. The repository

- [x] 2.1 Run `openspec update`; check that it refreshed the 11 skills and removed `.claude/commands/opsx/`, and changed nothing else (`git status`)
- [x] 2.2 Pin `@fission-ai/openspec` 1.17.0 in `.github/tools/package.json` and regenerate the lockfile (decision 3); check with `jq` that the lockfile resolves 1.17.0
- [x] 2.3 CLAUDE.md's Workflow section names the skills; the roadmap item is marked in progress (moved to done at the archive). Verify with `mq`

## 3. Checks, review, commits

- [x] 3.1 Run `scripts/check-openspec` (which uses the local 1.17.0) and the CI tools install (`npm ci --ignore-scripts` with a temporary cache into a scratch copy of `.github/tools`); add a dated line to `docs/worklog.md`. No Scala changes, so the rest of the gate isn't needed
- [x] 3.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [ ] 3.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes, and that its jobs installed OpenSpec 1.17.0

## Workflow follow-up

- After CI passes, archive the change on the same branch (no specs to sync) for the owner to
  push.
