# Tasks

All work is on branch `batch-tests`. The owner pushes from their own shell. For every test changed in groups 1 to 4, decision 1 applies: with the review's mutation in place the changed test fails, and without it the test passes; `evidence.md`, beside these tasks, records both runs (module, test, result). Mutations are never committed. Only JVM test modules run during these tasks (`./mill ears.test`, `ears.npm.test`, `ears.cli.test`, `ears.markdown.test`, `ears.linkCheck.test`, `ears.releaseNotes.test`); the full gate runs in group 6.

## 1. The classifier library and the npm package

- [x] 1.1 `Generators.validRequirement` (decision 2), moved from `ClassifierSuite` with its vocabulary, and `damagedSentenceOrText` moved from `ApiSuite` and `FacadeSuite`; `ClassifierSuite`'s round trip uses it unchanged. A throwaway sample of 2,000 draws reports the share that classify, before and after, for each of the two properties below
- [x] 1.2 `LawsSuite`'s span law on valid requirements and `damagedSentence` (no `Gen.string`): fails with the response span shrunk by one, and with the feature span shrunk by one
- [x] 1.3 `ApiSuite`'s read-back property on `oneOf(valid, damagedSentence, Gen.string)`: fails with the precondition span emptied, and with the unwanted trigger's span emptied
- [x] 1.4 `ModelSuite`: Where + While is Complex; fails with `case (Some(_), None, None)` widened to `(Some(_), _, None)`
- [x] 1.5 `DiagnosticSuite` "a weak modal is not also a missing modal" requires the weak-modal finding; fails with `should` accepted as the modal, so the sentence classifies
- [x] 1.6 Remove the duplicates: `ClassifierSuite` "the OpenSpec profile accepts the uppercase keyword"; `DiagnosticSuite` "missing then … not the last clause", its two kinds folded into "applying every fix together" (after review, a test of their own instead: decision 4); `RenderSuite`'s classic complex test; `ModelSuite`'s profile keyword tests. `./mill ears.test ears.npm.test` passes

## 2. The CLI

- [x] 2.1 `Main.bootstrapTo(err: OutputStream)` (decision 3) and its test of the UTF-8 bytes of a logged `é`: fails with `bootstrapTo` discarding lines; remove `CliSuite` "bootstrap installs its logger" and replace `JvmReadingSuite`'s with the new test
- [x] 2.2 `MainSuite` "an error with no message…" checks the logged value `java.lang.IllegalStateException`: fails with `String.valueOf(error.getMessage)`
- [x] 2.3 `SettingsSuite` "without --verbose…" compares with a literal `NonEmptyList`: fails with the command dropping `--output`
- [x] 2.4 The parse-error tests (`CheckerSuite` malformed JSON, `MainSuite` unparsable file before a missing one) check the message starts `Not OpenSpec change or spec JSON:`: fail with the message emptied
- [x] 2.5 `ExtractSuite`'s change has a MODIFIED delta whose candidate is expected: fails with only ADDED checked
- [x] 2.6 `MainSuite` "an entry's cause is written with it" checks `disk full`: fails with the cause emptied
- [x] 2.7 `CheckerSuite` standard input with two lines, both located: fails with only the first line read
- [x] 2.8 `LoggingSuite`'s one-line property draws from `VisibleSuite`'s character mix (shared, not copied): fails with line-separator escaping off
- [x] 2.9 Remove and merge the duplicates (decision 4): `CheckerSuite` `:103`, `:114`, `:171`, `:247` (its exit status into `:364`), `:343`, and merge `:85` with `:124`, `:92` with `:99`; `CliSuite:74`; `MainSuite` `:91`, `:123`, `:138`, `:178`, `:309`; move `MainSuite` `:132`, `:168`, `:189`, `:196` into `CliSuite`. `./mill ears.cli.test` passes, and the Node run of the moved tests passes in the gate

## 3. The Markdown facade, the link check, and the release notes

- [x] 3.1 Release-notes `MainSuite` clears standard error with standard output between runs (a helper used by every run): "a file that can't be read" fails with `notes` reading silently; "bad arguments" fails with the usage printed only for no arguments
- [x] 3.2 `SourceLinesSuite`: the escaped-first-word case after a hidden line ending replaces `:139` and `:151`, and fails with escape matching off; `:86` is renamed to what it shows; a conformance fixture for the same case (`"Intro. <span\nclass=x>\\*b\\* shall c.\n"` → line 2) runs on all three platforms in the gate
- [x] 3.3 `HelpersSuite` first candidate: two succeeding candidates and a record of what was tried (an `AtomicReference`, no `var`): fails with `firstLoaded` trying every candidate
- [x] 3.4 `LinkCheckSuite`: a public object inside a private one yields no operation: fails with `isPublicThroughout` checking only the direct owner; the duplicate private-owner test goes
- [x] 3.5 `LinkCheckSuite` "the line the generator prints matches the one the program will print" derives the line from the program: fails with the program's summary text changed
- [x] 3.6 `SourceLinesSuite`'s long paragraph renamed as a speed guard, with a comment on the bounded window; the conformance suite's parser-name test gets a comment that it's meaningful on Scala Native; `ChangelogSuite` "a misnamed Unreleased is named" removed, and the malformed-headings test keeps one version-shaped heading. `./mill ears.markdown.test ears.linkCheck.test ears.releaseNotes.test` passes

## 4. The comparison cases

- [x] 4.1 `scripts/cli-cases` (decision 5): `openspec-text` replaced by a fixture change (failing ADDED and MODIFIED deltas, a REMOVED one) and a fixture spec (a failing requirement), each compared in text and JSON with status 1; `openspec-byte-order-mark` with `--output json`; the real-spec cases expect 0; `verbose` requires a `DEBUG` line in the subject's log. `sh -n` and `shellcheck`; `scripts/check-npm-cli` and `scripts/check-native-cli` pass. Each new fixture case's standard output names findings in its format (not empty), so a build that drops requirements would differ

## 5. Records and docs

- [ ] 5.1 The work log, and the roadmap's tests item marked done when the theme merges; CI records committed with the change

## 6. Checks, review, commits

- [x] 6.1 Run the gate (CLAUDE.md's list) and report coverage from `scripts/coverage-records`, against the last records
- [x] 6.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix
- [x] 6.3 Commit (plan, then implementation); the owner pushes; the theme's draft PR runs CI. Report the run's result and minutes, from the records
- [x] 6.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- After CI passes and verify is clean, archive the change (no specs to sync).
