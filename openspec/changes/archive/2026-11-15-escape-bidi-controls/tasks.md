# Tasks

All work is on branch `batch-cli-messages`, the "CLI messages and output" theme (roadmap items 4 to 8), draft PR #27. The owner pushes from their own shell; Claude reads runs with `gh`. Test-first: each behavior's test is written and seen failing before the code that makes it pass. Editing, deleting, skipping, or loosening an existing test (including widening or narrowing a generator such as `TerminalText`'s) is asked of the owner first, with the requirement it covers; this plan adds tests beside them instead. No file gets a raw bidirectional character, U+2028, or U+2029 (design decision 3): after each task, every changed file is checked for their UTF-8 bytes.

## 1. Escape the nine characters

- [x] 1.1 Tests first, in `ears/cli/test/src` (every platform), each character built from its code point: `VisibleSuite`, each of U+202A to U+202E and U+2066 to U+2069 becomes `\u` and its four lowercase digits, RLM (U+200F) and ALM (U+061C) stay as they are, and a property that the result of text mixing them with `TerminalText.characters` holds none of the nine; `CheckerSuite`, a plain-text requirement with a missing comma and RLO shows `\u202e` in its fix, a missing file whose name holds RLO shows `\u202e` in its `Cannot read` message, and JSON output of a requirement with a finding and RLI shows `\u2067` and holds no U+2067 (the spec's scenarios). Seen failing on the JVM and Node
- [x] 1.2 The code (decision 1): `Visible.escape` escapes the nine characters; `Visible`'s comment says so. Verify `./mill ears.cli.test ears.cli.js.test` pass and `ears.cli`'s coverage minimums hold
- [x] 1.3 Cases in `scripts/cli-cases`: a plain-text requirement with a missing comma holding RLO, LRI, and RLM (written with `printf` octal bytes), with text and with JSON output, the bytes the same from the jar, Node, and the native binary, each output showing `\u202e` and `\u2066` and keeping RLM; verify `scripts/check-npm-cli` and `scripts/check-native-cli` pass
- [x] 1.4 Docs: `docs/json-output.md` (the nine as escapes; the version stays 1), the CHANGELOG (Fixed), and roadmap item 7 marked in progress. Verify `prettier --check '**/*.md'` and `./mill ears.releaseNotes.run check CHANGELOG.md`

## 2. Log lines (decision 4, folded in after the code review)

- [x] 2.1 Tests first, in `LoggingSuite`, each character built from its code point: RLO and RLI in a message and in a detail become `\u202e` and `\u2067`; a lone surrogate becomes `\ud83d` and a surrogate pair stays; a property that an entry from text mixing the nine and surrogates with `TerminalText.characters` holds none of the nine and no lone surrogate; the existing tests unchanged. Seen failing on the JVM and Node
- [x] 2.2 The code: `Visible.escaped` becomes `private[cli]`, `Logging.escape` walks positions and asks it after its own cases, `separatesLines` goes; both objects' comments say the predicate is shared. Verify `./mill ears.cli.test ears.cli.js.test` pass and `ears.cli`'s coverage minimums hold
- [x] 2.3 `scripts/cli-cases`' `missing-bidirectional` case also checks the log: it shows the name's RLO as `\u202e` and holds no raw RLO, on every build; verify `scripts/check-npm-cli`, `scripts/check-native-cli`, and `scripts/check-coursier-app` pass
- [x] 2.4 Docs: the CHANGELOG line names log lines too, the roadmap's "One predicate" item is marked done here, and the `Visible` comment's mention of log lines is current

## 3. Checks, review, commits

- [x] 3.1 Run the gate (CLAUDE.md's list) and report coverage from `scripts/coverage-records`; add a dated line to `docs/worklog.md` (run before the log-line tasks; run again in 3.3)
- [x] 3.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix
- [x] 3.3 After the log-line tasks: the gate again, coverage recorded, the work log, and `/code-review` again on the log-line part; the owner chooses which findings to fix
- [x] 3.4 Commit the plan and the implementation together, then the archive; the owner pushes to PR #27. Report the CI run's result and minutes from the records
- [x] 3.5 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- Archive the change (syncing `ears-cli`), then the theme's security review in a subagent, then the owner merges PR #27.
