# Tasks

All work is on branch `batch-release-0.2.0`, in the "release 0.2.0" theme (draft PR #17), the first of the security review's two fixes. The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. Tests first

- [x] 1.1 `VisibleSuite`: each ISO control, U+2028, and U+2029 becomes `\uXXXX`; everything else, backslash included, is unchanged (properties over generated strings), with concrete witnesses for ESC, BEL, NEL, and U+2028
- [x] 1.2 `CheckerSuite`: the spec's scenarios (an escape sequence in a `Fix:`, U+2028 in an OpenSpec requirement name, ESC in a missing file's name), a vertical tab, line feeds in a file name and in an argument, and properties that a control character inside a fix or a missing file's name shows as `\u` and four hexadecimal digits (expected text built without a format string); see them fail

## 2. The fix

- [x] 2.1 `Visible` in `ears/cli/src` (decision 1), applied to the text report after `singleLine`, and to the messages on standard error that aren't log lines; the tests pass
- [x] 2.2 `scripts/cli-cases`: a case whose input carries an escape sequence, so every build is compared on it

## 3. Docs

- [x] 3.1 SECURITY.md (what text output and messages escape), CHANGELOG.md under Unreleased, Fixed, the roadmap, and the work log. Verify with `mq`

## 4. Checks, review, commits

- [x] 4.1 Run the full gate as documented (`./mill __.checkFormat`, `./mill clean __.scoverage`, `./mill -j 1 '__:ScalaNativeModule.compile'`, `./mill __.test`, every module's `validateCoverageMinimums`, and the six check scripts). Report coverage, and add a dated line to `docs/worklog.md`
- [x] 4.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 4.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `ears-cli`) for the owner to push.
