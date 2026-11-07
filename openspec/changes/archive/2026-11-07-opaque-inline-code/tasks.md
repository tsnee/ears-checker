# Tasks

All work is on branch `batch-node-cli`, the third change in its batch; draft pull request #16
carries it, and its CI runs on every push. The owner pushes from their own shell; Claude reads
runs with `gh`. Compile, test, and format through Metals (BSP) where it can; the library's
shared suites, Scala.js and Scala Native, the coverage check, and the final run go through
`./mill`.

## 1. Inline code in the classifier (test-first)

- [x] 1.1 Add tests (in a new `InlineCodeSuite`) for each scenario of the `ears-classifier` delta (periods, a quoted modal, a quoted `SHALL` alone, double backticks, an unmatched backtick, escaped backticks, a system name in code), and a property: a code span of arbitrary backtick-free text in a valid sentence's response leaves it valid, with the span whole in the response. Verify they fail before 1.2, on the JVM
- [x] 1.2 Read code spans as words in `SkeletonParser` (design Decisions 1–2; Decision 3 was checked and found unnecessary, see design.md). Verify `./mill ears.test ears.js.test` pass, the coverage minimums hold, and `scripts/check-scalajs-link` passes

## 2. Inline code in Markdown sentences (test-first)

- [x] 2.1 Add conformance fixtures for the nine backtick edge cases in design.md's Context (each with the modal keyword, so a sentence is returned), a period inside code, and escaped backticks written as `` \` ``, and `HelpersSuite` tests for the fence and the escaping. Before changing the three existing fixtures whose expected sentences gain backticks, ask the owner, showing the `markdown-input` requirement they cover. Verify the new fixtures fail before 2.2
- [x] 2.2 Wrap code pieces in fences and skip periods inside code in `Extraction` (design Decision 4). Verify the conformance suite passes on the JVM, Node, and Scala Native, `ears.markdown`'s coverage stays at 100/100, and `OwnSpecsSuite` and `scripts/check-openspec` still pass on this repository's specs

## 3. Byte-order marks (test-first)

- [x] 3.1 Add `Utf8Suite` tests: a UTF-8 mark is dropped (and positions count from after it); UTF-16 LE and BE and UTF-32 LE and BE marks give `OtherEncoding`; a `CliSuite` test for the message. Verify they fail before 3.2
- [x] 3.2 Implement design Decision 5 in `Utf8` and `ReadFailure`, and add `scripts/check-npm-cli` cases for UTF-16 input (exit 2) and plain text with a mark (exit 1, no mark in the fix). Verify the `ears-cli` scenarios by hand on the jar (plain text, OpenSpec JSON, UTF-16, UTF-32), `ears.cli` and `ears.cli.js` tests pass, and `scripts/check-npm-cli` passes every case

## 4. Docs, checks, and review

- [x] 4.1 Update `README.md` (how inline code and byte-order marks are read, in the command line's and the library's sections) and `docs/roadmap.md` (the item comes off). Verify with `mq`
- [x] 4.2 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, report the coverage, and add a dated line to `docs/worklog.md`
- [x] 4.3 Run `/code-review` in a subagent on this change's commits, and let the owner choose which findings to fix
- [x] 4.4 Run `/security-review` in a subagent on this change's commits, and let the owner choose which findings to fix
- [x] 4.5 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes on pull request #16

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `ears-classifier`,
  `markdown-input`, and `ears-cli`) for the owner to push.
- This makes three changes in the batch, its ship point: mark #16 ready and squash-merge it. A
  release (0.2.0, for the classifier change) then needs `/security-review` on everything since
  0.1.1 before the tag, per CLAUDE.md's Releasing steps.
