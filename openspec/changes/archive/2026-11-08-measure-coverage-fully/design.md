# Design

## Context

Observations (2026-11-08, Mill 1.3.1, mill-contrib-scoverage 1.3.1, scoverage 2.5.2, Scala
3.10.0, macOS 27.2):

- **What happened:** during load-cmark-safely's checks, `./mill ears.cli.scoverage.consoleReport`
  gave 100% / 100% and `validateCoverageMinimums` passed, while the report's `statement-count`
  was 12 and its only file `OpenSpecInput.scala`. After `./mill clean ears.cli.scoverage.compile`
  and a test run, it counted 512 statements at 97.27% / 100%; the other modules, rebuilt the
  same way, matched their earlier numbers.
- **How the list is written** (the code review's reading of the Scala 3.10.0 compiler's
  `InstrumentCoverage`): each compile run deletes the old measurement files, reads the existing
  `scoverage.coverage`, keeps every earlier statement except those of files in this run (or files
  no longer on disk), adds the new ones, and rewrites the whole list in place (not atomically). So
  an incremental compile alone keeps every file's statements, which is why probes that edited one
  file, in `ears.cli` and in an excluded `ears/markdown` file, all kept 512. A 12-statement list
  needs the earlier list to have been missing or damaged when an incremental compile ran: deleted
  (Mill doesn't clear a persistent task's directory between runs), or cut off by a compile stopped
  while writing. Nothing records which.
- **A false pass without any compile:** if the list is missing and the sources are unchanged,
  Mill reuses the cached compile, scoverage's aggregator skips a data directory with no list, and
  its metrics give 1.0 for 0 statements: 100%, and the minimums pass.
- **The clean slate:** `./mill clean __.scoverage` removes every measured module's instrumented
  copy (`out/<module>/scoverage`, compiled classes and coverage data); the measured tests then
  rebuilt and ran in 10 s, and `ears.cli` counted 512 statements. CI starts from a fresh checkout,
  so it already measures this way.
- **Tried and dropped:** overriding the plugin's `scoverage` module in `Covered` with
  `zincIncrementalCompilation = false` built, counted every statement, and cost about a second per
  module; the review found it sound but not a cover for the missing-list false pass above, which
  would also have needed a guard. The owner chose the simpler rule (2026-11-08).
- **The spec:** "Coverage measured by the tests" lists five modules; `ears.releaseNotes` (added in
  add-release-notes, with minimums and a CI report) isn't among them.

## Goals / Non-Goals

**Goals:**
- No coverage report a change is judged by counts part of a module.

**Non-Goals:**
- A build that can't produce a partial report however it's driven: a developer who measures
  without the clean step can still be misled in the rare case above; the documented checks and CI
  can't.
- Finding out what removed or damaged the list.

## Decisions

### 1. Measure from a clean slate

- The checks before a change is done run `./mill clean __.scoverage`, then `./mill __.test`,
  then the coverage report and minimums. CLAUDE.md (its coverage notes and the list of checks)
  and CONTRIBUTING.md say so. About 10 s; the rest of `out/` (the Scala.js links, the native
  builds, the npm installs) is kept.
- Why not `./mill clean`: it would rebuild everything, minutes longer and needing the network,
  for state that lives only in the instrumented copies.

### 2. The spec

- `test-coverage`'s requirement lists `ears.releaseNotes`. The clean step is a working rule, not
  build behavior, so it's documented, not specified.

## Risks / Trade-offs

- **[A rule, not enforced]** Measuring without the clean step can still mislead, rarely; CI and
  the documented checks can't.
- **[Stale statements]** The list keeps statements of a file that still exists but is no longer a
  source of the module (moved elsewhere) until the data is cleared; they only lower coverage, and
  the clean step clears them.

## Open Questions

- None.
