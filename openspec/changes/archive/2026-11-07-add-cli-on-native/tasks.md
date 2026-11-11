# Tasks

All work is on branch `batch-release-0.2.0`, the first change in its batch; open a draft pull
request with the batch's first push, so every later push runs CI. The owner pushes from their
own shell; Claude opens the pull request and reads runs with `gh`. The roadmap's reorder
(already on the branch, uncommitted) goes in this change's implementation commit.

## 1. The classifier and the CLI on Scala Native

- [x] 1.1 Add `ears.native` (design Decision 1) and run the library's suites natively. Verify `./mill ears.native.test` passes and `scripts/check-scalajs-link` still passes
- [x] 1.2 Add `ears.cli.native` (Decision 1), linking in `release-full` mode, its tests in debug mode. Verify `./mill ears.cli.native.test` passes, the binary runs on a file, piped input, a missing file, and a directory, and record its size, link time, and start time

## 2. cmark at run time (test-first)

- [x] 2.1 Add `HelpersSuite` tests for `CmarkLibrary` (Decision 2): the candidate order on macOS and Linux (bare names, unversioned files, versioned files newest first, loader-path directories), the version check, and both messages. Ask the owner before changing the conformance suite's `fixture` helper and `InlineCodeRoundTripSuite` to expect `Right(...)`, showing the `markdown-input` requirement they cover. Verify the new tests fail before 2.2
- [x] 2.2 Implement `CmarkLibrary` and the native loader, make `Markdown.sentences` return `Either`, propagate a failure as the input's parse failure in the CLI, and remove `cmarkLinking`. Verify the conformance suite passes on the JVM, Node, and Scala Native; `ears.markdown` stays at 100/100 coverage; and the binary links no cmark (`otool -L`)

## 3. The check and CI

- [x] 3.1 Move the case list and comparison into `scripts/cli-cases`, sourced by `scripts/check-npm-cli` (unchanged in what it checks) and the new `scripts/check-native-cli` (Decision 3). Verify both pass, and that `check-native-cli` fails when the native binary's exit status for a failing file differs (a temporary change, reverted)
- [x] 3.2 Add the native build, the check, and the missing-cmark step to `.github/workflows/ci.yml` (Decision 4). Verify with `yq`, and after the push, that the steps pass on CI, how long they take, and what the native binary does with a non-ASCII file name under `LC_ALL=C` on Linux

## 4. Docs, checks, and review

- [x] 4.1 Update `README.md`, `CLAUDE.md`, and `docs/roadmap.md` (the item comes off) (Decision 5), with the Linux file-name finding from 3.2. Verify with `mq`
- [x] 4.2 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`, `scripts/check-native-cli`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, report the coverage and the binary's size, and add a dated line to `docs/worklog.md`
- [x] 4.3 Run `/code-review` in a subagent on this change's commits, and let the owner choose which findings to fix. Fixed: the missing-cmark CI step (a Markdown input with a finding, explicit checks, `pipefail`, every searched directory checked); the search tries each candidate until one binds (`firstLoaded`); the log names the parser or every library tried; `build.mill`'s misplaced doc comment; `check-native-cli`'s dead branch; `cli-cases`' shellcheck warnings; CLAUDE.md's layout. Skipped by choice: loading cmark once per process
- [x] 4.4 Run `/security-review` in a subagent on this change's commits, and let the owner choose which findings to fix. Fixed: on macOS a bare `dlopen` name searched the current directory; now every candidate is absolute apart from Linux's bare name, with a property test, `HOMEBREW_PREFIX` added, and relative loader-path entries ignored (the `HelpersSuite` tests changed with the owner's approval)
- [x] 4.5 Commit (plan, then implementation); the owner pushes. Open the batch's draft pull request with `gh`, and report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch (creating `cli-native-binary`, syncing
  `ears-cli`, `markdown-input`, and `continuous-integration`) for the owner to push.
