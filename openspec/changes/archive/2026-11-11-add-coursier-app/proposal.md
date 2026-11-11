# Proposal

## Why

The README's Coursier route is a shell alias around `cs launch` with two `--java-opt` flags and a pinned version: it has to be retyped or saved by hand, and it never updates. A Coursier app puts a real `ears-check` on the PATH with one `cs install`, carries the JVM options, and moves to each new release with `cs update`. With 0.2.0's native binaries, the same app can install the native binary on linux-x64, linux-arm64, and macos-arm64 and the JVM launcher everywhere else (Windows, Intel Macs), so Coursier users get the fastest start their platform has without choosing a file. The owner chose to keep it in 0.2.0 (2026-11-08).

## What Changes

- A channel file in this repository, `coursier/apps.json`, describes the app `ears-check`: the CLI from Maven Central at its latest release, its main class, its JVM options, and the release's native binary for each of the three platforms. Users install it with `cs install --channel https://raw.githubusercontent.com/tsnee/ears-checker/main/coursier/apps.json ears-check`.
- Where the platform has no native binary, or the release's binary can't be downloaded, Coursier installs the JVM launcher instead (a probe showed it, design).
- `scripts/check-coursier-app` installs the app from the channel file, pointed at this build (the locally published CLI, and with `--binary` the native binary), into a temporary directory, and compares the installed command with the jar on the shared CLI cases. CI runs it for the JVM launcher in `check` and `check-jdk17`, and for the native binary in each native recheck job.
- The README's Coursier section replaces the alias with `cs install` and `cs update`; `docs/release-files.md` mentions the channel; CLAUDE.md and CONTRIBUTING.md list the new script.

## Capabilities

### New Capabilities

- `coursier-app`: the `ears-check` Coursier app and the channel file that describes it.

### Modified Capabilities

- `continuous-integration`: CI checks the Coursier app, on the JVM and with each native binary.

## Impact

- New: `coursier/apps.json`, `scripts/check-coursier-app`.
- `.github/workflows/ci.yml` and `native.yml`: Coursier's CLI (`coursier/setup-action`) and the new check.
- `scripts/cli-cases` gains a third subject; README, `docs/release-files.md`, CLAUDE.md, CONTRIBUTING.md, CHANGELOG.md, the roadmap, the work log.
- No change to the CLI, the build's published artifacts, or the release workflow: the app reads what releases already publish.
