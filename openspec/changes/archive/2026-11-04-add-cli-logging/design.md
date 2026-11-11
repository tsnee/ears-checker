# Design

## Context

See proposal.md for why. What's in place:

- `Main` is a `ZIOAppDefault`. `run` reads the arguments and calls `cli(arguments, stdin)`, which runs `program` and writes the `Outcome`'s standard output and error; `program` parses `Settings`, reads each input (`read`), extracts candidates, and builds the outcome. Nothing logs, and ZIO's default logger is still installed.
- `Settings.parse(arguments): Either[Usage, NonEmptyList[Settings]]` (decline). A `Settings` is one input's options; there is no run-wide option yet. `SettingsSuite` and `CheckerSuite` build `Settings` values with four fields in 12 places.
- `scripts/check-openspec` runs the jar once over every spec and change; the Claude Code hook relays its combined output.
- zio-test's `TestConsole` and test logger are available; the project's rules say tests don't assert on log output.

## Goals / Non-Goals

**Goals:**

- From a failing run's standard error alone, find the input, its format and profile, the error's type and message, and the exit status.
- With `--verbose`, reconstruct a successful run.
- Every decision (the minimum level, the line format, what an entry carries) is a pure function with unit tests; `Main` only logs.

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

`--verbose` is a decline `Opts.flag` in the same command, so it appears in the usage text and works before or after the files. `parse` keeps its signature and meaning, so no existing test changes.

Alternative: a `verbose` field on `Settings`. It would repeat a run-wide flag on every input and change the 12 existing constructor calls in tests, which the project's rules say to avoid.

### 2. A pure `Logging` object; the logger installed per run

`Logging.scala` (`private[cli] object Logging`), all pure:

- `minimumLevel(verbose: Boolean): LogLevel` — `Debug` or `Warning`.
- `line(timestamp, level, message, annotations): String` — one line: `2026-11-04T12:00:00Z ERROR Cannot read input file=notes format=text …`. Line breaks in the message or a value become `\n`, and every other control character and U+2028/U+2029 become `\uXXXX` (code review finding 11); a value with a space, `=`, or `"` is quoted.
- `inputDetails(settings): Map[String, String]` — `file`, `format`, `profile`, `output` for one input (`<stdin>` for standard input).
- `argumentsDetail(arguments): String` — the arguments as a JSON array, so `"my notes.md"` and `my notes.md` log differently (finding 5).

`Main` wires it (revised after the code review, findings 3, 4, and 9):

- `logger(minimum, writeLog, clock): ZLogger[String, Unit]` writes each entry at `minimum` or above as `Logging.line(...)` to `writeLog`, stamped by `clock`, with a `cause` detail (`Cause.prettyPrint`) when the entry has one. Everything it uses is a parameter. `loggerFor(arguments, writeLog, clock)` picks `minimum` from `--verbose`.
- `bootstrap` (`bootstrapWith(System.err.println)`) removes ZIO's default logger and installs `loggerFor` built from `ZIOAppArgs` and ZIO's `Clock.javaClock`. It is the application's only logger, and it covers starting and exiting too, so a defect ZIO reports outside the run still reaches standard error in this format (finding 3).
- `cli(arguments, stdin)` is the console wiring only, as before this change; it logs to whatever logger is installed. Tests don't run `bootstrap`, so they install `loggerFor(…, fakeWriter, clock)` with `ZIO.withLogger`, the clock coming from `Clock.javaClock` so `TestClock` sets the timestamps; one test builds `bootstrapWith` from `ZIOAppArgs` to check the level it chooses.
- Details go on entries as ZIO log annotations (`ZIO.logAnnotate`), so the line shows them as `key=value`.

The arguments are parsed in `bootstrap` for the level and again in `program` for the run (finding 8); parsing is pure. A parse failure logs at error, which passes either level.

A first revision configured one logger through three `FiberRef`s made with ZIO's `Unsafe` API and set per run with `locally`. The owner found that uneasy: the logger's behavior depended on ambient fiber state rather than its parameters, and the `FiberRef`s were made outside an effect. Building the logger in `bootstrap` needs neither.

Alternative: zio-logging's console logger with a format string. It would add a library for what `Logging.line` does in a few lines, and the global rules say to add zio-logging only for JSON or a custom format.

### 3. What is logged where

| Point | Level | Message and details |
| --- | --- | --- |
| Arguments parsed | debug | `Arguments` with `arguments`, `inputs`, `verbose` |
| Invalid arguments | error | `Invalid arguments` with `arguments`, `reasons` (decline's errors), `exit=2` |
| Before reading an input | debug | `Reading input` with `inputDetails` |
| Input read | info | `Read input` with `inputDetails`, `characters`, `requirements` |
| Input unreadable or unparsable | error | `Cannot read input` (or `Cannot parse input`) with `inputDetails`, `error` (the exception's class or "parse"), `reason`, `exit=2` |
| Run ended, however it ended | info | `Finished` with `findings`, `exit` |

`Finished` closes every run, failures and `--help` included (finding 1). `--help` logs nothing else: decline accepts it only on its own, so `--verbose` can't accompany it (the first draft's `Help requested` debug entry could never be written; finding 7). Findings are the program's result, not a failure, so a failing requirement is not a warning. Nothing logs at warn yet; the level exists for recoverable problems later.

To log the exception type, `read` keeps the `Throwable` until it has logged, then maps it to the user message as now; `Checker.candidates` already returns its parse error as a value, which `program` logs with `error=parse`.

### 4. Tests

- `LoggingSuite`: `minimumLevel` both ways; `line` for a plain entry, annotations in order, a multi-line message and value (`\n`), quoting; `inputDetails` for a file and for standard input.
- `SettingsSuite` (new cases only): `parseInvocation` with and without `--verbose`, `--verbose` after the files, and `parse` unchanged with `--verbose` present.
- `MainSuite` (new cases only): `--verbose` changes neither the exit code nor the console output (through `cli` and `TestConsole`; finding 6); with the logger's writer faked, a default run of a passing input writes nothing, a directory writes one line, and an entry with a cause writes it. That last pair checks the filter's wiring, not the wording, which stays with `LoggingSuite`.
- The coverage minimums (`ears.cli` 96 / 95) must still pass.

## Risks / Trade-offs

- [Error entries duplicate the user message] → On a failure, standard error gains one line before the existing message (the program logs as it runs; the console writes the outcome at the end); the message itself is unchanged. `scripts/check-openspec` sees it only when an input fails, which already fails the check.
- [Entries logged outside a run] → The logger is installed for the whole application and defaults to warnings on standard error, so a defect ZIO reports while starting or exiting still appears.
- [Annotations hold user input] → Values are escaped, so a file name with a line break or a terminal escape sequence can't forge a second entry or change the terminal.
- [The default omits info and debug] → The owner's global rule asks for every level always; the owner chose warnings by default for this command line (2026-11-04), and the code review's finding 2 records the conflict.

## Migration Plan

None. A rollback is reverting the commit.
