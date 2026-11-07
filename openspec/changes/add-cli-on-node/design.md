# Design

## Context

See proposal.md for why. What's in place, and what probes found (2026-11-05):

- **Sources.** Of `ears.cli`'s sources, only `Main` is JVM-only. It holds:
  - `Files.readString` for files, and `System.in.readAllBytes` for standard input;
  - the error descriptions: `NoSuchFileException` becomes "no such file", and any other error
    gives its message;
  - `System.err` for the logger;
  - `Clock.javaClock` for the logger's timestamps.

  `Checker`, `Settings`, `Candidate`, `OpenSpecInput`, `MarkdownInput`, and `Logging` use no
  JVM API. `cli(arguments, stdin)` and `program` already take standard input as a `Task`.
- **Tests.** Three suites are JVM-only:
  - `MainSuite` (temporary files, `BufferedInputStream`, `LinkedBlockingQueue`);
  - `OwnSpecsSuite` (reads `openspec/` through `forkEnv`);
  - `LocaleSuite` (changes the JVM's default locale).

  `CheckerSuite`, `ExtractSuite`, `LoggingSuite`, and `SettingsSuite` aren't.
- **Dependencies.** ZIO 2.1.27, zio-json 1.1.1, decline 2.6.2, and zio-test 2.1.27 publish
  Scala.js builds.
- **A ZIO app on Node** (`scala-cli` probes, Scala.js 1.22, ES module):
  - **Exit status:** `exit(ExitCode(3))` reaches the shell as 3. `Console.printLineError` goes to
    standard error.
  - **Arguments:** `ZIOAppArgs`/`getArgs` is empty, since Scala.js passes no arguments to `main`.
    `process.argv.drop(2)` holds them.
  - **Reading:** `node:fs`'s `readFileSync(path, "utf8")` reads files, and `readFileSync(0)`
    reads piped and empty standard input. Failures carry `code` `ENOENT` (missing) and `EISDIR`
    (a directory).
  - **`java.time`:** ZIO on Scala.js doesn't declare scala-java-time, yet its default logger and
    `Clock.instant` need it. Without it, linking fails (`java.time.Instant.now()`). zio-test
    brings it in, which is why tests don't show this. scala-java-time is 2.9.0.
  - **Time zones:** `Clock.javaClock` fails at run time on Node with `ZoneRulesException:
    Unknown time-zone ID`. It calls `ZoneId.systemDefault`, and scala-java-time ships no zone
    data. ZIO's own clock (`clock.unsafe.instant()`) gives the instant without zones.
- **Size.** A stand-in with the CLI's dependencies (ZIO with logger and clock, decline,
  zio-json, scala-java-time, the classifier) linked for release as an ES module comes to
  4.66 MB (694 KB gzipped). The library's `main.js` is 1.57 MB. (Measured in apply: the CLI's
  own `main.js` is 5.04 MB, 748 KB gzipped, and the packed package 1.72 MB.)
- **The JVM's read-failure messages** (the 0.2.0-SNAPSHOT jar):
  - missing file: `Cannot read missing.txt: no such file`;
  - directory: `Cannot read dir: Is a directory`, the OS's text from `IOException`;
  - unreadable file: `Cannot read secret.txt: secret.txt`, since `AccessDeniedException`'s
    message is the path.
- **Log lines** (from `Logging.line`) start with an ISO timestamp and a level, for example
  `2026-11-05T13:36:38.339393Z ERROR Cannot read input …`. Everything else on standard error is
  a message.
- **npm names, chosen by the owner:**
  - library `@tsnee/ears-checker` (unchanged);
  - CLI `@tsnee/ears-checker-cli`, separate, with the command `ears-check`.

  `npx` runs a package's only `bin` whatever the package is called. None of the names is taken
  on npm.

## Goals / Non-Goals

**Goals:**
- One CLI program: everything but platform I/O compiled for both platforms and tested on both.
- On Node, the same standard output, messages, and exit statuses as the JVM jar, checked on real
  inputs by running both.
- An installable `@tsnee/ears-checker-cli` package whose only dependency is the Markdown parser.

**Non-Goals:**
- Publishing either npm package (the next roadmap item).
- The CLI on Scala Native.
- Identical log entries across platforms: they carry platform error names (an exception class
  on the JVM, an error code on Node).

## Decisions

### 1. One program, a reading seam, two entry points

```scala
/** Why an input couldn't be read. Platform errors aren't interpreted. */
enum ReadFailure derives CanEqual:
  case Unreadable
  case NotUtf8(line: Int, column: Int)

/** The platform's reading: a file by name, and standard input. */
final case class Reading(
    file: String => IO[(ReadFailure, String), String],
    standardInput: IO[(ReadFailure, String), String]
)
```

- **The failure detail.** The `String` beside each failure is the platform's own description of
  the error, for the log: the exception's `toString` on the JVM, the JavaScript exception's on
  Node (Node's message, starting with its code).
- **No interpretation of platform errors** (settled in review). The plan first mapped each
  platform's errors to shared kinds (missing, directory, denied) with their own messages. That
  means knowing exception classes and errno codes on two platforms, whose kinds and wording
  differ (ENOTDIR, ELOOP, ENAMETOOLONG, and EPERM each read differently) and can change with a
  JDK or Node release. Instead every read error is `Unreadable`: the message is
  `Cannot read <input>` on both platforms, and the error log entry, written by default, carries
  the platform's description. `NotUtf8` keeps its position in the message, since this program
  decides it (Decision 7).
- **Shared code** (`ears/cli/src`) is everything in `Main` but platform I/O:
  - `cli(arguments, reading)`, `program`, and `run`;
  - the logger's construction;
  - the message for each failure, the pure `ReadFailure.describe`.

  It lives in a new `object Cli` that both entry points call.
- **`Main` (`ears/cli/src-jvm`)** is the JVM entry point, as now, and keeps its name and
  `mainClass`. Its `Reading` reads bytes (`Files.readAllBytes`, `System.in`) and decodes them
  with the shared `Utf8` (Decision 7).
- **`NodeMain` (`ears/cli/js/src`)** is the Node entry point:
  - `object NodeMain { def main(args: Array[String]) }` passes `process.argv.drop(2)` to the
    `main` of `NodeApp`, the Node `ZIOAppDefault` over `Cli`, so `getArgs` and `bootstrap` see
    the real arguments (Context);
  - its `Reading` reads bytes with `node:fs` and decodes them with `Utf8`.
- **An empty file name is rejected** while parsing arguments: the JVM reads `""` as the
  working directory and Node as no file.
- **The jar's messages change:** an unreadable input gives `Cannot read <input>` (the jar said
  `Cannot read missing.txt: no such file`, and other errors' reasons, before).

### 2. Time from ZIO's clock

The logger's timestamp becomes a `() => Instant` built in `bootstrap` from ZIO's clock:
`clock.unsafe.instant()` under `Unsafe.unsafe`, read once per entry. It replaces
`Clock.javaClock`, which needs zone data on Scala.js (Context), and works the same on the JVM.
Tests still pass a fixed function.

### 3. Modules and sources

| Module | Sources | Tests |
|---|---|---|
| `ears.cli` (JVM, `Published`, `Covered`) | `ears/cli/src` + `src-jvm`, the Markdown facade's shared + JVM | `ears/cli/test/src` + `test/src-jvm` |
| `ears.cli.js` (Scala.js, not published to Maven) | `ears/cli/src` + `js/src`, the Markdown facade's shared + `js` | `ears/cli/test/src` on Node |

- `ears.cli.js` depends on `ears.js` and on the Scala.js builds of zio, decline, zio-json, and
  scala-java-time 2.9.0. It links as an ES module with `NodeMain` as its main class.
- Its tests use the Markdown facade's `npmDependencies` and import map, so `ExtractSuite`'s
  Markdown test runs on Node too.
- The JVM-only suites move to `ears/cli/test/src-jvm` and still run on the JVM. `MainSuite`'s
  call sites follow the new API (`Cli.program(arguments, Main.reading(stdin))`, the logger's
  `() => Instant`), approved by the owner. Three of its tests asserted the platform's reason on
  the message line (a directory's, a closed stream's, an exception's class); with the owner's
  approval they now assert `Cannot read <input>` (Decision 1), and their names say so. A new
  `JvmReadingSuite` covers `Main`'s reading, its strict decoding, and its UTF-8 console; new
  shared suites cover `Cli` over a fake `Reading` (`CliSuite`) and the decoder (`Utf8Suite`),
  on both platforms. `OwnSpecsSuite` now keeps the backticks around inline code, with the
  owner's approval, so it classifies the same text as `scripts/check-openspec` (OpenSpec's
  JSON); dropping them had made `` `Done.` `` end a sentence there and nowhere else.
- Coverage stays on the JVM (`ears.cli`, minimums 96 / 95), which now measures the shared
  program too. `NodeMain` and its `Reading` are the only Node-only code: a thin shell over
  `node:fs`, covered by the Node tests and the check in Decision 5.

### 4. The package: `ears.cli.js.npmPackage`

The task writes `@tsnee/ears-checker-cli` under its `Task.dest`, in the style of the library's
`ears.npm.npmPackage`:
- `main.js` from `fullLinkJS`;
- `main.js.map` with this repository's sources embedded. The library's
  `embedProjectSources` moves into a shared helper both tasks use, so no path from the build
  machine ends up in the package. Unlike the library's dependencies, several of the CLI's
  (ZIO, zio-json, decline, izumi-reflect, the Scala.js locales) name their sources by `file:`
  URIs from the machines that built them, not `https://` URLs; the helper keeps those, without
  content, as it does `https://` ones, and still fails on a `file:` URI in this repository;
- `bin/ears-check.js`, two generated lines: `#!/usr/bin/env node` and `import "../main.js";`.
  A shebang can't go in front of `main.js` itself without shifting its source map by a line;
- a README for the CLI package, and `LICENSE`;
- `package.json` with:
  - `name: "@tsnee/ears-checker-cli"` and `version` (the library's `npmVersion`);
  - `description`, `license: "CC0-1.0"`, and `type: "module"`;
  - `bin: { "ears-check": "./bin/ears-check.js" }`;
  - `dependencies: { "mdast-util-from-markdown": <the version in ears/markdown/js's
    lockfile> }`, read from the lockfile with `ujson`, exactly as tested, never a range;
  - `engines: { node: ">=22" }`, `repository` in npm's trusted-publishing form, `homepage`,
    `bugs`, `keywords`, and `files`.

The library package doesn't change.

### 5. The check: `scripts/check-npm-cli`

A POSIX `sh` script in the style of `check-npm-package`:

1. Builds the CLI package and the JVM assembly jar.
2. Packs the package, installs it into an empty project (`--cache` in a temporary directory),
   and checks that the only packages installed are it, `mdast-util-from-markdown`, and the
   latter's dependencies from the lockfile.
3. Runs `node_modules/.bin/ears-check` and the jar on each case:
   - every tracked `.md` file, in one run, with text output and then JSON output;
   - this repository's OpenSpec JSON (`openspec show --json` of every spec; `openspec show`
     can't show an archived change), in text and JSON output, with the default profile;
   - a plain-text file;
   - standard input, piped;
   - a file that isn't valid UTF-8, and non-ASCII output under `LC_ALL=C`;
   - a missing file, a directory, and a file the user may not read (skipped as root);
   - `--help`, `--format yaml`, and `--verbose` on a passing file.
4. For each case, compares exit statuses, standard output byte for byte, and standard error
   with log lines removed (lines starting with an ISO timestamp and a level). The first
   difference fails the script, naming the case and showing both sides. Each case also names
   the statuses it may end with, so both builds failing the same way fails too; and every
   tracked Markdown file must exist.
5. Fails if any packed file contains the repository path, or `$HOME` anywhere but the source
   map's list of sources, where libraries' own `file:` URIs (Decision 4) are kept. The first
   CI run showed why: GitHub's runners build ZIO and others under `/home/runner`, which is also
   `$HOME` on a runner.

CI runs it in `check` (Node 26) after the npm package check, and in `check-jdk17` (Node 22,
the oldest the package declares). It needs the network only to install from the registry.

### 6. Docs

- **README:**
  - a "Command line on Node" note: `npx @tsnee/ears-checker-cli` once published, and building
    and installing the package locally until then;
  - the new script under Building.
- **CLAUDE.md:** Build and test, Layout (`ears/cli/js`, `src-jvm`, `test/src-jvm`), and the
  rule that the CLI's platform I/O goes through `Reading`.
- **`docs/roadmap.md`:**
  - the Node item comes off;
  - "Publish the npm packages" names both packages and the owner's npm account;
  - Later gains the CLI's per-file size limit, if it ever checks untrusted input (from the
    `add-markdown-facade` memory measurements).

### 7. Encodings: UTF-8 in, UTF-8 out

Added after review, which found the two builds disagreeing wherever an encoding was involved.
A CLI meets the locale in six places:

| Where | Decision |
|---|---|
| Decoding input | Always UTF-8, never the locale's charset: JSON (RFC 8259) and CommonMark fix it, and CI and containers usually run under `LANG=C` with UTF-8 files. Invalid bytes are rejected (`ReadFailure.NotUtf8`, with the line and column), not replaced: a Latin-1 or UTF-16 file must not pass unread. |
| Encoding output | Always UTF-8. The JVM's `System.out` and `System.err` follow the locale (`é` becomes `?` under `LC_ALL=C`, and the rule differs between JDK 17 and newer), so `Main` installs a UTF-8 `Console` and logs through a UTF-8 stream. Node writes UTF-8 already. |
| File names | Node uses UTF-8. The JVM on Linux decodes arguments and paths with the locale's charset; checked separately on Linux before deciding anything. |
| Text rules | Locale-independent already (`LocaleSuite`). |
| The OS's error text | Only in log entries, never interpreted (Decision 1). |
| Our own text | Fixed English, ISO timestamps. |

**No `--encoding` option** (decided with the owner, 2026-11-07). UTF-8 only, with a clear
error, covers the formats the checker reads. An option limited to what Scala.js's library
decodes (US-ASCII, ISO-8859-1, UTF-8, and the UTF-16 variants) would be small, but ISO-8859-1
accepts every byte, so a wrong choice would pass unnoticed. Windows-1252, the legacy encoding
people actually have, isn't in Scala.js, and Node's decoder maps a few of its bytes differently
from Java's: supporting it means a shared decoding table per charset. Revisit only when someone
needs another encoding.

`Utf8.decode` is shared: the platform reads bytes, and Java's UTF-8 decoder in its reporting
mode (which Scala.js implements too) decodes them, so both platforms agree on what's invalid
and where. The column counts characters, as an editor does. Measured cost: none visible on
Node (0.37 s before and after for this repository's 880 KB of Markdown) and about 3% on the JVM
(0.65 s to 0.67 s; 1.01 s to 1.04 s for 7 MB). A UTF-8 byte-order mark is valid and still
dropped by the Markdown facade.

## Risks / Trade-offs

- **[The jar's output changes]** Only where Decisions 1 and 7 say: read-failure messages, input
  that isn't UTF-8, output under a non-UTF-8 locale, and empty file names. Apply compared the
  jar's output on this repository's files with `main`'s jar byte for byte.
- **[Non-ASCII file names under the C locale on Linux]** Checked in Docker (JDK 17 and 27): the
  Java launcher decodes the command line with the locale's charset before `main` runs, so
  `résumé.txt` arrives as `r��sum��.txt` and can't be opened (`InvalidPathException`, exit 2).
  Nothing in the program can recover the name; the README tells JVM users to run under a UTF-8
  locale. Node decodes the command line as UTF-8 whatever the locale: checked in Docker
  (Node 22 and 26, C and C.UTF-8), it reads `résumé.txt` and writes `café` in all four runs.
- **[Standard input closed by the caller]** With file descriptor 0 closed (`<&-`), the JVM
  reads whatever file it opened first as descriptor 0, usually one of its own, which strict
  decoding then rejects; Node reads nothing. It takes a caller that closes standard input on
  purpose, so it's left as a known difference.
- **[Package size]** 5.04 MB for the CLI's `main.js`, 9.4 MB unpacked with its source map
  (Context), plus the parser's dependencies. That's acceptable for a command-line tool; the library package stays small.
- **[A Node-only failure the check doesn't cover]** Its cases are the repository's own files
  and the error cases above. A platform difference in, say, Unicode case mapping inside the
  classifier is covered by `ears.js.test`, which runs the library's suites on Node.
- **[Standard input from a terminal]** `readFileSync(0)` on an interactive terminal waits for
  end of input, as the JVM does. The check covers only piped input.
- **[Log entries differ by platform]** By design (Non-Goals); the check ignores them.
