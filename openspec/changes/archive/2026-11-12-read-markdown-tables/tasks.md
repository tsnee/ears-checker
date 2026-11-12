# Tasks

All work is on branch `batch-tables`. The owner pushes from their own shell. Test-first: each behavior's test or fixture is written and seen failing before the code that makes it pass. Scala Native runs in the gate (task 5.1) and in CI; during tasks 1 to 3 the JVM and Node runs are enough (`./mill ears.markdown.test ears.markdown.js.test`, `./mill ears.cli.test ears.cli.js.test`).

## 1. The assumptions, first

- [x] 1.1 A conformance fixture, today's behavior, with a table whose rows hold `\|` in text and in inline code and `&#124;`, `&vert;`, and `&#x7C;` in text: on the JVM and Node, every text and code piece has a line, `\|` stays literal in code, and the references decode to `|` (the design's three unverified assumptions). Record the result in the design; if one fails, stop and revise decision 2 with the owner

## 2. Tables in the Markdown facade

- [x] 2.1 The fixtures for every `markdown-input` scenario of this change (decision 5), each agreeing with GitHub's renderer (`gh api markdown -f mode=gfm`, the result noted in the fixture's name or comment), all failing today. (Done: 35 new fixtures, checked on GitHub on 2026-11-12; 33 failed against a stub of today's behavior. The two that passed, "pipes with no delimiter row" and "a delimiter row with a different count", describe today's behavior and guard it.)
- [x] 2.2 The pipe tokenizer and alignment (decision 2), pure and shared, with unit tests and the round-trip property; the shared line splitter moved from the Node parser (decision 3), with the Node parser using it unchanged in behavior. (`PipesSuite` couldn't compile before `Pipes` under `-Werror`, so it wasn't seen failing first; instead, with escapes read as separators 5 of its 17 tests fail, with references unrecognized 4, and with the fallback ignoring `\|` in code 1.)
- [x] 2.3 `Tables` (decision 1) and `Markdown.read` with `MarkdownText` and `MarkdownWarning.ExtraCells` (decision 3); `Markdown.sentences` unchanged for its callers. The fixtures from 2.1 pass on the JVM and Node, and every existing conformance fixture still passes
- [x] 2.4 Coverage of `ears.markdown` stays at its minimums (`./mill ears.markdown.scoverage.validateCoverageMinimums` after a clean slate)
- [x] 2.5 (Added after the code review, 2026-11-12: the owner chose to match GitHub.) Raw rows, re-parsed cells (decision 1): each parser reports a paragraph's last line; the container-prefix stripper and the raw row splitter, pure and shared, with unit tests and the round-trip property; the cells and earlier lines re-parsed in one extra document per source document, behind the original, with the `x ` prefix. Fixtures first for the new scenarios (cells as inline text, references in cells, rows before inline text) and decision 5's new cases, each checked on GitHub's renderer and seen failing; then every fixture passes, the former known-difference fixtures now agreeing with GitHub, on the JVM and Node; `Pipes` (and `PipesSuite`, from 2.2) replaced by `Rows` (and `RowsSuite`); coverage at its minimums
- [x] 2.6 (Added after the second code review, 2026-11-12; the owner approved all six fixes.) The table ends where GitHub ends it, and the rest of the paragraph is read as the Markdown it is (decision 1, step 3); an extra cell warns only when it has text; a paragraph starts at its real first line, after definitions; the extra parse appends the original only when a segment holds `[`, reuses the loaded parser (cmark once on Native), and is skipped for documents with no `|`; list markers use ASCII digits and space or tab. Fixtures first for the spec's new scenarios (table end, an empty extra cell) and each review case (lazy lines in a quote and a list item, `2.` and an empty `- ` item after a table, an indented code line, an HTML block line, a delimiter row indented four, an image-only header, a definition then text on the paragraph's first lines), each checked on GitHub and seen failing; then all pass on the JVM and Node; coverage at its minimums

## 3. Warnings in the CLI

- [x] 3.1 Tests first, for each `ears-cli` scenario of this change: the text line, line order with findings, the JSON `warnings` array and its empty form, exit status 0 for a warning alone, and the WARN log entry
- [x] 3.2 `MarkdownInput` returns warnings; `Checker` carries and writes them (decision 4); the effect code logs them. `./mill ears.cli.test ears.cli.js.test` passes. (Five existing whole-document JSON tests in `CheckerSuite` contradicted the spec's always-present `warnings` field; with the owner's approval, each expected document gained `"warnings":[]`.)
- [x] 3.3 `scripts/cli-cases`: a Markdown table case in text and JSON output, with a finding and an extra-cells row (status 1); `sh -n`, `shellcheck`, and `scripts/check-npm-cli` pass

## 4. Documents

- [x] 4.1 `docs/json-output.md`: the `warnings` field, its object, the `table-extra-cells` kind, and that adding it keeps version 1; README: what the Markdown reader reads (tables, cell by cell) and the warning; CHANGELOG's Unreleased (Added: tables read cell by cell, the warning; Fixed: a table no longer reads as one sentence or hides a requirement after a paragraph); the roadmap (item 1 done when the theme merges) and the work log. `prettier --check`, and `ears.releaseNotes.run check CHANGELOG.md`

## 5. Checks, review, commits

- [x] 5.1 Run the gate (CLAUDE.md's list, Scala Native included) and report coverage from `scripts/coverage-records`
- [x] 5.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix. (Three reviews: the first's ten findings led to the owner choosing to match GitHub, and the rework (2.5); the second's six were all fixed (2.6); between them, the owner approved the test edits each needed.)
- [x] 5.3 Commit (plan, then implementation); the owner pushes; the theme's draft PR runs CI. Report the run's result and minutes, from the records. (PR #25's run 43222897883 on 1ebebc3, after merging main, passed all 10 jobs in 42 minutes.)
- [x] 5.4 Run `/openspec-verify-change` and report it before archiving. (Run in a subagent on 2026-11-12: 18 of 18 requirements implemented, 28 of 28 scenarios covered, the design followed, no warnings; its three wording suggestions, in this spec, the proposal, and these tasks, were fixed before archiving.)

## Workflow follow-up

- After CI passes and verify is clean, archive the change (syncing `markdown-input` and `ears-cli`).
