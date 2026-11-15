# Tasks

All work is on branch `batch-cli-messages`, the "CLI messages and output" theme (roadmap items 4 to 7), draft PR #27. The owner pushes from their own shell; Claude reads runs with `gh`. Test-first: each behavior's test is written and seen failing before the code that makes it pass. Editing, deleting, skipping, or loosening an existing test (including widening or narrowing a generator such as `TerminalText`'s) is asked of the owner first, with the requirement it covers; this plan adds tests beside them instead.

## 1. One escape for both outputs

- [x] 1.1 Tests first, in `ears/cli/test/src` (every platform): `VisibleSuite`, a lone high and a lone low surrogate become `\ud83d` and `\ude00`, a valid pair (an emoji) stays as it is, and a property that the result holds no lone surrogate; `CheckerSuite`, JSON output of an OpenSpec requirement with a finding holding U+009B, DEL, U+2028, and `\ud83d` shows each as `\u009b`, `\u007f`, `\u2028`, `\ud83d`, holds none raw, and parses back to the same requirement text; text output's fix shows `\ud83d` (the spec's scenarios). Seen failing on the JVM and Node
- [x] 1.2 The code (decision 1): `Visible.escape` escapes lone surrogates, and `Checker.report` passes the JSON document through it; `Visible`'s comment says what JSON output now gets. Verify `./mill ears.cli.test ears.cli.js.test` pass and `ears.cli`'s coverage minimums hold
- [x] 1.3 Cases in `scripts/cli-cases`: OpenSpec JSON with `\ud83d`, `\u009b`, and `\u2028` in a requirement with a finding, with text and with JSON output, the bytes the same from the jar, Node, and the native binary; verify `scripts/check-npm-cli` and `scripts/check-native-cli` pass
- [x] 1.4 Docs: `docs/json-output.md` (these characters as escapes; the version stays 1), the CHANGELOG (Fixed), and roadmap item 6 marked in progress. Verify `prettier --check '**/*.md'` and `./mill ears.releaseNotes.run check CHANGELOG.md`

## 2. Checks, review, commits

- [x] 2.1 Run the gate (CLAUDE.md's list) and report coverage from `scripts/coverage-records`; add a dated line to `docs/worklog.md`
- [x] 2.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix
- [x] 2.3 Commit the plan and the implementation together, then the archive; the owner pushes to PR #27. Report the CI run's result and minutes from the records
- [x] 2.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- Archive the change (syncing `ears-cli`), then the theme's last item (roadmap item 7, bidirectional overrides in text output).
