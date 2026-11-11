# Tasks

All work is on branch `batch-release-0.2.0`, in the "release 0.2.0" theme (draft PR #17), the second of the security review's two fixes. The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The pinned install

- [x] 1.1 `scripts/npm-cli-project` (decision 1): the dependency check, the built `package.json` and lockfile, and `npm ci --ignore-scripts`; `sh -n` and `shellcheck`
- [x] 1.2 `scripts/check-npm-cli` installs through it; with `--out` it keeps the tarball and jar right after packing and checks their checksums at the end (decision 2)
- [x] 1.3 `scripts/time-cli` installs through it; `scripts/check-npm-package` adds `--ignore-scripts` (decision 3)

## 2. Checks against the spec

- [x] 2.1 Run `scripts/check-npm-cli --out` on a scratch directory: it passes, installs exactly the four pinned dependencies, and keeps both files with their checksums unchanged
- [x] 2.2 With a scratch copy of the facade lockfile holding a wrong checksum for `entities`, the check fails at the install with `EINTEGRITY`; with a kept file changed during the run (a scratch hook), it fails naming the file and both checksums

## 3. Docs

- [x] 3.1 CLAUDE.md and CONTRIBUTING.md (how the Node CLI check installs), the roadmap, and the work log. Verify with `mq`

## 4. Checks, review, commits

- [x] 4.1 Run the full gate as documented (`./mill __.checkFormat`, `./mill clean __.scoverage`, `./mill -j 1 '__:ScalaNativeModule.compile'`, `./mill __.test`, every module's `validateCoverageMinimums`, and the six check scripts). Report coverage, and add a dated line to `docs/worklog.md`
- [x] 4.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 4.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes, including that `check` and `check-jdk17` installed the pinned packages
- [x] 4.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- After CI passes and verify is clean, archive the change on the same branch (syncing `continuous-integration`) for the owner to push.
