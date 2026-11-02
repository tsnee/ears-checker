# Tasks

Existing tests stay unchanged except the expected values of the three
`SettingsSuite` parsing tests, which become `NonEmptyList.one(...)`
(task 1.2). Any other edit to an existing test: stop and ask first.

## 1. Settings: one per file (test-first)

- [x] 1.1 Add `SettingsSuite` tests: two file arguments give two `Settings` in argument order, both carrying the shared options; no file argument gives one `Settings` with no file; verify they fail to compile against today's `parse`
- [x] 1.2 Change `Settings.parse` to return `Either[Usage, NonEmptyList[Settings]]` with `Opts.arguments[String]("file").orEmpty`, and wrap the three existing parsing tests' expected values in `NonEmptyList.one`; verify `./mill ears.cli.test` passes

## 2. Checker: combining inputs (test-first)

- [x] 2.1 Add `CheckerSuite` tests for the combined `outcome` and the public `candidates`, covering the `ears-cli` scenarios that don't need real files: two plain-text inputs give two checked requirements; findings follow input order ("b.txt" before "a.txt"); each input is classified under its own profile; a `Left` gives exit status 2, its message on standard error and nothing on standard output; JSON output is one document whose `checked` sums every input; verify they fail to compile
- [x] 2.2 Make `Checker.candidates` public, implement the combined `Checker.outcome`, and make the single-input `outcome` delegate to it (design Decision 2); verify `./mill ears.cli.test` passes with every existing `CheckerSuite` test unchanged

## 3. Main: reading every file (test-first)

- [x] 3.1 Add `MainSuite` tests with real temporary files: "notes.md" and "change.json" together are read as Markdown and OpenSpec JSON; two missing files give a message naming only the first; a failing file followed by a missing one exits 2 with nothing on standard output; an unparsable JSON file followed by a missing one names only the JSON file; verify they fail
- [x] 3.2 Read and parse every file in order, stopping at the first failure, standard input only when no file is given, and update `Main`'s scaladoc usage line (design Decisions 3 and 5); verify `./mill ears.cli.test` passes
- [x] 3.3 Run `./mill ears.cli.assembly` and the jar by hand on a failing file followed by a missing one, and on two passing files; verify the outputs and exit statuses 2 and 0 match the spec

## 4. Check script: one checker run

- [x] 4.1 Rewrite the EARS step of `scripts/check-openspec` to write each change and spec to a temporary directory and run the checker once (design Decision 4); verify it passes on the repository and, with a temporary change whose requirement uses "MUST", fails with the weak-modal finding while still reporting nothing else wrong, then remove that change
- [x] 4.2 Count the checker's JVM starts during a run (for example with `ps` sampling or a wrapper on `java` in a scratch copy) and verify there is exactly one; trigger the Claude Code hook with an edit under `openspec/` and verify it still runs cleanly in the sandbox (design risk on `mktemp`)

## 5. Integration checks and docs

- [x] 5.1 Run `./mill __.test`, `./mill __.checkFormat` and `scripts/check-openspec`; verify all pass with no warnings
- [x] 5.2 Update `docs/status.md` (drop the one-JVM-per-input limitation and the batching item; packaging is next) and add a dated line to `docs/worklog.md`; verify by reading both with `mq`

## Workflow follow-up

- Commit the plan, then the implementation, as separate commits after review.
- Verify, then archive, syncing the `ears-cli` and `openspec-check` deltas into the main specs.
