# Design

## Context

Measured on 2026-11-02 with the current `ears.cli.assembly` jar:

| `java` | Command line | Result |
|---|---|---|
| Temurin 17.0.21 | `--sun-misc-unsafe-memory-access=allow -jar …` | JVM doesn't start: `Unrecognized option` |
| Temurin 17.0.21 | `-jar …` | runs, no warnings |
| Temurin 17.0.21 | `-XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar …` | runs, no warnings |
| OpenJDK 27.0.1 | `-jar …` | runs, four `WARNING:` lines: `sun.misc.Unsafe::objectFieldOffset` called by `scala.runtime.LazyVals$` |
| OpenJDK 27.0.1 | `-XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar …` | runs, no warnings |

`JDK_JAVA_OPTIONS` doesn't help: JDK 17 still rejects the flag when
it comes from there. The jar's `scala.runtime.LazyVals` use comes
from the Scala 3 runtime and libraries compiled against it, so the
build can't remove it.

Where the jar is started today: `scripts/check-openspec` (the hook
and CI both run it), the README's alias, and CLAUDE.md's run
instructions. CI's only job runs on Temurin 27 (`actions/setup-java`)
with Mill on its own JDK 27.

## Goals / Non-Goals

**Goals:**
- One command line for starting the jar that works on every JDK from
  17 on, used everywhere this repository documents or runs it.
- CI fails if that stops being true on JDK 17.

**Non-Goals:**
- Running the test suites on JDK 17. Mill runs them on its JDK 27,
  and `-release:17` already rejects APIs newer than Java 17 at
  compile time.
- Non-HotSpot JVMs (OpenJ9). The README names HotSpot-based JDKs.
- Launchers for packaged CLIs. The packaging changes reuse this
  command line (Coursier's `javaOptions`, `cs launch --java-opt`).

## Decisions

### 1. `-XX:+IgnoreUnrecognizedVMOptions` before the flag

```sh
java -XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar "$jar" …
```

JDK 17 through 22 skip the option they don't know; JDK 23 and newer
apply it. The line is fixed, so it works in static launchers (an
alias, a Coursier app descriptor) that can't test the JDK first. The
user chose this on 2026-11-02.

Alternative: probe with `java --sun-misc-unsafe-memory-access=allow
-version` and add the flag only if that succeeds. Nothing is masked
and it costs about 20 ms, but every launcher needs its own probe, and
static launchers can't run one.

Trade-off: any other unrecognized JVM option on that command line is
ignored too, so a typo there goes unnoticed. The line has only these
two options, and a comment in the script says why the first is there.

### 2. Keep `allow`

`allow` keeps the warnings out on JDK 23 and newer and keeps the jar
running if a later JDK denies `sun.misc.Unsafe` memory access by
default. `warn` would bring the warnings back.

### 3. A second CI job on JDK 17

```text
check-jdk17: checkout -> setup-java (Temurin 17) -> setup-node (26)
  -> coursier/cache-action -> npm install -g @fission-ai/openspec@<pin>
  -> scripts/check-openspec
```

Mill still builds the jar on its own JDK 27; the runner's `java`,
which the script uses to start the jar, is Temurin 17. The job runs
next to `check` with the same triggers, concurrency, permissions,
`persist-credentials: false`, and timeout (top-level settings and
copied job settings). `coursier/cache-action` keys on the job name,
so this job gets its own cache; its fallback key restores `check`'s.

The job name becomes a second required status check on `main`, set
with `gh` after its first run on the pull request (so GitHub knows the
check name) and before the merge, so the archive on the branch
records it done.

Alternative: a matrix over `java-version: [17, 27]` for the whole
job. That would run the format check, compile, and tests twice for no
gain, since none of them use the runner's `java`.

### 4. Docs use the same line

The README alias and CLAUDE.md's run instructions use Decision 1's
line, and the README's "needs JDK 23 or newer; leave it out" note is
replaced by one sentence on what the two options do.

## Risks / Trade-offs

- [A later JDK drops `-XX:+IgnoreUnrecognizedVMOptions`] → It has been
  in HotSpot since JDK 6 and the JDK 17 CI job would fail first; the
  probe in Decision 1's alternative is the fallback.
- [Typos masked on the command line] → Accepted; see Decision 1.
- [The JDK 17 job's first run downloads JDK 17 and the dependencies]
  → Later runs use the cache, and runs are free on a public
  repository.
