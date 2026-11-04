# Tasks

All work is on branch `add-cli-logging`, in one pull request that also
carries the archive. The owner pushes from their own shell; Claude
opens the pull request and reads runs with `gh`. Compile, test, and
format through Metals (BSP); use `./mill` only for the coverage check,
`assembly`, and the final CI-equivalent run.

## 1. Run-wide options (test-first)

- [x] 1.1 Add new `SettingsSuite` cases for `parseInvocation` (no flag → `verbose = false`; `--verbose` before and after the files → `true`; the inputs equal `parse`'s) and for `parse` ignoring `--verbose`; verify they fail to compile before 1.2
- [x] 1.2 Add `Invocation` and `Settings.parseInvocation` with decline's `Opts.flag("verbose", …)`, and define `parse` through it (design Decision 1); verify every `SettingsSuite` and `CheckerSuite` test passes unchanged and the new ones pass, and that `--help`'s usage text lists `--verbose`

## 2. Pure logging decisions (test-first)

- [x] 2.1 Add `LoggingSuite` (design Decision 4) for `minimumLevel`, `line` (plain entry, annotation order, `\n` in a message and a value, quoting a value with a space or `=`), and `inputDetails` (file and standard input); verify it fails to compile before 2.2
- [x] 2.2 Implement `private[cli] object Logging` in `Logging.scala` (design Decision 2); verify `LoggingSuite` passes

## 3. Logging in Main

- [x] 3.1 Add new `MainSuite` cases: `--verbose` leaves the outcome and standard output unchanged for a passing and a failing input; with a fake writer in `cliLogging`, a default run of a passing input writes no lines, a directory writes exactly one, and `--verbose` on a passing input writes several; verify they fail before 3.2
- [x] 3.2 Install the logger in `Main` (`bootstrap` removing the default loggers, `cliLogging` and `cli`) and add the log calls in design Decision 3's table, keeping the `Throwable` in `read` until it is logged; verify all `ears.cli` tests pass and no existing user-facing message changed
- [x] 3.3 Run the assembly jar by hand on a passing file, a failing file, a directory, `--format yaml`, and `--help`, each with and without `--verbose`; verify standard output is unchanged from `main`'s jar, and that standard error matches the `ears-cli` delta's scenarios (nothing for a passing file by default, one error line for a directory, the read and outcome entries with `--verbose`)
  - 2026-11-04, compared with the published 0.1.0 CLI (same behavior as `main`): exit statuses and standard output are identical for every case except `--help`, whose usage text now lists `--verbose`. By default, the passing, failing, and Markdown files write nothing to standard error; the directory writes one error entry (`error=java.io.IOException … reason="Is a directory"`) before the unchanged message; `--format yaml` adds one entry. With `--verbose`, a run writes four entries: `Arguments`, `Reading input`, `Read input` (with `characters` and `requirements=2` for a two-item list), and `Finished` (`findings`, `exit`). `--verbose --help` exits 2 with "Unexpected option", as `--output json --help` already does in 0.1.0: decline accepts `--help` only on its own
- [x] 3.4 Update the README's usage line and option list with `--verbose` and a sentence on what standard error carries; verify with `mq`

## 4. Checks, review, and pull request

- [x] 4.1 Run `./mill __.checkFormat`, `./mill __.test`, both modules' `validateCoverageMinimums`, and `scripts/check-openspec`; verify all pass, the hook's output is unchanged when every spec passes, and `git status` shows changes only in `Settings.scala`, `Logging.scala`, `Main.scala`, their suites, `README.md`, `docs/worklog.md`, and this change; report the coverage; add a dated line to `docs/worklog.md`
  - 2026-11-04: all pass. `scripts/check-openspec`'s output has no log entries. Coverage: `ears` 95.48% / 90.91% (unchanged), `ears.cli` 97.61% / 96.97% (from 96.80% / 95.92%; minimums left at 96 / 95). `Checker.scala` also changed: `Outcome` gained `findings` for the outcome entry, and `Usage` gained decline's `reasons` (in `Settings.scala`) for the invalid-arguments entry; no test builds either, so no test changed
- [ ] 4.2 Run a local `/code-review` in a subagent over the branch's diff against `main`; report every finding to the owner, fix the ones the owner accepts, and record the rest with the owner's reason in the pull request description
- [ ] 4.3 After the owner pushes and Claude opens the pull request, verify `check` and `check-jdk17` pass

## Workflow follow-up

- Commit the plan, then the implementation, on the branch.
- After 4.3: verify, archive (syncing `ears-cli`), and commit the archive on the same branch; the owner pushes once more, CI reruns, and the owner squash-merges the one pull request.
