# Proposal

## Why

`ears-check` runs on the JVM (0.67 s to start on this repository's README) and on Node, so
using it means installing one of them. A native binary starts instantly and needs neither, which
suits pre-commit hooks and CI steps that run it often. The pieces exist: the Markdown reader
already runs natively with cmark (`ears.markdown.native`), and a probe on 2026-11-07 found every
library the CLI uses (ZIO, zio-json, decline, cats-parse, zio-test) building and running on
Scala Native 0.5, with the JVM's own file reading, standard input, console, clock, and
`Locale.ROOT` case mapping all working; a release build of the probe started in under 0.01 s.
This is the first change in the `batch-release-0.2.0` batch.

## What Changes

- **The CLI on Scala Native**, with the same standard output, messages, and exit statuses as
  the JVM and Node builds. It compiles the shared program and the JVM's entry point (`Main`)
  unchanged: everything that entry point uses works on Scala Native.
- **The classifier on Scala Native** (`ears.native`), which the CLI needs; its suites run
  natively too. Not published to Maven Central in this change.
- **cmark is found when the program starts, not when it's linked.** cmark puts its full release
  in its library's name (`libcmark.0.31.2.dylib`, `libcmark.so.0.30.0`), so a binary linked
  the usual way starts only where that exact release is installed. Instead the Markdown reader
  loads cmark itself (`dlopen`), from the usual names and library directories, whatever
  compatible release is installed, and only when a Markdown input needs it: plain text and
  OpenSpec JSON need no cmark at all.
  - If no cmark is installed (or one older than 0.30), checking a Markdown file fails with a
    message naming what to install: `brew install cmark`, or `apt install cmark` on
    Debian and Ubuntu.
  - The build no longer needs cmark to link (`pkg-config` goes); the native tests need it to
    run, as before.
- **A check that the native binary agrees with the jar**, on the same cases `check-npm-cli`
  uses, which move into a file both scripts share. CI runs it.
- **Documentation** of the native build and its runtime dependency on cmark.
- **Not in this change:** attaching binaries to releases (the release-notes change, next),
  Homebrew or other packages, publishing the native library, and platforms CI doesn't build
  (it builds linux-x64; macOS arm64 is built locally).

## Capabilities

### New Capabilities

- `cli-native-binary`: the native `ears-check` binary: a release build that links only the
  platform's system libraries, with cmark loaded at run time.

### Modified Capabilities

- `ears-cli`: the checker behaves the same on Scala Native as on the JVM, and says what to
  install when cmark is missing.
- `markdown-input`: on Scala Native, the reader finds an installed cmark at run time, whatever
  its release.
- `continuous-integration`: CI fails when the native binary's output differs from the jar's.

## Impact

- **`build.mill`:** `ears.native` (the classifier, `Base` + `ScalaNativeModule`, its tests);
  `ears.cli.native` (the shared program, `src-jvm`, the Markdown reader's native sources, its
  tests); `ears.markdown.native` loses `cmarkLinking`.
- **`ears/markdown/native/src`:** `MarkdownParser` calls cmark through functions loaded at run
  time; the choice of library file (names, directories, versions) is pure code in the shared
  sources, measured on the JVM.
- **Scripts:** `scripts/check-native-cli`, and the shared case list.
- **CI:** builds the native binary in release mode and runs the check in `check`.
- **Docs:** README (the native binary and cmark), CLAUDE.md, roadmap, worklog.
