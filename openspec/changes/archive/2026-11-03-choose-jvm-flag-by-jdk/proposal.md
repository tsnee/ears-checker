# Proposal

## Why

`scripts/check-openspec` always starts the checker's jar with
`--sun-misc-unsafe-memory-access=allow`, a flag JDK 23 added. On
JDK 17 through 22 the JVM refuses to start with it (`Unrecognized
option`), so the OpenSpec check fails there, although the jar is
built for Java 17 (`-release:17`) and runs fine on JDK 17 without the
flag. Nothing catches this: CI and this machine both run JDK 27. The
flag is still worth passing where it works: on JDK 27 the jar prints
four `sun.misc.Unsafe` warnings without it (from `scala.runtime.LazyVals`),
and a later JDK may refuse that access unless the flag allows it.

## What Changes

- **The check script** starts the jar with
  `-XX:+IgnoreUnrecognizedVMOptions` before the flag, so JDK 17
  through 22 skip the flag and run, and JDK 23 and newer apply it and
  stay quiet. One fixed command line, so the same form works in
  launchers that can't test the JDK first.
- **A JDK 17 CI job.** Next to the existing job, a second job runs
  `scripts/check-openspec` with Temurin 17 as the runner's `java`, so
  a JDK-23-only flag (or a newer class file) fails CI.
- **Docs.** The README's `ears-check` alias and CLAUDE.md's run
  instructions use the same command line, so they work on either JDK
  without editing; the deferred item leaves Likely next steps; the CI
  workflow's comment about the runner JDK is corrected.

Not in this change: running the test suites on JDK 17 (Mill runs them
on its own JDK 27; `-release:17` already enforces the Java 17 API),
and launchers for packaged CLIs (Coursier app, GraalVM binaries),
which the packaging changes add.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `openspec-check`: the check runs on JDK 17 through 22, and keeps
  the `sun.misc.Unsafe` warnings out of its output on JDK 23 and newer.
- `continuous-integration`: CI also runs the OpenSpec check on JDK 17.

## Impact

- `scripts/check-openspec`, `.github/workflows/ci.yml`, `README.md`,
  `CLAUDE.md`.
- A second CI job per run; free on a public repository.
- No change to the build, the sources, or the tests.
