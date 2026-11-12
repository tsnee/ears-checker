# Design

## Context

On 2026-11-11 three reviewers, each in its own git worktree, read every test suite and `scripts/cli-cases`, and for each test they suspected made a small break ("mutation") in the production code the test claims to cover, ran that module's JVM tests, and reverted. The owner approved every fix they proposed (groups A to D). Claude reran two of the claims itself: with the npm API's precondition span set to `SourceSpan(0, 0)` all 14 `ears.npm` tests still pass, and zio-test 2.1.27's `TestConsole.clearOutput` clears only `output`, while `clearOutputErr` clears `errOutput` (read in its source jar).

The review's evidence, by finding (file:line as of 04cc321):

| Finding | Mutation | Result today |
| --- | --- | --- |
| A1 release-notes `MainSuite.scala:81`, `:101` | `notes` reads the changelog silently; `badArguments` prints the usage only for no arguments | both pass |
| A2 `SourceLinesSuite.scala:86`, `:139`, `:151` | escape matching in `SourceLines.matchedFrom` switched off | all three pass |
| A3 `HelpersSuite.scala:149` | `firstLoaded` tries every candidate and returns the last success | passes |
| A4 `LinkCheckSuite.scala:124`, `:129` | `isPublicThroughout` checks only the symbol and its direct owner | both pass |
| A5 `CliSuite.scala:166`, `JvmReadingSuite.scala:83` | the bootstrap writes nothing; `Main.bootstrap` discards lines | both pass |
| A6 `MainSuite.scala:290` | the logged error becomes `String.valueOf(error.getMessage)` (`"null"`) | passes |
| A7 `SettingsSuite.scala:59` | the command drops `--output` | passes (compares `parse` with itself) |
| B1 `ApiSuite.scala:208`, `LawsSuite.scala:48` | the precondition and unwanted-trigger spans emptied (npm); the response and feature spans shrunk (library) | npm: pass; library: caught only by the seed's 1 to 2% of valid sentences |
| B2 `ModelSuite.scala:9` | `case (Some(_), None, None)` widened to `(Some(_), _, None)` | every `ears.test` suite passes |
| B3 `CheckerSuite.scala:165`, `MainSuite.scala:148` | the OpenSpec parse error made empty | both pass |
| B4 `ExtractSuite.scala:60` | only ADDED deltas checked | every CLI test passes |
| B5 `MainSuite.scala:263` | the cause's value emptied | passes |
| B6 `CheckerSuite.scala:72` | only standard input's first line read | passes |
| B7 `LoggingSuite.scala:80` | line-separator escaping off | the property passes; only the witness at `:69` fails |
| B8 `DiagnosticSuite.scala:52` | (by reading) the assertion holds when the sentence is accepted | — |
| B9 `SourceLinesSuite.scala:184` | the search window made the whole source | passes, 14 s instead of 0.6 s |
| B10 `LinkCheckSuite.scala:277` | the program's printed summary changed | passes |

## Goals / Non-Goals

**Goals:**

- Every test the review proved can't fail for its stated reason fails against that mutation after this change, and passes on the real code.
- Duplicates go, so each behavior has one test that owns it.
- The comparison cases can tell builds apart on OpenSpec findings and on `--verbose`.

**Non-Goals:**

- New behavior. The OpenSpec parse error not naming its file is a product gap the review found; it's on the roadmap (item 3), not here.
- A mutation-testing tool in the build. The mutations here are run by hand, once, as each test's proof; a tool (for Scala 3 none is current enough to rely on) is a separate question.
- A timeout on the long-paragraph test (B9): it would be a source of flakiness, so the test is named as a speed guard instead.

## Decisions

### 1. Each strengthened test is proved against its mutation, test-first

For each test in groups A and B: apply the review's mutation, change the test, and see it fail; revert the mutation and see it pass. That's the order a new test takes (fail first, then pass), with the mutation standing in for the missing behavior. The task records each mutation and both results, so verify can check them. Mutations are never committed.

### 2. One generator of valid requirements, in `Generators`

`ClassifierSuite` builds valid requirements (every template, both profiles) from its own `vocabulary` and `phrase`, near copies of `Generators.phrase`. They move into `ears/test`'s `Generators` as `validRequirement` (a `Requirement` and its rendered text under a profile), used by `ClassifierSuite`, by `LawsSuite`'s span law, and by `ApiSuite`'s read-back property, whose input becomes `Gen.oneOf(valid, damagedSentence, Gen.string)`. `ApiSuite` and `FacadeSuite`'s duplicated `damagedSentenceOrText` moves there too. `ears.npm.test` already depends on `ears.test`, and `ears.npm.js.test` on `ears.js.test`, so both see it.

The span law drops `Gen.string`: the skeleton's totality test already owns arbitrary text, and the law is about trees, which `Gen.string` never yields.

### 3. A seam for the JVM's log stream

`Main.bootstrap` writes log lines to standard error through a UTF-8 `PrintStream` it makes itself, so no test can see the bytes. It becomes `bootstrapTo(err: OutputStream)`, with `bootstrap = bootstrapTo(FileOutputStream(FileDescriptor.err))`. The test logs `"é"` into a `ByteArrayOutputStream` and expects the line to end in `0xc3 0xa9 0x0a`: the UTF-8 log path, untested until now. The shared `CliSuite` bootstrap test is removed; `MainSuite`'s level test already covers the shared logger.

### 4. Duplicates: the stronger test stays

Where two tests check the same path with the same input, the one with more assertions (usually a whole-document JSON comparison) stays and the other goes, with any assertion only it had folded in (`CheckerSuite:247`'s exit status into `:364`; `DiagnosticSuite:94`'s kinds into `:100`). Pairs that differ only in a default (`CheckerSuite:92`/`:99`, `:85`/`:124`) merge into one test that checks both. `MainSuite`'s tests that pass input through `Main.reading(ZIO.succeed(x))` test shared behavior: those with a twin in `CliSuite` go; the rest move there, so they also run on Node.

Kept on purpose (the review's "deliberate layering"): `SkeletonSuite` beside `DiagnosticSuite`, `FacadeSuite` beside `ApiSuite`, `consumer.ts` beside `FacadeSuite`, the conformance fixtures that guard parser differences, `Utf8Suite`'s byte-order mark beside the conformance fixture's, and `LocaleSuite` and `InlineCodeRoundTripSuite` in the CLI module. The conformance suite's parser-name test, meaningful only on Scala Native, gets a comment saying so.

### 5. Comparison cases with findings

`openspec-text` compares builds on this repository's specs, which `check-openspec` holds to zero findings, so its output is always empty. It's replaced by inputs written in `scripts/cli-cases` like its other inputs: a change in `openspec show --json`'s shape with failing ADDED and MODIFIED deltas and a REMOVED one (the shape `ExtractSuite` uses), and a spec with a failing requirement, each in text and JSON output. `openspec-json` on the real specs stays, as the large case. `openspec-byte-order-mark` adds `--output json`, so the `checked` count is compared, not just the exit status. The OpenSpec cases' expected status becomes `0` (or `1` for the new failing fixtures). `verbose` also requires a `DEBUG` line in the subject's log, which the comparison strips.

## Risks / Trade-offs

- **Coverage may drop slightly** as duplicates go; a duplicate adds no statements of its own, so it shouldn't, and the gate's minimums decide.
- **The seed is fixed** (zio-test's), so a property's reach is the same every run; with the new valid-requirement generator as one of three choices, roughly a third of draws should yield a tree, against about 2% today; the task samples the generators to measure it, rather than trusting that estimate.
- **Moving tests between suites** changes their names in test reports; nothing depends on them.
- **The `Main` seam** is a production change for testability: the program's behavior is the same, and the seam is the global rule's "a seam for each effectful service".
