# Changelog

Every release of ears-checker, newest first. Each change adds its lines under Unreleased; a
release renames Unreleased to its version and date.

## Unreleased

### Added

- The `ears-check` command line on Node, as the npm package `@tsnee/ears-checker-cli`, with the
  same output and exit statuses as the jar.
- The `ears-check` command line as a native binary for Linux (x64 and Arm) and macOS (Arm). It
  reads Markdown through an installed cmark 0.30 or later, loaded when it runs (`brew install
  cmark`, or `apt install cmark` on Debian and Ubuntu); plain text and OpenSpec JSON need
  nothing installed. It never loads cmark from the current directory, even when
  `LD_LIBRARY_PATH` or `DYLD_LIBRARY_PATH` has a relative entry; on macOS it then reads no
  Markdown and says which entry to remove.
- The classifier as the npm package `@tsnee/ears-checker`, with TypeScript declarations and a
  source map leading back to the Scala sources.
- Release notes from this changelog, and a GitHub Release for each version with the jar, both
  npm tarballs, the native binaries, a `SHA256SUMS` file, and build-provenance attestations.

### Changed

- The README is written for users, with a quick start for each way to get ears-checker and its
  limits; building and contributing moved to `CONTRIBUTING.md`, and `SECURITY.md` says how to
  report a vulnerability privately.
- JSON output starts with a format version, `"version": 1`, and the release that wrote it,
  `"checker"`, and is documented field by field in `docs/json-output.md`, with what can change
  within a version.
- Markdown is read the same way on every platform: commonmark-java on the JVM, commonmark.js on
  Node, and cmark natively, checked against one conformance suite.
- Checking is much faster on Node and natively: a 7 MB Markdown file takes about 2 to 3 s on
  every build, where Node took 43 s and the native binary 88 s (`docs/performance.md`).
- Inline code in a Markdown requirement reads as one quoted word, as OpenSpec's JSON gives it.
- Input must be UTF-8: a file with a bad byte is an error naming its line and column. Output
  is UTF-8 whatever the locale.
- An empty file name is a bad argument (exit status 2).

## 0.1.1 - 2026-11-05

### Added

- `ears-check` logs to standard error, one line per entry: warnings and errors by default, and
  every level from debug with `--verbose`.

### Fixed

- The Scala.js library (`ears-checker_sjs1_3`) links without scala-java-locales.

## 0.1.0 - 2026-11-03

The first release, on Maven Central.

### Added

- `io.github.tsnee:ears-checker_3` and `ears-checker_sjs1_3`: classifies one requirement
  sentence against the six EARS templates, with a diagnostic and a suggested fix where one
  exists. Pure, with no IO and no effect system; Scala 3.10 or later.
- `io.github.tsnee:ears-checker-cli_3`: the `ears-check` command line, on JDK 17 or later. It
  checks plain text, Markdown, and OpenSpec JSON (`openspec show --json`), from files or
  standard input, with the `classic` or `openspec` profile and text or JSON output; it exits 0
  when every requirement passes, 1 when any finding is reported, and 2 for bad arguments or
  input.
