# Proposal

## Why

`ears-check` runs only on the JVM, so using it means installing Java and Coursier. JavaScript developers would rather run `npx`. With Markdown behind the facade (`add-markdown-facade`), nothing stops the CLI from running on Node: decline, ZIO, zio-json, and zio-test all build for Scala.js, and of the CLI's sources only `Main`'s I/O is JVM-only.

## What Changes

- **The CLI on Node**, with the same standard output, messages, and exit statuses as on the JVM.
  - The CLI's sources are split. What doesn't touch the platform (arguments, reading inputs through a seam, checking, output, logging) becomes shared. Each platform gets a small entry point that supplies the seam: `Main` on the JVM, as now, and `NodeMain` on Node.
  - On Node, files and standard input are read through `node:fs`. Arguments come from `process.argv`, since Scala.js passes none to `main`.
  - The logger's timestamp comes from ZIO's own clock instead of `Clock.javaClock`, which needs time-zone data Scala.js doesn't ship.
  - **The jar's behavior changes where the platforms would otherwise differ** (settled in review, design Decisions 1 and 7):
    - an input that can't be read gives `Cannot read <input>` on both platforms, without the platform's reason (for a missing file the jar said `no such file`); the reason is in the error log entry, which is always written;
    - input must be valid UTF-8, or the run fails with the line and column of the first bad byte; the jar used to fail on a file (with an unhelpful message) but replace bad bytes on standard input;
    - output is UTF-8 whatever the locale;
    - an empty file name is a bad argument.

    Every other output of the jar stays the same.
- **A new npm package, `@tsnee/ears-checker-cli`**, separate from the library package `@tsnee/ears-checker`, as library-first tools like remark and markdownlint do.
  - Its `bin` is `ears-check`, so `npx @tsnee/ears-checker-cli spec.md` runs it.
  - It depends on `mdast-util-from-markdown` at the version the conformance suite tests. The library package stays dependency-free.
- **A check that the two CLIs agree:** a script packs and installs the Node CLI, then runs it and the JVM jar on the same inputs. The inputs are every tracked Markdown file, this repository's OpenSpec JSON, plain text, standard input, a missing file, a directory, `--help`, and bad arguments, in text and JSON output. It requires identical standard output, messages, and exit statuses. CI runs it on Node 26 and Node 22.
- **Not in this change:** publishing either npm package (the next roadmap item, which needs the owner's npm account). A Scala Native CLI is also left for its own change.

## Capabilities

### New Capabilities

- `cli-npm-package`: the `@tsnee/ears-checker-cli` npm package: its name, its command, its metadata, and its dependency on the Node Markdown parser.

### Modified Capabilities

- `ears-cli`: the checker behaves the same on Node as on the JVM.
- `continuous-integration`: CI fails when the Node CLI's output differs from the JVM CLI's on the same inputs, on the oldest and newest Node the package supports.

## Impact

- **Sources:**
  - `ears/cli/src` keeps what's shared.
  - `Main` and the JVM I/O move to `ears/cli/src-jvm`, and the Node entry point and I/O go in `ears/cli/js/src`.
  - The three JVM-only suites (`MainSuite`, `OwnSpecsSuite`, `LocaleSuite`) move to `ears/cli/test/src-jvm`.
- **`build.mill`:**
  - `ears.cli` gains platform source directories.
  - A new `ears.cli.js` module (Scala.js, ES module, `NodeMain`) with its Node tests and an `npmPackage` task. It reuses the library package's source-map embedding and the Markdown facade's npm install.
  - scala-java-time 2.9.0 is added for Scala.js. ZIO doesn't declare it, and zio-test hides that in tests.
- **New script:** `scripts/check-npm-cli`.
- **CI:** a step in `check` and in `check-jdk17`.
- **Docs:** `README.md` (running with `npx` once published, and building the package), `CLAUDE.md`, `docs/roadmap.md`, and `docs/worklog.md`.
- **The JVM CLI, the library, and the library's npm package:** unchanged in behavior. The jar's output on this repository's files is checked identical to `main`'s.
