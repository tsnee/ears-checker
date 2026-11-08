# Tasks

All work is on branch `batch-release-0.2.0`, the fourth change in the "release 0.2.0" theme
(draft PR #17). The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. Contributing and security

- [ ] 1.1 Write `CONTRIBUTING.md` (decision 3), moving the README's "Building" section into it. Verify with `mq` that nothing from "Building" is lost, and that every command in it runs (`./mill` tasks and scripts named)
- [ ] 1.2 Write `SECURITY.md` (decision 4). Verify with `mq`, and that the owner has turned on private vulnerability reporting (`gh api repos/tsnee/ears-checker/private-vulnerability-reporting` shows `"enabled":true`), or SECURITY.md names the channel the owner chose

## 2. The README

- [ ] 2.1 Rewrite `README.md` in decision 1's order, for 0.2.0 (decision 2), with the limits from the design's observations. Verify with `mq`, that every link resolves (files in the repository, and URLs with `curl`), and that every command and output was run (decision 5), listing those that can't run until 0.2.0 or npm publishing
- [ ] 2.2 Check the npm packages' READMEs for claims the rewrite contradicts, and fix them. Verify by reading each against the new README
- [ ] 2.3 Add the README's versions to CLAUDE.md's release steps, CONTRIBUTING.md and SECURITY.md to its layout, a `CHANGELOG.md` line under Unreleased, and take the item off the roadmap. Verify with `mq` and `ears.releaseNotes.run check CHANGELOG.md`

## 3. Checks, review, and commits

- [ ] 3.1 Run `scripts/check-openspec` and `./mill __.checkFormat` (no code changes, so the full gate isn't needed), and add a dated line to `docs/worklog.md`
- [ ] 3.2 Run `/code-review` in a subagent on this change (accuracy of every claim against the code), and let the owner choose which findings to fix
- [ ] 3.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch for the owner to push.
- The theme continues with the roadmap's next items; merging PR #17 waits for the theme's end.
