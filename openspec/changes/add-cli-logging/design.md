# Design

## Context

See proposal.md for why. What's in place:

- `Main` is a `ZIOAppDefault`. `run` reads the arguments and calls
  `cli(arguments, stdin)`, which runs `program` and writes the
  `Outcome`'s standard output and error; `program` parses `Settings`,
  reads each input (`read`), extracts candidates, and builds the
  outcome. Nothing logs, and ZIO's default logger is still installed.
- `Settings.parse(arguments): Either[Usage, NonEmptyList[Settings]]`
  (decline). A `Settings` is one input's options; there is no
  run-wide option yet. `SettingsSuite` and `CheckerSuite` build
  `Settings` values with four fields in 12 places.
- `scripts/check-openspec` runs the jar once over every spec and
  change; the Claude Code hook relays its combined output.
- zio-test's `TestConsole` and test logger are available; the
  project's rules say tests don't assert on log output.

## Goals / Non-Goals

**Goals:**
- From a failing run's standard error alone, find the input, its
  format and profile, the error's type and message, and the exit
  status.
- With `--verbose`, reconstruct a successful run.
- Every decision (the minimum level, the line format, what an entry
  carries) is a pure function with unit tests; `Main` only logs.

**Non-Goals:**
- Changing any existing user-facing message, exit status, or output.
- Logging inside `Checker` or the `ears` library, which are pure.

## Decisions

### 1. A run-wide `Invocation`, leaving `Settings` alone

```scala
/** The whole command line: run-wide options and one settings per input. */
final case class Invocation(verbose: Boolean, inputs: NonEmptyList[Settings]) derives CanEqual

object Settings:
  def parseInvocation(arguments: List[String]): Either[Usage, Invocation]
  def parse(arguments: List[String]): Either[Usage, NonEmptyList[Settings]] =
    parseInvocation(arguments).map(_.inputs)
```

`--verbose` is a decline `Opts.flag` in the same command, so it
appears in the usage text and works before or after the files.
`parse` keeps its signature and meaning, so no existing test changes.

Alternative: a `verbose` field on `Settings`. It would repeat a
run-wide flag on every input and change the 12 existing constructor
calls in tests, which the project's rules say to avoid.

### 2. A pure `Logging` object; the logger installed per run

`Logging.scala` (`private[cli] object Logging`), all pure:

- `minimumLevel(verbose: Boolean): LogLevel` — `Debug` or `Warning`.
- `line(timestamp, level, message, annotations): String` — one line:
  `2026-11-04T12:00:00Z ERROR Cannot read input file=notes format=text …`.
  Line breaks in the message or a value become `\n`; a value with a
  space or `=` is quoted.
- `inputDetails(settings): Map[String, String]` — `file`, `format`,
  `profile`, `output` for one input (`<stdin>` for standard input).

`Main` wires it:

- `bootstrap = Runtime.removeDefaultLoggers`, so ZIO's own logger
  never writes.
- `cli` parses the `Invocation` first, then runs everything else
  under `ZIO.withLoggerScoped` with a `ZLogger` that filters by
  `minimumLevel` and writes `Logging.line(...)` with
  `java.lang.System.err.println` (unbuffered and safe from any fiber,
  unlike interleaving with `Console`). The write is a seam:
  `cliLogging(arguments, stdin, writeLog: String => Unit)` does the
  work, and `cli(arguments, stdin)` is `cliLogging` with
  `System.err.println`, so `cli`'s existing tests keep their calls and
  new tests pass a fake writer. The logger's filter and line come from
  `Logging` and are tested there.
- Details go on entries as ZIO log annotations
  (`ZIO.logAnnotate`), so the line shows them as `key=value`.

The level comes from the parsed arguments, so it can't be chosen in
`bootstrap`, which runs before the arguments are parsed. A parse
failure logs at error, which passes either level.

Alternative: zio-logging's console logger with a format string. It
would add a library for what `Logging.line` does in a few lines, and
the global rules say to add zio-logging only for JSON or a custom
format.

### 3. What is logged where

| Point | Level | Message and details |
|---|---|---|
| Arguments parsed | debug | `Arguments` with `arguments`, `inputs`, `verbose` |
| Invalid arguments | error | `Invalid arguments` with `arguments`, `reasons` (decline's errors), `exit=2` |
| Before reading an input | debug | `Reading input` with `inputDetails` |
| Input read | info | `Read input` with `inputDetails`, `characters`, `requirements` |
| Input unreadable or unparsable | error | `Cannot read input` (or `Cannot parse input`) with `inputDetails`, `error` (the exception's class or "parse"), `reason`, `exit=2` |
| Run ended | info | `Finished` with `findings`, `exit` |

`--help` is a successful run: it logs only at debug. Findings are
the program's result, not a failure, so a failing requirement is not
a warning. Nothing logs at warn yet; the level exists for recoverable
problems later.

To log the exception type, `read` keeps the `Throwable` until it has
logged, then maps it to the user message as now; `Checker.candidates`
already returns its parse error as a value, which `program` logs with
`error=parse`.

### 4. Tests

- `LoggingSuite`: `minimumLevel` both ways; `line` for a plain entry,
  annotations in order, a multi-line message and value (`\n`), quoting;
  `inputDetails` for a file and for standard input.
- `SettingsSuite` (new cases only): `parseInvocation` with and
  without `--verbose`, `--verbose` after the files, and `parse`
  unchanged with `--verbose` present.
- `MainSuite` (new cases only): `--verbose` changes neither standard
  output nor the outcome; with the logger's writer faked, a default run
  of a passing input writes nothing and a directory writes one line.
  That last pair checks the filter's wiring, not the wording, which
  stays with `LoggingSuite`.
- The coverage minimums (`ears.cli` 96 / 95) must still pass.

## Risks / Trade-offs

- [Error entries duplicate the user message] → On a failure, standard
  error gains one line before the existing message (the program logs as
  it runs; the console writes the outcome at the end); the message
  itself is unchanged. `scripts/check-openspec` sees it only when an input
  fails, which already fails the check.
- [Logging from a scoped logger misses entries logged outside `cli`]
  → `run` logs nothing itself; `bootstrap` removes the default logger
  so no stray entry appears in another format.
- [Annotations hold user input] → Values are escaped, so a file name
  with a line break can't forge a second entry.

## Migration Plan

None. A rollback is reverting the commit.
