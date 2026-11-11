# Tasks

All work is on branch `batch-release-0.2.0`, the sixth change in the "release 0.2.0" theme (draft
PR #17). The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The decisions, test-first

- [x] 1.1 Write `HelpersSuite` tests for `unsafeEntries`, `loadable` (the Linux candidates with and without the bare name, macOS declining), and the macOS message (decision 4). Verify they fail before 1.2
- [x] 1.2 Implement `CmarkLibrary.unsafeEntries`, `loadable`, and the message (decisions 1 to 3). Verify the tests pass on the JVM, Node, and natively, and `ears.markdown` keeps 100% statement and branch coverage

## 2. The loader

- [x] 2.1 Apply them in `CmarkLoader.load` (decisions 2 and 3). Verify `scripts/check-native-cli` passes, and rerun the design's macOS probes: no marker with `DYLD_LIBRARY_PATH=.` (status 2, the message), the real cmark with no variable and with `DYLD_LIBRARY_PATH=""`, plain text as usual

## 3. CI

- [x] 3.1 Add the planted-library step to `native.yml`'s recheck jobs (decision 5). Verify with `actionlint`; after the push, that it passes on all three platforms (it repeats the design's probes on the runners, x64 and arm64)
- [x] 3.2 Confirm on Linux, with the owner's colima probe, that glibc reads `;` as a separator: a planted library loads by bare name with `LD_LIBRARY_PATH` set to `/opt/lib;`, `/opt/lib;.`, and `;/opt/lib`, and never by absolute path. Record the results in the design

## 4. Docs, checks, review, commits

- [x] 4.1 Update SECURITY.md and the README (the caveat goes; what the binary does instead), CLAUDE.md if it describes the loader, CHANGELOG (Fixed), and take the item off the roadmap. Verify with `mq`
- [x] 4.2 Run the full gate: `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, and the five check scripts. Report coverage and add a dated line to `docs/worklog.md`
- [x] 4.3 Run `/code-review` in a subagent on this change, and `/security-review` in a subagent (it's a security fix), and let the owner choose which findings to fix
- [x] 4.4 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes (including task 3.1's step on every platform), archive the change on the same
  branch (syncing `markdown-input` and `continuous-integration`) for the owner to push.
