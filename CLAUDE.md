# ears-checker

Checks requirements against the EARS templates (Easy Approach to Requirements Syntax): a pure
Scala 3 classifier library for the JVM and Scala.js, and the `ears-check` command-line tool for
plain text, Markdown, and OpenSpec JSON. CC0-licensed; unreleased (0.1.0 will be the first
release, with an unstable API).

This repository was extracted from `spec-mutation-lab` with its history. Its first OpenSpec
change, `bootstrap-ears-checker`, makes it build, test, check its own specs, and publish locally
on its own. If that change is still under `openspec/changes/`, start there:
`/opsx:apply bootstrap-ears-checker`.

## Workflow

- Develop through OpenSpec: propose a change (`/opsx:propose` or `/opsx:new`), implement it
  with `/opsx:apply`, check it with `/opsx:verify`, and archive it with `/opsx:archive`.
  Don't start feature code without a change.
- Every requirement is strict EARS, with `SHALL` in uppercase because OpenSpec's validator
  requires it. The templates and wording rules are in `openspec/config.yaml` under
  `rules.specs`, which OpenSpec feeds into every spec it writes.
- Every OpenSpec file must pass strict validation with no warnings, and every requirement must
  pass this repository's own checker: `scripts/check-openspec` runs `openspec validate --all
  --strict` and `--archived --strict`, builds `ears.cli` from source, and checks every active
  change and spec with it in one run. A Claude Code PostToolUse hook (`.claude/settings.json` →
  `scripts/claude-openspec-hook`) runs it after any edit under `openspec/` and reports failures
  back to Claude, including each EARS finding and its fix.
- At the end of each working session, add a dated line to `docs/worklog.md`.
- Another repository (`spec-mutation-lab`) runs this checker at a pinned version from
  `~/.ivy2/local` until 0.1.0 is on Maven Central. After a change that should reach it, run
  `./mill __.publishLocal`.

## Build and test

- `./mill __.compile`, `./mill __.test` (JVM and Scala.js), `./mill ears.cli.test`
- `./mill __.reformat` formats with scalafmt; `./mill __.checkFormat` checks it
- `./mill ears.cli.assembly` builds the runnable jar; run it with
  `java --sun-misc-unsafe-memory-access=allow -jar <jar> [options] [FILE...]`
- Mill 1.3.1 (pinned in the `build.mill` header), Scala 3.10.0, JDK 27 (Mill fetches it; the
  header pins JVM index 0.0.5 because Mill 1.3.1's default index predates JDK 27)
- `ears.js` tests run on Node.

Run `./mill __.test` before calling any change done. Warnings are errors.

## Code style

Strongly typed functional Scala 3, written for a reader who knows Haskell and PureScript well.

- Pure core, effects at the edges. The `ears` library has no IO and no ZIO, and must stay free
  of JVM-only APIs so `ears.js` builds. Files, processes, and the console belong in `ears.cli`
  (ZIO).
- Model the domain with `enum` ADTs, opaque types for identifiers, and total functions.
  No `null`, no exceptions for control flow, no `var`, no `asInstanceOf`, and no `Any` except
  as ZIO's empty environment (`ZIO[Any, E, A]`). Return `Either` / `Option` for failure.
- The build enforces most of this; see `Base` in `build.mill`. The compiler runs with explicit
  nulls, `-source:future`, all-match exhaustivity, and extra `-W` checks, all as errors.
  WartRemover's Unsafe warts are errors too (`var`, `null`, `throw`, `return`, casts, `head`,
  `Option.get`, default arguments, …). Modules with no ZIO also ban inferred `Any`; ZIO modules
  and zio-test specs can't, because of `ZIO[Any, E, A]`. Don't silence a wart with
  `@SuppressWarnings` without saying why in a comment.
- `-language:strictEquality` is on: derive `CanEqual` for types you compare with `==`.
- Packages: `io.github.tsnee.ears` for the library, `io.github.tsnee.ears.cli` for the CLI.
  Source directories mirror the package.
- scalafmt sorts and groups imports (`./mill __.reformat`). Unused or duplicate imports fail the
  build.
- Use Scala 3 syntax: braceless, `given`/`using`, extension methods, `derives`.
- Prefer small composable functions over classes with state. Typeclasses over inheritance.
- Data plus extensions: enums and case classes are plain data with no methods. Operations on one
  primary value are extension methods in that type's companion object (found without imports,
  so caller wildcard imports can't shadow them); constructors and symmetric operations are
  companion functions (`Template.of`). No public top-level `def`s: a wildcard import such as
  `zio.test.*` shadows them. Long logic can live in a `private[pkg]` object in its own file.
- Tests: zio-test only, with `check` and `Gen` properties wherever a law or equivalence can be
  stated. Every diagnostic kind should have a test with a concrete witness sentence.
- Don't weaken a test to make it pass. Change a test only when it contradicts the spec, and say
  so in the change.
- Add a library only when it earns its place; check Maven Central for the current version.
  ZIO is the effect system.
- Keep examples and test fixtures in generic domains (scheduling, e-commerce, access control,
  SaaS billing, public specs such as RFCs).

## Layout

- `ears/` — the classifier library, package `io.github.tsnee.ears`: cats-parse tokenizer,
  lenient skeleton parse, classifier with fixable diagnostics, rendering
  - `ears.js` — the same sources and tests built with Scala.js and run on Node
  - `ears/cli/` — the `ears-check` command (ZIO, decline, zio-json, commonmark-java), package
    `io.github.tsnee.ears.cli`; `OwnSpecsSuite` checks this repository's own specs
- `openspec/` — specs `ears-classifier` and `ears-cli`, and the archived changes that built them
- `scripts/` — `check-openspec` and the Claude Code hook that runs it
- `docs/worklog.md` — dated work log

## Likely next steps

Packaging, each as its own OpenSpec change: GitHub Actions CI; Maven Central libraries (JVM and
Scala.js); the CLI as a Coursier app and GraalVM binaries; an npm package; Scala Native once
Markdown input no longer needs commonmark-java. Also deferred from the first code review: choose
the checker's JVM flag by JDK version (`--sun-misc-unsafe-memory-access` needs JDK 23 or newer).
