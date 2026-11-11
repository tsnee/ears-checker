# Design

## Context

See proposal.md for why. The extraction kept these paths, with the history of each:

```text
ears/  (library, ears.js sources, cli/)
openspec/specs/      ears-classifier, ears-cli
openspec/changes/archive/
                     add-ears-checker,
                     batch-ears-check
openspec/config.yaml, build.mill, mill,
.scalafmt.conf, .gitignore, .mcp.json,
.claude/, scripts/
```

Notes specific to the other project were rewritten out of every commit before the first push. The history will become public at 0.1.0, so new files must not bring any back; task 4.2 checks, using a list of terms kept outside the repository.

`build.mill` today: the header (Mill 1.3.1, JDK 27, JVM index 0.0.5), `Base` with the strict compiler flags and WartRemover, `ZioSpecs`, the wart lists, then `object semantics` (sources missing here) and the `ears` tree: `ears`, `ears.js` (Scala.js 1.22.0, same sources), `ears.cli` (main class `io.github.tsnee.ears.cli.Main`).

## Goals / Non-Goals

**Goals:**

- The repository builds, tests, and checks its own specs with nothing from the repository it was extracted from.
- `cs launch io.github.tsnee:ears-checker-cli_3:0.1.0-SNAPSHOT` works after `publishLocal`: that is the contract with the downstream project.

**Non-Goals:**

- Publishing anywhere but `~/.ivy2/local`, signing, CI, binaries, npm. Packaging changes come next.
- Changing any spec, source file, or test.

## Decisions

### 1. One shared publishing trait

```scala
trait Published extends PublishModule {
  def publishVersion = "0.1.0-SNAPSHOT"
  def pomDescription: String
  def pomSettings = PomSettings(
    description = pomDescription,
    organization = "io.github.tsnee",
    url = "https://github.com/tsnee/"
      + "ears-checker",
    licenses = Seq(License.`CC0-1.0`),
    versionControl = VersionControl
      .github("tsnee", "ears-checker"),
    developers = Seq(Developer("tsnee",
      "Tom Snee", "https://github.com/tsnee"))
  )
}
```

`ears` and `ears.js` mix it in with `artifactName` `ears-checker` (Mill adds `_3`, and `_sjs1_3` for Scala.js); `ears.cli` with `ears-checker-cli`. Test modules don't, so `__.publishLocal` skips them. Each published module sets its own `pomDescription`, so the library and the CLI read differently on Maven Central; `ears.js` takes its name and description from `ears`, as it already does its sources and dependencies, so the two can't drift apart. (Added after the code review.) If Mill 1.3.1's API differs in a detail (for example the license constant's name), follow Mill's docs and keep the coordinates.

### 2. `scripts/check-openspec` stays as it is

It builds `ears.cli` from source and checks every change and spec in one run. For the checker's own repository that is right: its specs are checked by the code they describe.

### 3. The license file is the official text

`LICENSE` is the CC0 1.0 Universal legal code as published by Creative Commons (https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt), unmodified.

### 4. Compile for Java 17

`Base` passes `-release:17`. Scala 3.10's standard library is Java 17 bytecode, so no user can run the artifacts on an older JVM anyway. Without the flag the class files already said 17, but the code was checked against the build's JDK 27 API, so a newer call would compile and then fail at runtime on JDK 17. (Added after the code review.)

## Risks / Trade-offs

- [`OwnSpecsSuite` reads `openspec/` through `OPENSPEC_DIR`, set in `build.mill`] → Keep that `forkEnv` when editing the build; the suite then checks this repository's specs.
- [The sandbox may block `~/.ivy2`] → The `claude` nono profile 1.2.0 grants it; if publishing is denied, stop and say so.
- [A private-history mention comes back in a new file] → The audit in task 4.2 blocks the push.
