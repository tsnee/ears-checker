# Tasks

All work is on branch `batch-release-0.2.0`, the third change in its batch (draft PR #17). The
owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The version and the tests (test-first)

- [x] 1.1 Add the `CheckerSuite` tests of decision 5: the whole document per location type (line, change, spec), no findings (`{"version":1,"checked":1,"findings":[]}`), a `null` fix, the emoji span (`start` 17), and the property that every document starts with `"version":1` and ends with one line break. Verify they fail only where `version` is missing
- [x] 1.2 Write `"version": 1` first in the document (`Checker.report`). Verify the new and existing tests pass on the JVM, Node, and Scala Native (`./mill ears.cli.test ears.cli.js.test ears.cli.native.test`) and `ears.cli` meets its coverage minimums

- [x] 1.3 Add `"checker"` second in the document (decision 6), from a `CheckerBuild.version` that `build.mill` generates for the JVM, Node, and native builds, with tests first. Verify the tests pass on all three platforms, a release build generates `0.2.0` (`EARS_RELEASE_VERSION=0.2.0`), and the reference's examples match the jar's output

## 2. Docs

- [x] 2.1 Write `docs/json-output.md` (decision 3), each example copied from a run of the built jar. Verify with `mq` and by comparing each example with the jar's output
- [x] 2.2 Add the README's "JSON output" subsection, a `CHANGELOG.md` line under Unreleased (Changed: the `version` field), CLAUDE.md's layout entry for the reference, and take the item off the roadmap. Verify with `mq` and `ears.releaseNotes.run check CHANGELOG.md`

## 3. Checks, review, and commits

- [x] 3.1 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`, `scripts/check-native-cli`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, report the coverage, and add a dated line to `docs/worklog.md`
- [x] 3.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [ ] 3.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `ears-cli`) for the owner to
  push.
