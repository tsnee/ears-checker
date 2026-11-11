# Tasks

All work is on branch `batch-release-0.2.0`, the eighth change in the "release 0.2.0" theme (draft PR #17). The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. The check first

- [x] 1.1 Write `scripts/check-coursier-app` (decision 3): the address check, JVM mode, and native mode (`--binary`, `--jar`), sourcing `scripts/cli-cases` as a third subject; `bash -n` and `shellcheck` it. Run it before the channel file exists and see it fail
- [x] 1.2 Check that the address check fails on swapped linux-arm64 and linux-x64 file names, and that native mode fails on `launcherType: bootstrap`, each with the message the spec's scenario gives (scratch copies of the descriptor, not commits)

## 2. The channel file

- [x] 2.1 Write `coursier/apps.json` (decisions 1 and 2), and check it with `jq`
- [x] 2.2 Run `scripts/check-coursier-app` in JVM mode on this Mac's JDK, and in native mode with `out/ears/cli/native/nativeLink.dest/out` and the assembly jar; both pass. Check that dropping `--sun-misc-unsafe-memory-access=allow` from a scratch copy fails JVM mode on a JDK 23 or later, naming the differing input

## 3. CI

- [x] 3.1 Add `coursier/setup-action` (pinned by commit; decision 3 says which release) and the JVM-mode check to `check` and `check-jdk17` in `ci.yml`, and the native-mode check to each recheck job in `native.yml`, with a comment on each step

## 4. Docs

- [x] 4.1 README's Coursier section, `docs/release-files.md`, CLAUDE.md (the checks, layout, CI, and Releasing step 6: publish the draft release before the Central Portal deployment), CONTRIBUTING.md, CHANGELOG.md under Unreleased, and the roadmap (decision 4). Verify with `mq`

## 5. Checks, review, commits

- [x] 5.1 Run the full gate as documented: `./mill __.checkFormat`, `./mill clean __.scoverage`, `./mill -j 1 '__:ScalaNativeModule.compile'`, `./mill __.test`, every module's `validateCoverageMinimums`, and the six check scripts. Report coverage, and add a dated line to `docs/worklog.md`
- [x] 5.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 5.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes, and that each new step ran

## 6. Intel macOS and Windows (added 2026-11-11, decision 5)

- [x] 6.1 Give `scripts/check-coursier-app` `--repo <dir>` (install from an Ivy repository already holding this build, without Mill) and `--cases smoke` (help, a passing file, a failing file, and JSON output), and make it work under Git Bash on Windows (the `.bat` launcher, Windows paths in `file://` addresses). Test `--repo` and `--cases smoke` here, with a scratch repository; say that the Windows branch is first tested in CI
- [x] 6.2 In `ci.yml`, upload the published CLI and the jar from `check` as an artifact, and add jobs on `macos-15-intel` (all cases) and `windows-latest` (smoke cases) that install the JVM launcher from it and compare it with the jar; update the docs and the design's risks
- [x] 6.3 Run the gate's affected checks, run `/code-review` on the additions and let the owner choose fixes, commit, and report the CI run: both new jobs ran, and on each the app installed the JVM launcher

## Workflow follow-up

- After CI passes, archive the change on the same branch (creating `coursier-app`, syncing `continuous-integration`) for the owner to push.
- After 0.2.0 is published, its release work installs the app from `main` on this Mac, checks that it runs the release's macos-arm64 binary, and records the result.
