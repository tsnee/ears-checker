# Tasks

All work is on branch `add-cli-on-node`, in one pull request that also carries the archive. The
owner pushes from their own shell; Claude opens the pull request and reads runs with `gh`.
Compile, test, and format through Metals (BSP) where it can; shared test sources, `forkEnv`,
Scala.js and npm, `show`, the coverage check, and the final CI-equivalent run go through
`./mill`. npm commands take `--cache` under a temporary directory.

## 1. One program, on the JVM (test-first)

- [x] 1.1 Give `ears.cli` platform source directories (`src-jvm`, `test/src-jvm`) and move `Main`, `MainSuite`, `OwnSpecsSuite`, and `LocaleSuite` there unchanged (design Decision 3). Verify every `ears.cli` test still passes and the build's outputs are unchanged
- [x] 1.2 Record a baseline: the jar's standard output, messages, and exit status on every tracked Markdown file, the OpenSpec JSON, and the error cases of design Decision 5, from the jar built before 1.3
- [x] 1.3 Add tests for `ReadFailure`'s messages and for `Cli` with a fake `Reading` (each failure kind, standard input, several files). Verify they fail to compile before 1.4
- [x] 1.4 Move the program out of `Main` into shared `Cli` behind `Reading` (design Decision 1), with the logger's time from ZIO's clock (design Decision 2), and map the JVM's exceptions to `ReadFailure` in `Main`. Verify every `ears.cli` test passes, the coverage minimums hold, and the jar's output equals the baseline except the two messages in design Decision 1. Before changing any existing `MainSuite` assertion for them, ask the owner

## 2. The CLI on Node

- [x] 2.1 Add `ears.cli.js` (design Decision 3): Scala.js, ES module, `NodeMain` as main class, scala-java-time 2.9.0, the Markdown facade's Node sources, and tests on Node with the facade's import map. Verify the shared CLI suites pass on Node
- [x] 2.2 Write `NodeMain` and its `Reading` over `node:fs` (design Decision 1). Verify with a release link and `node` by hand on a file, piped input, a missing file, and a directory, and record the size of `main.js`

## 3. The package and the check

- [x] 3.1 Move the library's source-map embedding into a helper both package tasks share, and add `ears.cli.js.npmPackage` (design Decision 4). Verify the library package is unchanged (its packed file list and `package.json`), and check the CLI package's `package.json` with `jq`: name, version, `bin`, the exact `mdast-util-from-markdown` dependency read from the lockfile, `engines`, `repository`, and `files`
- [x] 3.2 Write `scripts/check-npm-cli` (design Decision 5). Verify it passes, then that it fails, each change reverted afterwards: when `NodeMain`'s "no such file" message changes, when the Node CLI exits with a different status for a failing file, and when the packed package lacks `bin/ears-check.js`
- [x] 3.3 Add the check to `check` and `check-jdk17` in `.github/workflows/ci.yml`. Verify with `yq`

## 4. Documentation

- [x] 4.1 Update `README.md`, `CLAUDE.md`, and `docs/roadmap.md` (design Decision 6). Verify with `mq`

## 5. Checks, review, and pull request

- [x] 5.1 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, and report the coverage and both packages' sizes. Add a dated line to `docs/worklog.md`
- [x] 5.2 Run `/code-review` in a subagent on the branch's diff, and let the owner choose which findings to fix
- [ ] 5.3 Commit; the owner pushes. Open the pull request with `gh`, recommending squash merge. Report the CI run's result and minutes. After CI passes, archive the change on the same branch (creating `cli-npm-package`, syncing `ears-cli` and `continuous-integration`) for the owner to push and merge
