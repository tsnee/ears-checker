# Design

## Context

See proposal.md for why. What's in place:

- `build.mill` has no plugins; `ears` (pure, full Unsafe warts) and
  `ears.cli` (ZIO) share `Base`'s strict compiler options and
  WartRemover. `ears.js` compiles `ears`'s sources and runs
  `ears.test`'s sources on Node.
- The 2026-11-03 measurement used Mill's own contrib plugin,
  `com.lihaoyi::mill-contrib-scoverage` 1.3.1, with scoverage 2.5.2, on
  a scratch clone of this build with the same compiler options; it
  compiled and its tests passed. The plugin's `ScoverageModule` adds an
  instrumented `scoverage` copy of a module beside the ordinary one,
  and `ScoverageTests` points a test module at that copy. Its reports
  are `scoverage.htmlReport`, `xmlReport`, `xmlCoberturaReport`, and
  `consoleReport`; `scoverage.validateCoverageMinimums` checks
  `statementCoverageMin` and `branchCoverageMin`.
- `Main.run` (lines 16–23) reads the arguments, calls `program`,
  prints the outcome, and exits; it and `readStdin` have no tests.
  `program` and `Checker` hold every decision and are tested.
- CI's `check` job runs `__.checkFormat`, `__.compile`, `__.test`, and
  `scripts/check-openspec`; the release workflow calls `ci.yml`.

## Goals / Non-Goals

**Goals:**
- A drop below a minimum fails locally and in CI with the module and
  numbers in the message.
- What's published is byte-for-byte what it would be without coverage.

**Non-Goals:**
- Line-level coverage gates per file, or a coverage badge.
- Covering code that only exists to start the JVM process (the one
  line left in `run`).

## Decisions

### 1. Mill's scoverage contrib plugin

Loaded in the build header with
`//| mvnDeps: ["com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION"]`,
so its version always matches Mill's. `ears` and `ears.cli` mix in
`ScoverageModule` with `scoverageVersion = "2.5.2"` (the current
release on Maven Central, checked 2026-11-04), and their `test`
modules mix in `ScoverageTests`.

Alternatives: Scala 3's own `-coverage-out` option with a hand-written
report (no plugin, but the report and minimum check would be ours to
maintain); sbt-style external tools (none for Mill). The plugin is
Mill's own, already used once on this build, and supplies the check.

Because `ScoverageTests` runs the ordinary `test` modules against the
instrumented copy, `./mill __.test` measures coverage with no extra
test run. The jars, POMs, and assembly come from the ordinary modules,
never the `scoverage` copies; task 1.3 checks that by looking for the
coverage runtime in the built jars.

`ears.js` is left as it is: it compiles the same sources, so the JVM
numbers cover them, and its tests keep running uninstrumented on Node.

### 2. Per-module minimums near today's numbers

`statementCoverageMin` and `branchCoverageMin` on each module, as
whole percentages, at least `ears` 95 / 90 and `ears.cli` 93 / 88.
After the new `Main` tests land, each is set to the measured value
rounded down to a whole percent, if that is higher. A failing check
names the module and both numbers (the plugin's message, confirmed in
task 1.4).

Alternatives: one floor for both (the user chose per module), or
statements only.

### 3. A seam in `Main`

```scala
def run = getArgs.flatMap(arguments => cli(arguments.toList, readAll(java.lang.System.in))).flatMap(exit)

/** Runs the checker and writes its outcome to the console; the caller exits with the code. */
def cli(arguments: List[String], stdin: Task[String]): UIO[ExitCode]

/** Reads a whole stream as UTF-8. */
def readAll(in: java.io.InputStream): Task[String]
```

`cli` is `program` followed by the two console writes, so a test runs
it under zio-test's `TestConsole` and checks `TestConsole.output`,
`TestConsole.outputErr`, and the exit code. `readAll` is tested with a
`ByteArrayInputStream` and with a stream whose `read` fails, which also
covers the standard-input error message and `describe`'s
non-missing-file case. No test touches the real `System.in` or exits
the JVM. Names are final when implementing; the shape isn't.

Alternative: exclude `run` and `readStdin` from coverage with
scoverage comments. Smaller, but leaves the wiring untested (the user
chose the seam).

### 4. CI: report, then check

In `check`, after `Test`:

1. **Report coverage**: `./mill <module>.scoverage.consoleReport` for
   each module in turn (run together, the two reports interleave), with
   its two percentage lines appended to `$GITHUB_STEP_SUMMARY` in a code
   block under a heading per module. Mill logs task output to standard
   error, so the step reads `2>&1`. It needs no `if:`: like every step,
   it runs only when `Test` passed.
2. **Check coverage**:
   `./mill ears.scoverage.validateCoverageMinimums + ears.cli.scoverage.validateCoverageMinimums`.

The report comes first so the summary is written even when the check
fails. It uses Mill's own report task rather than a published action:
the summary actions found (`irongut/CodeCoverageSummary`, last release
2022; `5monkeys/cobertura-action`, 2024; `insightsengineering/coverage-action`)
are stale or post pull request comments, which needs a write token the
workflow doesn't have. The continuous-integration spec's "Published
actions" requirement covers setup and caching steps, not this.
`check-jdk17` gets no coverage steps.

## Risks / Trade-offs

- [Instrumented code behaves differently] → Only tests run against
  it; the OpenSpec check and every published artifact use the ordinary
  build.
- [Slower tests] → The `check` job's time before and after is recorded
  in task 4.2; if it grows by more than a minute, revisit.
- [Strict compiler options reject instrumented code] → The 2026-11-03
  run compiled with them; task 1.2 confirms on the real build.
- [Stale local measurements] → The plugin's data directory
  (`out/<module>/scoverage/data.dest`) is persistent, and each test run
  adds measurement files beside the last run's (seen in task 1.3).
  CI starts from a clean checkout, so its numbers are exact; locally,
  `./mill clean ears.scoverage ears.cli.scoverage` before measuring
  gives the same.
- [A minimum blocks an unrelated change] → Lowering a minimum is a
  one-line change in `build.mill`, visible in review.

## Migration Plan

None: the next pull request after this one runs the check. Rollback
is reverting the commit.
