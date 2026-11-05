# Tasks

All work is on branch `add-markdown-facade`, in one pull request that also carries the archive.
The owner pushes from their own shell; Claude opens the pull request and reads runs with `gh`.
Compile, test, and format through Metals (BSP) where it can; shared test sources, `forkEnv`,
the npm and native toolchains, `show`, the coverage check, and the final CI-equivalent run go
through `./mill`. cmark is installed locally (0.31.2, Homebrew).

## 1. The facade on the JVM (test-first)

- [x] 1.1 Add `ears.markdown` to `build.mill` (design Decision 2): `Base with Covered`, sources `ears/markdown/src` and `src-jvm`, commonmark-java, not `Published`, with its test module. Run `import-build` and verify it in `list-modules`
- [x] 1.2 Write `MarkdownConformanceSuite` in `ears/markdown/test/src` (design Decisions 1 and 3, `markdown-input` delta): the 5 Markdown cases from `ExtractSuite`, then a sentence after a soft break inside one mdast `text` node, code spans across lines, escapes and entities, nested lists, lazy continuation lines, HTML blocks, ATX and setext headings, block quotes, tabs, and several sentences on several lines of one paragraph. Expected results are what today's `MarkdownInput` gives; record each case where that seems wrong rather than changing it. Verify it fails to compile before 1.3
- [x] 1.3 Implement `Piece`, `Paragraph`, `Sentence`, `Extraction`, `Markdown`, and the JVM `MarkdownParser` (commonmark-java), moving the logic from `MarkdownInput`. Verify the suite passes, and measure coverage and set `ears.markdown`'s minimums near what it reaches
- [x] 1.4 Point `ears.cli` at the shared and JVM sources and make `MarkdownInput` a call to `Markdown.sentences` (design Decision 2). Move `ExtractSuite`'s Markdown tests into the conformance suite, asking the owner first, since they're existing tests. Verify every `ears.cli` test passes, `./mill show ears.cli.publishArtifacts` and the POM's dependencies are unchanged, and the assembly jar gives the same output as `main`'s on this repository's own Markdown files
  - 2026-11-05: every CLI test passed before the test move. The jar's JSON output on all 88 tracked Markdown files (386 sentences checked, 501 findings) is byte-identical to the jar built before the change; the POM's dependencies and the four published artifacts are unchanged. The owner chose to replace `ExtractSuite`'s 5 Markdown tests, now in the conformance suite word for word, with one test that `Candidate.fromMarkdown` puts the file name and line into each candidate. Compiling the facade in made the CLI's coverage measure it again (92.74% / 91.18%, below 96 / 95); `-coverage-exclude-files` leaves it to `ears.markdown`, and the CLI is at 97.02% / 98.25%

## 2. Node

- [x] 2.1 Add `ears/markdown/js/package.json` and `package-lock.json` pinning `mdast-util-from-markdown` 2.1.0, a Mill task that runs `npm ci` into its `Task.dest` with `--cache` there, and `ears.markdown.js` with its Node test module (design Decisions 2 and 4)
- [x] 2.2 Implement the Node `MarkdownParser` over mdast (design Decision 4). Include design Decision 3's two adjustments (split `text` at `\n`, code-span line endings to spaces). Verify the conformance suite passes on Node; for any fixture that differs, stop and report it with both parsers' output
  - 2026-11-05: 23 fixtures pass on Node and on the JVM. Probing first found two more mdast differences, beyond the plan's two: an image's alt text is an attribute (today's reader includes it as text, keyword and all), and inline HTML tags are `html` nodes (today's reader drops the tags and keeps the text between them); mdast also keeps CRLF as `\r\n` in text values. The parser handles all of them, and three fixtures were added for them (image alt text, inline HTML, a reference link), with today's results. `npm ci` runs in the Mill task `ears.markdown.js.npmDependencies`, into its own directory

## 3. Scala Native

- [x] 3.1 Add `ears.markdown.native` (Scala Native 0.5.12, linking flags from `pkg-config`, with a clear failure when cmark is missing) and its test module (design Decisions 2 and 4)
- [x] 3.2 Implement the native `MarkdownParser` over cmark (design Decision 4). Verify the conformance suite passes natively; for any fixture that differs, stop and report it with both parsers' output. Record the native test run's link and run time
  - 2026-11-05: 23 fixtures pass natively (cmark 0.31.2), so the facade gives the same results on all three platforms. The first link failed for want of the Scala Native test interface: `ZioSpecs` replaced the test module's `mvnDeps` instead of adding to them, which also dropped what Mill's `ScalaNativeTests` adds; it now adds. One fixture first failed natively (a code span across lines: the next sentence on line 1, not 2). A C probe showed cmark's inline start lines are wrong after such a span unless parsing with `CMARK_OPT_SOURCEPOS`, so the binding uses it (design Decision 4). Link and run: 24 s the first time, 12 s on a relink

## 4. CI and docs

- [x] 4.1 Add the `libcmark-dev` install to `check` in `.github/workflows/ci.yml` (design Decision 5). Verify with `yq`
- [x] 4.2 Update `README.md`, `CLAUDE.md`, and `docs/roadmap.md` (design Decision 6). Verify with `mq`

## 5. Checks, review, and pull request

- [x] 5.1 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, and report the coverage. Add a dated line to `docs/worklog.md`
  - 2026-11-05: all pass; 378 tests. Coverage: `ears` 95.73% / 92.04%, `ears.cli` 97.02% / 98.25%, `ears.npm` 100% / 100%, `ears.linkCheck` 97.28% / 100%, `ears.markdown` 100% / 100%
- [x] 5.2 Run `/code-review` in a subagent on the branch's diff, and let the owner choose which findings to fix
  - 2026-11-05: the review ran about 75 more inputs on all three platforms, built cmark 0.30.2 to check CI's version (all fixtures pass on it), and found 8 issues; the owner chose to fix all (design Decision 3, revised).
    1. cmark's inline lines were wrong after a backslash hard break, after a link whose title is on the next line, and in a paragraph right after a reference definition. `CmarkLines` (shared) now corrects them from the paragraph's end line, the breaks, and relative lines.
    2. mdast `imageReference` and alt text across lines are now handled.
    3. Decoded `&#10;` and `&#13;` are no longer taken for line endings (`LineEndings`, from mdast's source offsets).
    4. A leading byte-order mark is dropped on every platform.
    5. Deep nesting no longer overflows the stack on Node or natively (`Walk`). The `markdown-input` delta now limits "same results" to documents every parser follows and adds "Deep nesting" (no failure).
    6. npm's errors show (`--loglevel=error`); the network need is documented.
    7. A missing `pkg-config` gives the friendly message, and `pkg-config`'s flags come first, so they choose the cmark linked.
    8. The input is UTF-8-encoded once, and the document freed in a `finally`.

    13 new fixtures and 12 helper tests; 459 tests pass, 50 on each platform for the facade, and `ears.markdown` is at 100% / 100%. The CLI's output on the 88 Markdown files is still byte-identical
- [x] 5.3 Commit; the owner pushes. Open the pull request with `gh`, recommending squash merge. Report the CI run's result and minutes, and the native tests' share of them. After CI passes, archive the change on the same branch (creating `markdown-input` and syncing `continuous-integration`) for the owner to push and merge
  - 2026-11-05: committed as b192fbf, pushed by the owner; PR #15. CI run 42033641710 passed: `check` 3m 32s, `check-jdk17` 1m 29s. Ubuntu's cmark installed as 0.30.2 in 8 s; the native test binary linked in 31 s, and the Test step took 77 s, up from 30–47 s before, for the Node and native runs. Archived on the same branch, syncing `test-coverage` too
  - 2026-11-05: on the owner's question whether the explicit-stack walk traded a stack overflow for running out of memory: no. Measured on the JVM, memory is linear in the input and the parser's: commonmark-java's parse alone used 993 MB for 1,000,000 levels of emphasis (5.8 MB), with 3 inline nodes left for the walk; 1,000,000 nested block quotes (1.9 MB) used 67 MB in all. A nesting limit in our code can't bound that, since the parser has run by then; an input-size limit before parsing could, which matters only if the CLI checks untrusted input
