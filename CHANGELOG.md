# Changelog

Every release of ears-checker, newest first. Each change adds its lines under Unreleased; a release renames Unreleased to its version and date.

## Unreleased

### Added

- Both npm packages are on npm, from 0.2.0: `npx @tsnee/ears-checker-cli` runs the command line, and `npm install @tsnee/ears-checker` installs the library. From this release on, the release workflow stages them on npm with trusted publishing, and they're published once the owner has checked that they're the GitHub Release's own tarballs.
- Markdown tables are read as GitHub reads them, each cell on its own: every requirement in a table is checked, on its row's line, the same on the JVM, Node, and Scala Native.
- Warnings, beside the findings, for problems in the input that aren't a requirement's: a `file:line: warning: …` line in text output, and a `warnings` array in JSON output (still format version 1). The first is `table-extra-cells`, a table row with more cells than its header, which GitHub doesn't show. Warnings don't change the exit status.
- cmark-gfm's license notices: in the jar (`META-INF/cmark-gfm/COPYING`), in the CLI's npm package (`cmark-gfm/COPYING`), and as `THIRD-PARTY-NOTICES.txt` in each GitHub Release.

### Changed

- Markdown is read with GitHub's own parser, cmark-gfm 0.29.0.gfm.13 with its table extension, the same on every build: compiled into the native binary, and as a WebAssembly module in the jar and the npm package. The native binary no longer needs cmark installed and loads no library beyond the system's, and the log's parser detail names cmark-gfm (a Markdown input no longer logs the libraries tried). Thousands of tables in a row are read without running out of stack.
- The CLI's npm package has no dependencies.
- A link definition directly above a table, with no blank line, is read as GitHub reads it: as paragraph text, so a cell's link to it stays unresolved.

### Fixed

- A Markdown table is no longer read as one long sentence, which gave a misleading finding and left the table's other requirements unchecked; a table right after a paragraph no longer merges into it.
- An input given as OpenSpec JSON that isn't OpenSpec JSON, or Markdown the parser couldn't read, is named in the error, as an unreadable input is: `Cannot parse notes.json: not OpenSpec change or spec JSON: …`, where the message didn't say which input it was about. A broken change's error now says what's wrong with it as a change, not as a spec.
- `--help` works anywhere among the options, where it worked only alone and was otherwise an unexpected option. With an input file it's invalid arguments, so a glob that picks up a file named `--help` fails rather than checking nothing.
- JSON output writes the C1 control characters (such as U+009B), DEL, and the line and paragraph separators (U+2028, U+2029) as `\u` escapes, where they were written raw, so no line-based tool splits the document and no terminal acts on them. Still format version 1: each escape is the same character.
- A lone surrogate in an OpenSpec requirement (from a JSON escape such as `\ud83d`) is shown as `\ud83d` in text output and in JSON output, the same from every build, where the jar and the native binary wrote `?` and the Node CLI U+FFFD.

## 0.2.0 - 2026-11-11

### Added

- The `ears-check` command line on Node, as the npm package `@tsnee/ears-checker-cli`, with the same output and exit statuses as the jar. It's attached to the GitHub Release as a tarball; it isn't on npm yet.
- The `ears-check` command line as a native binary for Linux (x64 and Arm) and macOS (Arm). It reads Markdown through an installed cmark 0.30 or later, loaded when it runs (`brew install cmark`, or `apt install cmark` on Debian and Ubuntu); plain text and OpenSpec JSON need nothing installed. It never loads cmark from the current directory, even when `LD_LIBRARY_PATH` or `DYLD_LIBRARY_PATH` has a relative entry; on macOS it then reads no Markdown and says which entry to remove.
- The classifier as the npm package `@tsnee/ears-checker`, with TypeScript declarations and a source map leading back to the Scala sources, likewise attached to the release as a tarball and not on npm yet.
- `ears-check` as a Coursier app, from this repository's channel (see the README): `cs install` gives the native binary on Linux and macOS (Apple silicon) and the JVM launcher elsewhere, and `cs update ears-check` moves it to the next release.
- Release notes from this changelog, and a GitHub Release for each version with the jar, both npm tarballs, the native binaries, a `SHA256SUMS` file, and build-provenance attestations.

### Changed

- The README is written for users, with a quick start for each way to get ears-checker and its limits; building and contributing moved to `CONTRIBUTING.md`, and `SECURITY.md` says how to report a vulnerability privately.
- JSON output starts with a format version, `"version": 1`, and the release that wrote it, `"checker"`, and is documented field by field in `docs/json-output.md`, with what can change within a version.
- Markdown is read the same way on every platform: commonmark-java on the JVM, commonmark.js on Node, and cmark natively, checked against one conformance suite.
- Checking is much faster on Node and natively: a 7 MB Markdown file takes about 2 to 3 s on every build, where Node took 43 s and the native binary 88 s (`docs/performance.md`).
- Inline code in a Markdown requirement reads as one quoted word, as OpenSpec's JSON gives it.
- Input must be UTF-8: a file with a bad byte is an error naming its line and column. Output is UTF-8 whatever the locale.
- An empty file name is a bad argument (exit status 2).

### Fixed

- Text output and messages no longer pass control characters from the input to the terminal: an escape sequence, bell, or line separator in a requirement, a file name, or an OpenSpec name shows as `\uXXXX`, so a file can't hide or forge findings on screen. Backslashes are unchanged.

## 0.1.1 - 2026-11-05

### Added

- `ears-check` logs to standard error, one line per entry: warnings and errors by default, and every level from debug with `--verbose`.

### Fixed

- The Scala.js library (`ears-checker_sjs1_3`) links without scala-java-locales.

## 0.1.0 - 2026-11-03

The first release, on Maven Central.

### Added

- `io.github.tsnee:ears-checker_3` and `ears-checker_sjs1_3`: classifies one requirement sentence against the six EARS templates, with a diagnostic and a suggested fix where one exists. Pure, with no IO and no effect system; Scala 3.10 or later.
- `io.github.tsnee:ears-checker-cli_3`: the `ears-check` command line, on JDK 17 or later. It checks plain text, Markdown, and OpenSpec JSON (`openspec show --json`), from files or standard input, with the `classic` or `openspec` profile and text or JSON output; it exits 0 when every requirement passes, 1 when any finding is reported, and 2 for bad arguments or input.
