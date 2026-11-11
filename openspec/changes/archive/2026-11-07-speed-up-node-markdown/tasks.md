# Tasks

All work is on branch `batch-release-0.2.0`, the fifth change in the "release 0.2.0" theme (draft PR #17). The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. Timing first

- [x] 1.1 Write `scripts/time-cli` (decision 5). Verify with `sh -n` and `shellcheck`, and that a run on this commit reproduces the design's shape (Node superlinear on Markdown); record it in a new `docs/performance.md`

## 2. The classifier

- [x] 2.1 Build the code-span content parser once (decision 1). Verify that `./mill ears.test ears.js.test ears.native.test` pass unchanged and that `scripts/time-cli` shows the plain-text speedup

## 3. commonmark.js on Node (test-first)

- [x] 3.1 Write `SourceLines`' tests and properties (decision 3), on the JVM. Verify they fail before 3.2
- [x] 3.2 Implement `SourceLines`. Verify the tests pass and `ears.markdown` keeps 100% statement and branch coverage
- [x] 3.3 Switch `ears.markdown.js` to `commonmark` 0.31.2 (package.json, lockfile with `npm install --package-lock-only --ignore-scripts`, the import map) and rewrite Node's `MarkdownParser` on it (decision 2). Verify the conformance suite passes unchanged on Node (`./mill ears.markdown.js.test`) and on the JVM and natively; if a fixture can't pass, stop and report
- [x] 3.4 Ask the owner before removing `LineEndings` and its tests, showing the requirement they cover; remove them if agreed. Verify `./mill __.compile` and the helpers' tests
- [x] 3.5 Point the CLI npm package's dependency at `commonmark` (`build.mill`). Verify `scripts/check-npm-cli` passes, and that the packed package lists only `commonmark` 0.31.2 under `dependencies`

## 4. Measure, document, review, commit

- [x] 4.1 Run `scripts/time-cli` and record it in `docs/performance.md`. Verify Node is linear on Markdown within the run's noise
- [x] 4.2 Update CLAUDE.md (Node's parser, the facade notes, `scripts/time-cli`), CONTRIBUTING.md, the README if a claim changed, the conformance suite's and `HelpersSuite`'s comments, CHANGELOG (Changed), and take the item off the roadmap. Verify with `mq`
- [x] 4.3 Run the full gate: `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, and the five check scripts. Report coverage and add a dated line to `docs/worklog.md`
- [x] 4.4 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 4.5 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `cli-npm-package`) for the owner to push.
