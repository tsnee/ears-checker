# Tasks

All work is on branch `batch-release-0.2.0`, the second change in its batch (draft PR #17). The
owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The changelog and the release-notes tool (test-first)

- [x] 1.1 Add `ears.releaseNotes` to `build.mill` (decision 2): JVM only, commonmark-java at the facade's version (a shared value), never published, with coverage minimums; add it to CI's coverage report and check. Verify `./mill ears.releaseNotes.compile` passes and no product module depends on it
- [x] 1.2 Write `Changelog` tests: a valid changelog's sections; each form failure (`Unreleased` missing or not first, a heading without a date, a malformed version or date, versions out of order) naming the heading; a section kept verbatim, including a `## ` line inside a code block; a missing or empty version section naming the version; `notes` with and without a files description, `{version}` replaced. Verify they fail before 1.3
- [x] 1.3 Implement `Changelog` and `Main` (`check`, `notes`), logging as the CLI does. Verify the tests pass and the module meets its minimums
- [x] 1.4 Write `CHANGELOG.md` (decision 1): `0.1.0` and `0.1.1` from `git log` up to each tag, and `Unreleased` with this batch's changes; and `docs/release-files.md` (decisions 3 and 7). Verify `ears.releaseNotes.run check CHANGELOG.md` passes and `notes 0.1.1` prints that section

## 2. Kept files and the macOS minimum

- [x] 2.1 Add `--out` to `check-npm-package` and `check-npm-cli`, and `--out`, `--binary`, and `--jar` to `check-native-cli` (decision 4), each logging the kept file's SHA-256. Verify with `shellcheck`, that each script still passes with no switches, that the kept files are the ones installed or run, and that `--binary --jar` on a kept binary passes
- [x] 2.2 Link the native binary with `-mmacosx-version-min=15.0` on macOS (decision 5). Verify `otool -l` shows `minos 15.0` and `check-native-cli` passes

## 3. Workflows

- [x] 3.1 Add `native.yml` (decision 5) and call it from `ci.yml`; move the native CLI check and the step with less cmark into it; set `EARS_RELEASE_VERSION` from a release tag in every job and upload the kept files only then (decision 4); add the changelog check to `check`. Verify with `yq` and `actionlint` if installed (say so if not), and that every action is pinned by commit
- [x] 3.2 Add the notes to `release.yml`'s `tag` job and the `draft-release` job (decision 6), with its permissions and no checkout. Verify with `yq`
- [x] 3.3 After the push, verify on the pull request's run that all six native jobs pass, record their times and the Linux binaries' newest `GLIBC_` symbol version, and confirm nothing is uploaded as a release file. If a platform can't build, report it to the owner before going on

## 4. A release rehearsal

- [x] 4.1 Rehearse `draft-release`'s steps without a tag, in the scratchpad: the notes for `0.1.1`, `SHA256SUMS` over locally kept files, and `sha256sum -c` on it. Verify every step's command line by reading it; the real run happens at the 0.2.0 release, which reports the draft's files, checksums, and an attestation check

## 5. Backfill, docs, and review

- [x] 5.1 Write the backfill script (decision 8) in the scratchpad with `bash -n` and `shellcheck` clean, and give the owner one command to run it; after it runs, verify both releases with `gh release view`
- [x] 5.2 Update the README, CLAUDE.md, and the roadmap (decision 9). Verify with `mq`
- [x] 5.3 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`, `scripts/check-native-cli`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, report the coverage, and add a dated line to `docs/worklog.md`
- [x] 5.4 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 5.5 Run `/security-review` in a subagent on this change (the new write token in particular), and let the owner choose which findings to fix
- [x] 5.6 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes

## Workflow follow-up

- After CI passes, archive the change on the same branch (creating `github-release`, syncing
  `release-publishing`, `continuous-integration` with the rename, and `cli-native-binary`) for
  the owner to push.
