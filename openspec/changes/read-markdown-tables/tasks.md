# Tasks

All work is on branch `batch-tables`. The owner pushes from their own shell. Test-first: each behavior's test or fixture is written and seen failing before the code that makes it pass. Scala Native runs in the gate (task 5.1) and in CI; during tasks 1 to 3 the JVM and Node runs are enough (`./mill ears.markdown.test ears.markdown.js.test`, `./mill ears.cli.test ears.cli.js.test`).

## 1. The assumptions, first

- [ ] 1.1 A conformance fixture, today's behavior, with a table whose rows hold `\|` in text and in inline code and `&#124;`, `&vert;`, and `&#x7C;` in text: on the JVM and Node, every text and code piece has a line, `\|` stays literal in code, and the references decode to `|` (the design's three unverified assumptions). Record the result in the design; if one fails, stop and revise decision 2 with the owner

## 2. Tables in the Markdown facade

- [ ] 2.1 The fixtures for every `markdown-input` scenario of this change (decision 5), each agreeing with GitHub's renderer (`gh api markdown -f mode=gfm`, the result noted in the fixture's name or comment), all failing today
- [ ] 2.2 The pipe tokenizer and alignment (decision 2), pure and shared, with unit tests and the round-trip property; the shared line splitter moved from the Node parser (decision 3), with the Node parser using it unchanged in behavior
- [ ] 2.3 `Tables` (decision 1) and `Markdown.read` with `MarkdownText` and `MarkdownWarning.ExtraCells` (decision 3); `Markdown.sentences` unchanged for its callers. The fixtures from 2.1 pass on the JVM and Node, and every existing conformance fixture still passes
- [ ] 2.4 Coverage of `ears.markdown` stays at its minimums (`./mill ears.markdown.scoverage.validateCoverageMinimums` after a clean slate)

## 3. Warnings in the CLI

- [ ] 3.1 Tests first, for each `ears-cli` scenario of this change: the text line, line order with findings, the JSON `warnings` array and its empty form, exit status 0 for a warning alone, and the WARN log entry
- [ ] 3.2 `MarkdownInput` returns warnings; `Checker` carries and writes them (decision 4); the effect code logs them. `./mill ears.cli.test ears.cli.js.test` passes
- [ ] 3.3 `scripts/cli-cases`: a Markdown table case in text and JSON output, with a finding and an extra-cells row (status 1); `sh -n`, `shellcheck`, and `scripts/check-npm-cli` pass

## 4. Documents

- [ ] 4.1 `docs/json-output.md`: the `warnings` field, its object, the `table-extra-cells` kind, and that adding it keeps version 1; README: what the Markdown reader reads (tables, cell by cell) and the warning; CHANGELOG's Unreleased (Added: tables read cell by cell, the warning; Fixed: a table no longer reads as one sentence or hides a requirement after a paragraph); the roadmap (item 1 done when the theme merges) and the work log. `prettier --check`, and `ears.releaseNotes.run check CHANGELOG.md`

## 5. Checks, review, commits

- [ ] 5.1 Run the gate (CLAUDE.md's list, Scala Native included) and report coverage from `scripts/coverage-records`
- [ ] 5.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix
- [ ] 5.3 Commit (plan, then implementation); the owner pushes; the theme's draft PR runs CI. Report the run's result and minutes, from the records
- [ ] 5.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- After CI passes and verify is clean, archive the change (syncing `markdown-input` and `ears-cli`).
