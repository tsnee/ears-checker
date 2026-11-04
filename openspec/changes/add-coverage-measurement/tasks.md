# Tasks

All work is on branch `add-coverage-measurement`. The owner pushes it
from their own shell; Claude opens the pull request and reads runs
with `gh`.

## 1. Coverage in the build

- [x] 1.1 Check Maven Central for the current `org.scoverage` release and record it in design Decision 1; verify `mill-contrib-scoverage` exists at Mill 1.3.1
- [x] 1.2 Load the plugin in the `build.mill` header and add `ScoverageModule` to `ears` and `ears.cli` and `ScoverageTests` to their `test` modules (design Decision 1), using Metals after `import-build`; verify `./mill __.compile`, `./mill __.test` (including `ears.js.test`), and `./mill __.checkFormat` pass, and `./mill ears.scoverage.consoleReport + ears.cli.scoverage.consoleReport` prints statement and branch percentages for both modules
- [x] 1.3 Verify the published artifacts are uninstrumented: after `./mill __.test`, list `ears.jar`, `ears.cli.jar`, and `ears.cli.assembly` and search their classes (`javap -c` or the jar listing) for any `scoverage` reference; there must be none
- [x] 1.4 Set `statementCoverageMin` / `branchCoverageMin` to `ears` 95 / 90 and `ears.cli` 93 / 88 (design Decision 2); verify `ears.scoverage.validateCoverageMinimums` passes, and, temporarily raising `ears.cli`'s statement minimum to 99, that `ears.cli.scoverage.validateCoverageMinimums` fails with a message naming the module and both percentages (then restore it). If `ears.cli` is below 93 before group 2, record the measured value here and set the minimum after 2.4
  - 2026-11-04: `ears` set to 95 / 90 and passes (95.48% / 90.91%). `ears.cli` measured 92.98% / 88.00%, below 93, so its minimums wait for 2.4. With a temporary 99 the check failed with "statement coverage (92.98) did not meet the minimum desired by the project. (99.0)" under `[error] ears.cli.scoverage.validateCoverageMinimums`

## 2. Main's seam and error paths (test-first)

- [x] 2.1 Add failing tests to `MainSuite`: `cli` under `TestConsole` writes the outcome's standard output and error and returns its exit code for a passing input (0), a failing input (1), and `--help` (0, usage on standard output); verify they fail to compile or fail before 2.2
- [x] 2.2 Implement design Decision 3 in `Main.scala` (`cli`, `readAll`, `run` reduced to wiring); verify 2.1's tests pass
- [x] 2.3 Add tests for the `ears-cli` delta's new scenarios: standard input that is a closed stream (every read fails with the JDK's `IOException("Stream closed")`; WartRemover bans a hand-written `throw`) gives status 2 and a standard-error message naming standard input and "Stream closed"; a directory given as a file gives status 2 and a message naming it and the operating system's reason; `readAll` decodes a UTF-8 `ByteArrayInputStream`; verify they pass
- [x] 2.4 Run `./mill __.test` and the coverage report; verify `Main.scala`'s only uncovered statement is the call in `run`, then raise each module's minimums to its measured percentages rounded down to a whole number where higher than 1.4's, and verify `validateCoverageMinimums` passes for both modules; record the before and after numbers in the pull request description
  - 2026-11-04, measured after `./mill clean ears.scoverage ears.cli.scoverage`: `ears` 95.48% / 90.91% (unchanged; minimums stay 95 / 90); `ears.cli` 92.98% / 88.00% → 96.80% / 95.92% (minimums 96 / 95). `Main.scala`'s only uncovered code is `run` (lines 17–18). One more test was added for an error with no message (`describe`'s fallback). The assembly jar, run by hand, exits 1 with a finding for piped input and 2 with "Cannot read /tmp: Is a directory"

## 3. CI and docs

- [x] 3.1 Add the "Report coverage" and "Check coverage" steps to `ci.yml`'s `check` job (design Decision 4); verify `yq` parses it, `actionlint` reports nothing, no `run:` contains `${{`, and `check-jdk17` is unchanged
- [x] 3.2 Update CLAUDE.md (Build and test: the coverage report and check commands and the minimums' location; CI: five checks; Layout: the `test-coverage` spec) and the README's Building section; verify by reading both with `mq`
- [x] 3.3 Run `./mill __.test`, the coverage check, and `scripts/check-openspec`; verify all pass and `git status` shows changes only in `build.mill`, `Main.scala`, `MainSuite.scala`, `ci.yml`, `CLAUDE.md`, `README.md`, `docs/worklog.md`, and this change; add a dated line to `docs/worklog.md`

## 4. Review and pull request

- [ ] 4.1 Run a local `/code-review` in a subagent over the branch's diff against `main`; report every finding to the owner, fix the ones the owner accepts, and record the rest with the owner's reason in the pull request description
- [ ] 4.2 After the owner pushes and Claude opens the pull request, verify `check` and `check-jdk17` pass, the run's summary page shows both modules' percentages, and record the `check` job's time beside the previous run's (design Risks)

## Workflow follow-up

- Commit the plan, then the implementation, on the branch; the owner squash-merges after group 4.
- Verify, archive (syncing `test-coverage`, `continuous-integration`, and `ears-cli`), and land the archive through its own small pull request.
