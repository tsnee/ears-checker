# Tasks

Each implementation task is test-first: write the zio-test cases named in the task (from the
spec scenarios), see them fail, then write the code until they pass. "Verify" names the command
that must succeed with no warnings.

## 1. Modules and dependencies

- [x] 1.1 Add `ears` (pure warts, depends on cats-parse 1.1.0) and its zio-test module to `build.mill`; verify `./mill ears.compile + ears.test` succeeds with an empty placeholder suite
- [x] 1.2 Add `ears.cli` (ZIO-safe warts; ZIO 2.1.27, decline 2.6.2, zio-json 1.1.1, commonmark 0.31.0; depends on `ears`) and its zio-test module; verify `./mill ears.cli.compile + ears.cli.test` succeeds
- [x] 1.3 Add `ears.js`: a Scala.js 1.22.0 module that compiles the same sources as `ears` with the same settings, with a test module that runs the same zio-test suites on Node; verify `./mill ears.js.test` runs the placeholder suite on Node
- [x] 1.4 Add the three modules to the layout section of `CLAUDE.md`; verify `./mill __.compile` and `./mill __.checkFormat` succeed

## 2. Data model and rendering

- [x] 2.1 Test-first: `Span`, `Spanned`, `Requirement`, `Trigger`, `Template`, `Profile`, and the template derivation (no clauses → ubiquitous, one → its own template, two or more → complex); verify `./mill ears.test + ears.js.test`
- [x] 2.2 Test-first: canonical `render` for each spec scenario tree under both profiles (capitalized first keyword, `, then` after If, profile keyword, final period); verify `./mill ears.test + ears.js.test`

## 3. Skeleton parser

- [x] 3.1 Test-first: the lenient skeleton parse never fails and splits each spec scenario sentence into clauses, article, system, modal word, response, and trailing punctuation with correct spans, including the "Source spans" scenario (trigger at offsets 5 to 17); verify `./mill ears.test + ears.js.test`
- [x] 3.2 Test-first: totality property over generated strings (no exception; tree or at least one diagnostic once classification exists, skeleton result for now); verify `./mill ears.test + ears.js.test`

## 4. Template classification

- [x] 4.1 Test-first: the six template scenarios (ubiquitous, event-driven, state-driven, unwanted behavior, optional feature, both complex scenarios) classify to the expected trees; verify `./mill ears.test + ears.js.test`
- [x] 4.2 Test-first: OpenSpec and classic profile scenarios, and the "Total classification" empty-input scenario; verify `./mill ears.test + ears.js.test`
- [x] 4.3 Test-first: round-trip property (`classify(render(r))` equals `r` ignoring spans) with generated requirements; verify `./mill ears.test + ears.js.test`

## 5. Modal diagnostics

- [x] 5.1 Test-first: missing-modal, wrong-case (with fix), multiple-responses, weak-modal (with fix, including uppercase MUST), and empty-response, each with its scenario as the witness; verify `./mill ears.test + ears.js.test`

## 6. Clause diagnostics

- [x] 6.1 Test-first: clause-order (with reordering fix), repeated-clause, conflicting-triggers, missing-then (with fix), missing-comma (with fix), and missing-article (with fix), each with its scenario as the witness; verify `./mill ears.test + ears.js.test`

## 7. Sentence diagnostics and laws

- [x] 7.1 Test-first: missing-period (with fix), multiple-sentences, unrecognized-structure, and the "Independent problems" scenario (wrong case plus missing period reported together); verify `./mill ears.test + ears.js.test`
- [x] 7.2 Test-first: fix-soundness property (applying a fix removes that diagnostic kind) and span-sanity property (spans lie within the input); verify `./mill ears.test + ears.js.test`
- [x] 7.3 Classify every requirement statement in this change's three spec files and confirm each yields a tree with no diagnostics, as a JVM-only zio-test in `ears.cli.test` (which can read files); verify `./mill ears.cli.test`

## 8. CLI core

- [x] 8.1 Test-first: requirement extraction for plain text, Markdown (code skipped, sentences split at a period followed by whitespace, line numbers), OpenSpec change JSON (ADDED and MODIFIED only), and OpenSpec spec JSON, using the spec scenarios; verify `./mill ears.cli.test`
- [x] 8.2 Test-first: argument parsing with decline (format and profile options, format inference from `.md` and `.json`, stdin when no file) and default-profile rules; verify `./mill ears.cli.test`
- [x] 8.3 Test-first: findings with locations, text output (one line per finding, default), JSON output (one document, empty list when clean), and exit statuses 0, 1, and 2 with the error message on standard error; verify `./mill ears.cli.test`

## 9. CLI program

- [x] 9.1 Wire the ZIO main program and build the assembly; add one end-to-end test per input format that runs the program on a temporary file and checks output and exit status; verify `./mill ears.cli.test` and `./mill show ears.cli.assembly`

## 10. OpenSpec check

- [x] 10.1 Extend `scripts/check-openspec` to build the assembly once and run the CLI with `--format openspec` on every active change and every spec; verify it passes on the repo, including this change
- [x] 10.2 In a temporary copy of `openspec/`, add a change whose requirement reads "The scheduler MUST list upcoming events." and confirm the script fails with a weak-modal finding, and that `scripts/claude-openspec-hook` exits 2 with that finding in its message
- [x] 10.3 Update the Workflow section of `CLAUDE.md` to say the hook enforces EARS, add a dated line to `docs/worklog.md`, and update `docs/status.md`; verify `./mill __.test` and `scripts/check-openspec` both succeed

## Workflow follow-up

- Review the change, then archive it with `/opsx:archive`; archiving creates the three main specs under `openspec/specs/`.
- After archiving, run `scripts/check-openspec` again so the new main specs are EARS-checked too.
