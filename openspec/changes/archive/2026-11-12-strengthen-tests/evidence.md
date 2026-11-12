# Evidence

For each changed test, the mutation the review found, applied to production code, the test failing with it, and passing without it (decision 1); for each removed test, the test that covers it. Written as each group was applied, on 2026-11-11 (macOS, the JVM unless a run says otherwise); paths under a session scratchpad named there were working files, not kept.

## strengthen-tests, group 1: evidence (2026-11-11)

All runs on the JVM, branch `batch-tests`, working tree on top of 3def2c9. Each mutation was applied alone, the module's suite run, then reverted with `git checkout`; `git diff -- ears/src ears/npm/src` is empty at the end.

### 1.1 Share of draws that classify (Right), 2,000 draws each

Measured with a throwaway zio-test probe in `ears/test` (`Gen.runCollectN(2000)`, zio-test's TestRandom seed), deleted afterwards. Classification by `Requirement.classify`, which is what `Api.classify`'s `ok: true` mirrors.

| Property's input | Before | After |
| --- | --- | --- |
| LawsSuite span law (OpenSpec) | `oneOf(Gen.string, damagedSentence)`: 27 / 2000 (1.35%) | `oneOf(validRequirement(OpenSpec), damagedSentence)`: 1005 / 2000 (50.25%) |
| ApiSuite read-back (Classic or OpenSpec) | `oneOf(damagedSentence, Gen.string)`: 19 / 2000 (0.95%) | `oneOf(validRequirement(profile), damagedSentence, Gen.string)`: 686 / 2000 (34.3%) |

### 1.2 LawsSuite "span sanity: each tree element's span covers exactly its text" (`ears.test`)

- Mutation A, `Classifier.treeOf`: response span end shrunk by one (`response.copy(span = SourceSpan(response.span.start, response.span.end - 1))`). With it: FAILS, `mismatched.isEmpty`, `mismatched = List(Located(value = "order", span = SourceSpan(16, 20)))` (LawsSuite.scala:65). 3 passed, 1 failed.
- Mutation B, `Classifier.treeOf`: feature span end shrunk by one. With it: FAILS, `mismatched = List(Located(value = "order", span = SourceSpan(6, 10)))`. 3 passed, 1 failed.
- Without either: passes (LawsSuite 4 of 4).

### 1.3 ApiSuite "every result has the library's diagnostics, or its requirement, read back exactly" (`ears.npm.test`)

- Mutation A, `Api.scala`: `"precondition" -> requirement.precondition.fold(Value.Null)(p => clause(p.copy(span = SourceSpan(0, 0))))`. With it: FAILS after 2 iterations, shrunk input `("While order, the order shall order.", Classic)`; the decoded requirement has `precondition = Some(Located("order", SourceSpan(0, 0)))` against the library's. 13 passed, 1 failed.
- Mutation B, `Api.scala`: unwanted trigger's span emptied (`triggerOf("unwanted", text.copy(span = SourceSpan(text.span.start, text.span.start)))`). With it: FAILS after 12 iterations, shrunk input `("If order, then the order shall order.", Classic)`; decoded trigger `Unwanted(Located("order", SourceSpan(3, 3)))`. 13 passed, 1 failed.
- Without either: passes (14 of 14).

### 1.4 ModelSuite "Where and While together, with no trigger, are complex" (`ears.test`)

- Mutation, `Model.scala` `Template.of`: `case (Some(_), None, None) => OptionalFeature` widened to `case (Some(_), _, None)`. With it: FAILS, `OptionalFeature() was not equal to Complex()`. 8 passed, 1 failed.
- Without it: passes.

### 1.5 DiagnosticSuite "a weak modal is not also a missing modal" (`ears.test`)

- Mutation, `Classifier.scala`: `modalChecks` returns no modal problem for `should` (`if modal.value == "should" then None else if modal.value.equalsIgnoreCase("shall") then …`), and `treeOf` accepts `modal.value == "should"` alongside the profile's keyword, so "The scheduler should list upcoming events." classifies. With it: FAILS, `found.contains(WeakModal)`, `found = Nil` (DiagnosticSuite.scala:54). "weak modal, with a fix" fails too. 23 passed, 2 failed.
- Without it: passes.

### 1.6 Removals, and the final runs

Removed: ClassifierSuite "the OpenSpec profile accepts the uppercase keyword"; DiagnosticSuite "missing then is reported for an If clause that is not the last clause" (its `ClauseOrder` and `MissingThen` assertions folded into "applying every fix together yields a sentence with no diagnostics"); RenderSuite classic "complex: state and event"; ModelSuite's `profiles` suite (both keyword tests).

Final runs, no mutation: `ears.test` 87 passed (8+9+25+20+10+11+4), `ears.npm.test` 14 passed, `ears.js.test` 87 passed, `ears.npm.js.test` 11 passed; `./mill __.checkFormat` succeeds.

## strengthen-tests, group 2 (the CLI): evidence

Run on 2026-11-11 on branch `batch-tests`, macOS, `./mill ears.cli.test.testOnly <suite>` (JVM). Each mutation was applied with `scratchpad/mutate.sh` (sed on a copy restored from a backup taken before any mutation), the suite run, the file restored, and the suite run again. Logs: `scratchpad/<label>-mutated.log` and `scratchpad/<label>-clean.log`. Afterwards `git diff -- ears/cli/src ears/cli/js/src` is empty, and `git diff -- ears/cli/src-jvm` shows only the `bootstrapTo` seam.

### 2.1 `Main.bootstrapTo` and the UTF-8 log line

- Seam: `Main.bootstrapTo(err: => OutputStream): ZLayer[ZIOAppArgs, Nothing, Unit]`; `bootstrap = bootstrapTo(FileOutputStream(FileDescriptor.err))`. `err` is by-name so the stream is still made when the layer is built, as before.
- Test: JvmReadingSuite "the JVM's log lines are UTF-8, whatever the platform's default" (replaces "the JVM's bootstrap installs its logger"): builds `Main.bootstrapTo(ByteArrayOutputStream)`, logs `ZIO.logError("é")`, expects the last three bytes `0xc3 0xa9 0x0a`.
- Removed: CliSuite "bootstrap installs its logger" (MainSuite "the application's logger takes its level from the command line" builds `Cli.bootstrapWith` and counts lines per level).
- Mutation: `Cli.bootstrapWith(_ => stream.get[PrintStream].flush())` (lines discarded; the literal `_ => ()` would leave `stream` unused under `-Werror`).
- With it: FAIL, 9 passed 1 failed: expected `List(-61, -87, 10)`, obtained an empty list.
- Without: PASS, 10 passed.

### 2.2 MainSuite "an error with no message"

- Test: renamed "an error with no message: status 2, standard input named, and the error logged"; keeps the status and message assertions and adds, through the suite's `logged` helper (the run's real log lines), `lines.exists(_.contains(" error=java.lang.IllegalStateException "))`.
- Mutation: `Main.reading`'s standard-input branch logs `String.valueOf(error.getMessage)`.
- With it: FAIL, 16 passed 1 failed: the line held `error=null`.
- Without: PASS, 17 passed.

### 2.3 SettingsSuite "without --verbose"

- Test: renamed "without --verbose: not verbose, with the inputs and their options"; compares `parseInvocation(List("--output", "json", "notes.md"))` with the literal `Right(Invocation(verbose = false, NonEmptyList.one(Settings(Some("notes.md"), None, None, Some(OutputFormat.Json)))))`.
- Mutation: the command builds `Settings(_, format, profile, output.filter(_ => false))` (drops `--output`; `None` in its place would leave `output` unused under `-Werror`).
- With it: FAIL, 17 passed 3 failed: this test, plus "output option" and "several files", which already caught it.
- Without: PASS, 20 passed.

### 2.4 The parse-error tests

- Tests: CheckerSuite "exit status 2 and a message on standard error for malformed JSON" and MainSuite "an unparsable file before a missing one…": `stderr.startsWith("Not OpenSpec change or spec JSON:")` (replacing `stderr.nonEmpty`).
- Mutation: `OpenSpecInput.candidates` returns `Left(error.take(0))`.
- With it: CheckerSuite FAIL (29 passed, 1 failed: the malformed-JSON test, "Result was false"); MainSuite FAIL (16 passed, 1 failed: the unparsable-file test).
- Without: PASS (30 and 17 passed).

### 2.5 ExtractSuite's change

- Test: the change JSON gets a third delta, MODIFIED "Upcoming" ("The scheduler SHALL list upcoming events in order."), `deltaCount` 3; the expected list holds its candidate after the ADDED one.
- Mutation: `checkedOperations = Set("ADDED")`.
- With it: FAIL, 5 passed 1 failed ("a change: added and modified requirements are checked…").
- Without: PASS, 6 passed.

### 2.6 MainSuite "an entry's cause is written with it"

- Test: adds `written.exists(_.contains("disk full"))`.
- Mutation: `"cause" -> ""` in `Cli.logger`.
- With it: FAIL, 16 passed 1 failed.
- Without: PASS, 17 passed.

### 2.7 CheckerSuite standard input

- Test: "standard input: each line is checked and located as <stdin>" now checks two failing lines; the findings' locations are `List("<stdin>:1:", "<stdin>:2:")`.
- Mutation: `content.linesIterator.take(1)` in `Candidate.fromPlainText`.
- With it: FAIL, 28 passed 2 failed: this test, and "a finding on a line" (a two-line file).
- Without: PASS, 30 passed.

### 2.8 LoggingSuite's one-line property

- Shared generator: new `ears/cli/test/src/io/github/tsnee/ears/cli/TerminalText.scala` (`acted`, `characters`, `texts`), moved out of VisibleSuite, which now imports them.
- Test: "whatever the details hold, an entry is one line of printable characters" draws `check(TerminalText.texts, TerminalText.texts)` and asserts `!written.exists(TerminalText.acted)`.
- Mutation: `case c if separatesLines(c) => c.toString` in `Logging.escape`.
- With it: FAIL, 15 passed 2 failed: the property and the witness "other control characters and line separators are written as \uXXXX".
- Without: PASS, 17 passed.

### 2.9 Duplicates

CheckerSuite:

- `:103` "JSON output with no findings…" removed: "no findings: the version, the checker, the count, and an empty list" compares the whole document (one line, `checked` 1, `findings` []).
- `:114` "JSON output with a finding names its kind and fix" removed: "a finding on a line" compares a whole document with a kind and a string fix; the OpenSpec profile on plain text is the merged `:85`/`:124` test's.
- `:171` "exit status 2 and the read error…" removed: "an input that can't be read or parsed: status 2, its cause, and no findings" asserts status 2, the name, and empty stdout, and "a message from an unreadable input shows its control characters" asserts the two-argument `outcome(settings, Left(…))` overload's exact stderr (the overload passes `Left` straight to the one-argument one).
- `:247` "every input is checked: JSON counts the requirements of both" removed; its `exitCode == 0` folded into `:364`, renamed "several inputs: one document counting both, followed by one line break" (the whole document already held `"checked":2` and one line).
- `:343` "plain text without a fix: the fix is null" removed: "a finding in a change requirement, without a fix" and "…spec requirement…" compare whole documents with `"fix":null`; `findingJson` writes `fix` the same way whatever the location.
- `:85` + `:124` merged as "selected profile: the OpenSpec profile on plain text reports wrong case and its fix" (both assertions: `wrong-case` and the corrected sentence).
- `:92` + `:99` merged as "text output, by default or selected: one human-readable line per finding": one line, not starting `{`, and the default outcome equals the selected-text one.

CliSuite:

- `:74` "a file name with an escape sequence shows it as \u001b" removed: CheckerSuite "a message from an unreadable input shows its control characters as \uXXXX" (the same message, exact) and the property "a character in a missing file's name…" cover the escaping; CliSuite's line-feed name test still covers the path through the program.

MainSuite:

- `:91` "piped input" removed: CliSuite "standard input is read when no file is given" (same assertions, status 0 and empty stdout).
- `:123` "two missing files: the message names only the first" removed: CliSuite "several files are read in order, up to the first that fails" asserts stderr equals exactly the first failing file's message and that no later file is read.
- `:138` "a missing file before a failing one" removed: the CliSuite test above (status 2, exact stderr, nothing read after) with "a file that can't be read: status 2 and its name" (empty stdout) and the moved "a failing file before a missing one" (empty stdout when a checked file precedes the failure).
- `:178` "a failing requirement" (console wiring) removed: CliSuite console "a failing file: exit code 1 and the finding on standard output" (same three assertions).
- `:309` "readAll decodes a stream as UTF-8" removed: JvmReadingSuite "standard input is read as UTF-8" reads the same text through `Main.readAll`.
- Moved into CliSuite (shared, so they run on Node too), over the suite's fake `Reading`: `:132` "a failing file before a missing one: status 2 and nothing on standard output" (in "program"), and the console tests `:168` "a passing requirement: exit code 0 and nothing written", `:189` "--help: exit code 0 and the usage text on standard output", `:196` "bad arguments: exit code 2 and the usage text on standard error" (in "console"). Each keeps its assertions. All four ran and passed on Node.

### Final runs

- `./mill __.checkFormat`: everything formatted.
- `./mill ears.cli.test` (JVM): 148 passed, 0 failed (162 before, by count of the 14 removed).
- `./mill ears.cli.js.test` (Node): 113 passed, 0 failed (118 before, by count).

## strengthen-tests, group 3: evidence (2026-11-11)

Each mutation was applied to production code, the module's JVM tests run, then reverted (`git checkout -- <src>`) and the tests run again. At the end, `git diff -- ears/markdown/src ears/markdown/src-jvm ears/markdown/js/src ears/markdown/native/src ears/linkCheck/src ears/releaseNotes/src` is empty.

### 3.1 release-notes `MainSuite`

Change: `run` now reads both streams, then clears both (`TestConsole.clearOutput *> testConsoleWith(_.clearOutputErr)`); the ad hoc `clearOutput` calls are gone. zio-test 2.1.27's `TestConsole` companion has no `clearOutputErr` accessor (only the service has it), hence `testConsoleWith`.

- Mutation a: `notes` reads the changelog with `ZIO.attemptBlocking(Files.readString(changelog)).option`. "a file that can't be read exits 1 and names it": FAILS (`Result was false`; `notesErr` is now empty, no longer carrying `check`'s message). Reverted: PASSES.
- Mutation b: `badArguments` prints the usage only when `arguments.isEmpty`. "bad arguments exit 2 with the usage": FAILS (`Result was false`). Reverted: PASSES.
- Reverted: `ears.releaseNotes.test` 30 passed (MainSuite 8, ChangelogSuite 22).

### 3.2 `SourceLinesSuite` and the conformance fixture

- `:139` and `:151` replaced by "an escaped first word after a hidden line ending matches the punctuation it decodes to": `SourceLines.paragraph("Intro <span\nclass=x>\\*b\\* c.", 1, List(text("Intro "), text("*b* c.")))`, lines `List(Some(1), Some(2))`.
- Mutation: `matchedFrom`'s escape branch guarded by `false &&`. JVM: the new test FAILS (expected `List(Some(1), Some(2))`, the escaped text stays on line 1); "matched: in text an escaped backslash…" fails too. Reverted: PASSES.
- `:86` renamed "text an entity changed takes the line it's on, and the text after a break the next" (escapes play no part in it: the entity stops the whole-piece match, the first word places it). Input unchanged.
- Conformance fixture "an escaped first word after inline HTML across lines": `"Intro. <span\nclass=x>\\*b\\* shall c.\n"` gives `Sentence(2, "*b* shall c.")` on the JVM (commonmark-java) and on Node (commonmark.js). With the same mutation on Node (`ears.markdown.js.test`) the fixture FAILS as well as the two SourceLines tests; reverted, Node passes 112. On the JVM the fixture can't see the mutation (commonmark-java gives inline positions; `SourceLines` isn't used there).
- Native (not run here; the gate runs it): cmark with `CMARK_OPT_SOURCEPOS` advances its line past raw inline HTML across lines, the same mechanism the existing fixture "inline HTML across lines keeps the text on each side apart" relies on (its "The c SHALL d." on line 2). The escaped `*` pieces are separate text nodes on line 2; `CmarkLines` places the one stretch at the paragraph's end line (2) less its span (1), so "Intro. " is line 1 and `*b* shall c.` line 2. Expected to agree; the gate confirms.

### 3.3 `HelpersSuite` first candidate

Test "the first candidate that loads is used, and the ones after it aren't tried": candidates `/a /b /c /d`, `/b` and `/c` both load (different values); an `AtomicReference[List[String]]` records each attempt. Asserts `Right("loaded /b")` and tried `List("/a", "/b")`; the all-fail case uses a separate pure attempt.

- Mutation: `firstLoaded` = `candidates.map(attempt)`, last `Right` else every `Left`. FAILS (`Right("loaded /c")` not `Right("loaded /b")`; tried list differs). Reverted: PASSES.

### 3.4 `LinkCheckSuite` private owners

Kept "a public member of a private[p] object isn't one" (`PrivateWithinAccess`, the qualifier form the library's own `private[pkg]` objects take). The `PrivateAccess` test (same `case _ => false` branch of `isPublic`, same direct-owner shape) became "a public member of a public object inside a private one isn't one": `Owner.Hidden` private, `Owner.Hidden.Inner` public, `Owner.Hidden.Inner.run()` expects only `apply`.

- Mutation: `isPublicThroughout` = symbol public and (owner is a package or owner public). FAILS (obtained includes `p/Owner.Hidden.Inner.run().`). Reverted: PASSES.

### 3.5 `LinkCheckSuite` summary line

"the line the generator prints matches the one the program will print" now takes the `println(s"…")` template from `LinkCheck.program(operations)` (three operations), substitutes `${reached.size}` with the count, and compares with `LinkCheck.summary(3)`.

- Mutation: the program prints `operations linked` instead of `operations reachable`. FAILS (expected `Some("link-check: 3 operations reachable")`). The sibling test "references every operation from main…" also fails, since it holds the `println` line as a literal. Reverted: PASSES (27 + 5).

### 3.6 Renames, comments, removals

- `SourceLinesSuite` long paragraph renamed "speed guard: a long paragraph with an entity on every line stays linear", with a comment on the bounded window (14 s vs 0.6 s from the review), no timeout.
- `MarkdownConformanceSuite` "the platform's parser is found and named": comment that it's meaningful on Scala Native; the JVM's and Node's `MarkdownParser.name()` return constants (`Right("commonmark-java")`, `Right("commonmark.js")`).
- `ChangelogSuite` "a misnamed Unreleased is named" removed: "Unreleased missing: the first heading is named" owns `UnreleasedNotFirst` with a first heading that isn't Unreleased.
- "malformed versions and dates, and a second Unreleased" keeps one version-shaped heading (`v0.2.0 - 2026-11-20`); `Changelog` parses the heading's version with `Version.parse`, which "what isn't a version" covers (`1.2`, `1.2.3.4`, `01.2.3`, `1.2.99999999999`).

### Final counts

- `ears.markdown.test` (JVM): 112 passed (57 + 35 + 20). `ears.markdown.js.test` (Node): 112.
- `ears.linkCheck.test`: 32 passed (27 + 5).
- `ears.releaseNotes.test`: 30 passed (8 + 22).
- `./mill __.reformat`: nothing to change; `./mill __.checkFormat`: success.

## strengthen-tests, task 4.1: evidence (2026-11-11)

Changed: `scripts/cli-cases` only.

### Cases

| Case | Arguments | Expected | Subject's status (Node, native) |
| --- | --- | --- | --- |
| openspec-change-text (new) | fixture-change.json | 1 | 1, 1 |
| openspec-change-json (new) | --output json fixture-change.json | 1 | 1, 1 |
| openspec-spec-text (new) | fixture-spec.json | 1 | 1, 1 |
| openspec-spec-json (new) | --output json fixture-spec.json | 1 | 1, 1 |
| openspec-json (real specs) | was "0 1", now 0 | 0 | 0, 0 |
| openspec-byte-order-mark | now --output json; was "0 1", now 0 | 0 | 0, 0 |
| verbose | also needs a DEBUG line in `$tmp/$subject/verbose.log` | 0 | 0, 0 (passes) |
| openspec-text | removed (real specs, always empty output) |  |  |

Markdown cases stay "0 1": today they end with status 1 (762 findings on 621 requirements, in README.md and openspec/**/*.md, archived changes included), but no gate holds the Markdown to either status, so neither one is guaranteed.

### Outputs (the native subject, kept by a scratchpad harness that sources cli-cases)

openspec-change-text (status 1, 2 lines):

    add-reminders/reminders/"Reminder sent": missing-comma: Put a comma after the When clause. Fix: "When a reminder is due, the scheduler SHALL send a notice."
    add-reminders/reminders/"Upcoming": weak-modal: Use SHALL instead of "SHOULD". Fix: "The scheduler SHALL list upcoming events in order."

openspec-change-json (status 1; checked 2, 2 findings; the REMOVED delta isn't checked):

    {"version":1,"checker":"0.3.0-SNAPSHOT","checked":2,"findings":[{"location":{"type":"change","change":"add-reminders","capability":"reminders","requirement":"Reminder sent"},...

openspec-spec-text (status 1, 2 lines):

    invoices/"Overdue": missing-then: Follow the If clause with "then". Fix: "If an invoice is overdue then the billing service SHALL notify the account owner."
    invoices/"Overdue": missing-comma: Put a comma after the If clause. Fix: "If an invoice is overdue, the billing service SHALL notify the account owner."

openspec-spec-json (status 1; checked 2, 2 findings):

    {"version":1,"checker":"0.3.0-SNAPSHOT","checked":2,"findings":[{"location":{"type":"spec","spec":"invoices","requirement":"Overdue"},...

openspec-byte-order-mark (status 0):

    {"version":1,"checker":"0.3.0-SNAPSHOT","checked":4,"findings":[]}

verbose: the native log has 2 DEBUG lines. The jar without --verbose writes no log lines at all, so the new check would fail a build that ignores the switch.

### Checks

- `sh -n scripts/cli-cases`: OK.
- `shellcheck -x` on cli-cases, check-npm-cli, check-native-cli, check-coursier-app: clean. Without `-x`, check-npm-cli shows only the SC1091 info note for sourcing npm-cli-project, which HEAD's copy shows too.
- `scripts/check-npm-cli`: exit 0, "the Node CLI installs and matches the JVM CLI on every case" (24 cases).
- `scripts/check-native-cli`: exit 0 on the first run (no plugin race), "the native CLI matches the JVM CLI on every case" (24 cases).
- Jar compared: sha256 0072e89d80cfc3f297b605fd8f25aac9c0de8ca99ff41d448715e00789f07e19.

## strengthen-tests: review fixes A to F (2026-11-11)

### B (review 3): stopped, no edit kept

Probe: in CheckerSuite's "every document is one line that starts with the version and the checker", the sentence `"The scheduler shall list\u2028upcoming events."` was replaced by `"When an order arrives\u2028the scheduler shall list events."` (missing comma, so a finding with a fix) and the assertion `!outcome.stdout.exists(c => c.toInt == 0x2028 || c.toInt == 0x2029)` added. Production code unchanged.

Result, `./mill ears.cli.test.testOnly io.github.tsnee.ears.cli.CheckerSuite`: 29 passed, 1 failed: that property, after 2 iterations, shrunk input containing the U+2028 sentence; `.exists(c => c.toInt == 0x2028 || c.toInt == 0x2029) = true`.

So the CLI's JSON output (`Checker.report`, `document.toJson` from zio-json's `Json` AST) already writes U+2028 raw; nothing escapes it (roadmap item 5). Per the instruction, the probe was reverted (file restored byte for byte from a copy taken before it) and no change kept.

### C (review 4, 7): MainSuite "an error with no message"

Test now runs `Cli.program` once and asserts on `Main.reading(...).standardInput`'s error value: `failed.map(_._1).contains(ReadFailure.Unreadable)` and `failed.map(_._2).contains("java.lang.IllegalStateException")`; suite "unreadable input" marked `@@ TestAspect.silent`.

Mutation: in `Main.reading`, `case error => ReadFailure.Unreadable -> error.toString` changed to `... -> String.valueOf(error.getMessage)`.

- With mutation, `./mill ears.cli.test.testOnly io.github.tsnee.ears.cli.MainSuite`: 16 passed, 1 failed: "an error with no message: status 2, standard input named, and the error carried", `failed.map(_._2).contains("java.lang.IllegalStateException")` false.
- Reverted (`cmp` against a copy of Main.scala taken before: identical): `./mill ears.cli.test` passes, 148 tests. "Cannot read standard input" no longer appears in the test output.

### A, D, E, F

No mutation asked. A: renamed only. D: `acted`/`controls` from `TerminalText` (new `TerminalText.controls`, same three generators as CheckerSuite's private one). E: named arguments. F: the original test restored to HEAD's text; ClauseOrder/MissingThen split out.

### Final runs (all mutations reverted, after `./mill __.reformat`; `./mill __.checkFormat` passes)

- `./mill ears.cli.test`: 148 passed, 0 failed
- `./mill ears.test`: 88 passed, 0 failed
- `./mill ears.cli.js.test` (Node): 113 passed, 0 failed
- `./mill ears.js.test` (Node): 88 passed, 0 failed
