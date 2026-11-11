# Proposal

## Why

Test coverage is measured only by hand: on 2026-11-03 a throwaway scoverage setup gave `ears` 95.5% of statements and 90.9% of branches, and `ears.cli` 93.0% and 88.0%. Nothing stops a change from lowering either. The gaps are concentrated in `Main.scala` (56%): the console and exit-code wiring, the standard-input error path, and error causes other than a missing file are untested.

## What Changes

- **Coverage in the build.** `ears` and `ears.cli` measure statement and branch coverage whenever their tests run, and a build task fails when a module drops below its minimum: `ears` 95% / 90%, `ears.cli` 93% / 88% (statements / branches), raised to whatever the new tests reach, rounded down to a whole percent. Published artifacts stay uninstrumented.
- **Coverage in CI.** The `check` job fails below a minimum and writes each module's percentages to the run's summary page.
- **`Main` gets a seam.** The console and exit-code wiring becomes a function of the arguments and standard input that writes through ZIO's console, so tests can run it with a test console; `run` only supplies the real arguments and standard input and exits.
- **Tests for `Main`'s error paths:** standard input that can't be read, and a file argument that exists but can't be read (a directory).
- **Docs.** CLAUDE.md and the README list the coverage check beside the other checks.

Not in this change: coverage of the Scala.js build (it compiles the same sources as `ears`, which the JVM measures), coverage reports stored as artifacts or posted on pull requests, and logging in `Main`.

## Capabilities

### New Capabilities

- `test-coverage`: how the build measures test coverage, the per-module minimums it enforces, and keeping measurement out of published artifacts.

### Modified Capabilities

- `continuous-integration`: the CI workflow fails when coverage is below a minimum and reports coverage on the run's summary page.
- `ears-cli`: unreadable standard input and unreadable (not missing) files get explicit scenarios under the unreadable-input requirements; the behavior already exists, and the new tests pin it down.

## Impact

- `build.mill`: Mill's scoverage contrib plugin (`mill-contrib-scoverage` at Mill's own version, loaded in the build header) on `ears` and `ears.cli`, with their minimums.
- `ears/cli/src/.../Main.scala` and `MainSuite.scala`.
- `.github/workflows/ci.yml`: coverage steps in `check`. The release workflow runs them too, since it calls `ci.yml`.
- `CLAUDE.md`, `README.md`.
- Instrumented test runs are slower; the `check` job's time is measured before and after.
