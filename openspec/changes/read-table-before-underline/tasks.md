# Tasks

All work is on branch `batch-tables`. The owner pushes from their own shell. Test-first: each behavior's test or fixture is written and seen failing before the code that makes it pass. During groups 1 and 2, the JVM and Node runs are enough (`./mill ears.markdown.test ears.markdown.js.test`); Scala Native runs in the gate (task 3.1) and in CI.

## 1. Fixtures, first

- [ ] 1.1 The conformance fixtures of design decision 4 in `MarkdownConformanceSuite`, one per spec scenario and per other case, each checked against GitHub's renderer (`gh api markdown -f mode=gfm`, the result in its comment) and each, except the one-column heading, seen failing on the JVM and Node. Ask the owner before editing the stale comment in "an empty list item ends a table".

## 2. Underlined paragraphs and the thematic break

- [ ] 2.1 `Rows.endsTable` ends a table at a thematic break (decision 3), with `RowsSuite` tests (the breaks and the near misses, as a property where it can be stated) seen failing first; `./mill ears.markdown.test` passes.
- [ ] 2.2 `ParsedParagraph.underlined`, and each parser (commonmark-java, commonmark.js, cmark) giving a heading that spans more than one line as an underlined paragraph, cmark's pieces placed from the line before the underline (decision 1); `Tables` reads an underlined paragraph only for its table and drops one without, the no-pipe shortcut included (decision 2), with `TablesSuite` tests seen failing first. Every fixture from 1.1, and every existing one, passes on the JVM and Node.
- [ ] 2.3 `ears.markdown` coverage at its minimums, from a clean slate (`./mill clean __.scoverage`, then `./mill ears.markdown.test` and `./mill ears.markdown.scoverage.validateCoverageMinimums`).
- [ ] 2.4 Docs: the README's known difference about `---` and `===` under a table removed (one difference left, the definition above a table); CHANGELOG's Unreleased tables entry checked still true; the work log. `prettier --check '**/*.md'` and `./mill ears.releaseNotes.run check CHANGELOG.md` pass.

## 3. Checks, review, commits

- [ ] 3.1 Run the gate (CLAUDE.md's list, Scala Native included) and report coverage from `scripts/coverage-records`.
- [ ] 3.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix.
- [ ] 3.3 Commit (plan, then implementation); the owner pushes; PR #25 runs CI. Report the run's result and minutes, from the records.
- [ ] 3.4 Run `/openspec-verify-change` and report it before archiving.

## Workflow follow-up

- After CI passes and verify is clean, archive the change (syncing `markdown-input`), mark roadmap item 1 done, and merge the theme after a security review of the batch.
