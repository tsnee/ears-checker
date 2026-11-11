# ears-checker

Checks requirements against the EARS templates (Easy Approach to Requirements Syntax): a pure
Scala 3 classifier library for the JVM and Scala.js, and the `ears-check` command-line tool for
plain text, Markdown, and OpenSpec JSON. CC0-licensed; released on Maven Central from 0.1.0
(`io.github.tsnee`), with an unstable API while the version is 0.x. The classifier is also built
as the npm package `@tsnee/ears-checker`, and the command line, on Node, as
`@tsnee/ears-checker-cli`; neither is published yet.

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

## Build and test

- `./mill __.compile`, `./mill __.test` (JVM and Scala.js), `./mill ears.cli.test`
- `./mill __.reformat` formats with scalafmt; `./mill __.checkFormat` checks it
- Compile the Scala Native modules one at a time: `./mill -j 1 '__:ScalaNativeModule.compile'`.
  Scala Native 0.5.12's compiler plugin caches its definitions globally with no locking, so two
  native compiles in Mill's one JVM can corrupt each other's compiler state (scala-native#4950).
  It shows as an exception in the plugin's `scalanative-prepareInterop-postinline` phase, such as
  "tail of empty list" in `Types.findMember` or "object CFuncPtr1 does not have a member method
  fromScalaFunction". A failure like that isn't the code: rerun that command. `__.test`,
  `__.compile`, and Metals' compiles (`compile-full`) compile them in parallel when their
  sources have changed
- `./mill ears.cli.assembly` builds the runnable jar. Run it on any JDK from 17 with
  `java -XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar <jar> …`:
  the flag silences JDK 23+'s `sun.misc.Unsafe` warnings, and older JDKs skip it
- The `build.mill` header pins the Mill version, the JDK Mill fetches, and the JVM index. The
  index pin is there because Mill's default index can predate the pinned JDK; raise it with the
  JDK.
- `ears.js`, `ears.npm.js`, `ears.markdown.js`, and `ears.cli.js` tests run on Node;
  `ears.native`'s, `ears.markdown.native`'s, and `ears.cli.native`'s run natively (Scala Native
  0.5). Nothing links cmark: the native Markdown reader loads an installed cmark (0.30 or later)
  when it runs, choosing the file with the pure `CmarkLibrary`, so the native Markdown tests need
  it installed (`brew install cmark`; CI installs `libcmark-dev`). `ears/src-jvm` and
  `ears/cli/src-jvm` serve the JVM and Scala Native: every Java API they use works on both.
- The checker reads Markdown only through the facade in `ears/markdown` (`Markdown.sentences`),
  never with a parser directly. (Developer tools that need more than sentences, such as
  `ears.releaseNotes` with headings and source text, may use commonmark-java directly, at the
  facade's version, `commonmarkVersion` in `build.mill`.) Its conformance suite runs every
  fixture on the JVM, Node, and Scala Native; a case where the parsers could differ gets a
  fixture. Node's parser is commonmark.js (`commonmark`), pinned by
  `ears/markdown/js/package-lock.json` and installed (with the network, the first time and after
  a clean) by the Mill task `ears.markdown.js.npmDependencies`; Mill's import map points the
  tests' import at it. commonmark.js gives no inline positions, so `SourceLines` finds each
  piece's line in the paragraph's source.
- Coverage: `./mill __.test` measures `ears`, `ears.cli`, `ears.npm`, `ears.linkCheck`,
  `ears.markdown`, and `ears.releaseNotes` (scoverage, on the JVM only; the Scala.js and Scala Native modules compile the
  same sources). `ears.cli` compiles the facade's sources in and excludes them from its own
  coverage (`-coverage-exclude-files`), since `ears.markdown` measures them.
  `./mill ears.cli.scoverage.consoleReport`
  (or `htmlReport`) shows it, and `./mill ears.cli.scoverage.validateCoverageMinimums` checks the
  module's `statementCoverageMin` and `branchCoverageMin` in `build.mill`. Each test run clears
  the last run's measurements, so a report covers only the latest run (all of it after
  `__.test`). Measure from a clean slate, as CI does: `./mill clean __.scoverage` first removes
  the instrumented copies and their coverage data (seconds to rebuild, not minutes), so no statement list
  left missing or damaged by an earlier build can make a report count part of a module.
  Scoverage can't measure Scala.js-only code, so keep it thin: decisions go in code that also
  builds for the JVM.
- The npm package: `./mill ears.npm.npmPackage` builds its directory, with `main.js` minified
  by esbuild after Scala.js (`minify` in `build.mill`) and a source map that leads back to the
  Scala sources. esbuild is pinned in `ears/npm/tools/package-lock.json` and installed by
  `ears.npm.tools.npmDependencies` (`npm ci --ignore-scripts`, as every `NpmInstalled` module
  does). `scripts/check-npm-package` packs it, checks `main.js` against its size limit and that
  the map leads `classify` to `Facade.scala`, lints it with publint and Are the Types Wrong,
  installs it into an empty project, and type-checks and runs `ears/npm/smoke/consumer.ts`; it
  needs `tsc`, `publint`, and `attw` from npm. npm commands here take `--cache` in a temporary
  directory, since `~/.npm` is read-only in Claude's sandbox. A size limit is raised only on
  purpose, with the reason in the commit.
- The CLI is one program on the JVM and Node: `Cli` (`ears/cli/src`) is shared, and each
  platform's entry point supplies a `Reading`, its files and standard input: `Main` on the JVM
  (`ears/cli/src-jvm`), `NodeMain` over `node:fs` (`ears/cli/js/src`). Platform I/O goes only
  through `Reading`, and decisions stay in shared code, where they're measured. The platforms'
  read errors aren't interpreted (their kinds and wording differ and can change with a JDK or
  Node release): the message is `Cannot read <input>`, and the log entry carries the platform's
  error. Input is UTF-8 (`Utf8`, shared), and the JVM writes UTF-8 whatever the locale.
  `./mill ears.cli.js.npmPackage` builds `@tsnee/ears-checker-cli`, minified by esbuild and
  without a source map, which depends on exactly the parser version in the facade's lockfile.
  `scripts/check-npm-cli` packs it, checks that it has no map and that `main.js` is within its
  size limit, installs it, then runs it and the jar on this repository's Markdown, its specs as
  OpenSpec JSON, and the error cases, and fails on any difference in standard output, messages
  (log lines aside), or exit status; it needs `openspec`. `./mill ears.cli.native.nativeLink`
  builds the native binary (full release mode), and `scripts/check-native-cli` checks it links
  no cmark and compares it with the jar on the same cases, which both scripts source from
  `scripts/cli-cases`.
- The Coursier app: `coursier/apps.json` is a channel with one app, `ears-check`: the CLI from
  Maven Central at its latest release, its main class and JVM flags, and launcher type
  `graalvm-native-image` with `prebuiltBinaries` naming each platform's release binary, so
  Coursier installs the native binary where a release has one and falls back to the JVM
  elsewhere (the type only selects that fallback; nothing runs `native-image`). Users install it
  from `main` by URL. `scripts/check-coursier-app` checks each native address names its
  platform's release file, publishes `ears` and `ears.cli` to `~/.ivy2/local`, installs a copy
  of the descriptor pointed at that build (every native address a missing local file, so the JVM
  launcher; with `--binary`, this platform's address that binary, which must be what's
  installed), and compares it with the jar on `scripts/cli-cases`; it needs `cs`. The copy only
  changes where things come from (`ivy2Local` in front of the descriptor's repositories, this
  build's version for `latest.release`, which the descriptor must ask for, and local files for
  the addresses), so its repositories, launcher type, and flags are checked as written. The
  launcher runs with `JAVA_HOME` and `JAVA_OPTS` unset, on the `java` the jar runs on.
- The library must link on Scala.js with only its declared dependencies. zio-test brings
  scala-java-locales into every Scala.js test, which hides a missing class such as
  `java.util.Locale`. `scripts/check-scalajs-link` catches it: it publishes `ears.js` locally,
  has `ears.linkCheck` generate a program referencing every public operation from the
  library's SemanticDB, and links that with `scala-cli` in release mode against only the
  published artifact. A new public operation needs nothing; one the generator can't reference
  yet (an overload, `using` parameters, varargs) fails the check by name. Supporting a new shape
  means a case in `LinkCheck` and a member of `ears.linkCheck.fixture`, whose generated program
  the script compiles. It needs `scala-cli` and `jq`. Code that differs by platform goes in
  `ears/src-jvm` and `ears/js/src`.
- Tests that read files the build passes in `forkEnv` read the variable through `Live`
  (zio-test's `TestSystem` has no environment), and run only through `./mill`: Metals' test
  runner doesn't pass `forkEnv`. Metals can't run the library's suites either: they're shared
  with `ears.js.test`.

Run `./mill __.checkFormat`, `./mill clean __.scoverage`, `./mill -j 1
'__:ScalaNativeModule.compile'`, then `./mill __.test`, every module's
`validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`,
`scripts/check-native-cli`, `scripts/check-coursier-app` (and with `--binary` the native
binary), `scripts/check-scalajs-link`, and `scripts/check-openspec` before calling any change
done (as CONTRIBUTING.md says), and report the coverage.
Warnings are errors.

CI (`.github/workflows/ci.yml`) runs the same checks on every push to `main` and every pull
request: `./mill __.checkFormat`, the compile (the Scala Native modules one at a time),
`./mill __.test`, the changelog's form
(`ears.releaseNotes.run check CHANGELOG.md`), `scripts/check-npm-package`,
`scripts/check-npm-cli`, `scripts/check-coursier-app` (the JVM launcher, on JDK 27),
`scripts/check-scalajs-link`, the coverage minimums (with each module's percentages on the run's
summary page), and `scripts/check-openspec`. A second job, `check-jdk17`, runs on the oldest
supported runtimes: `scripts/check-openspec` with JDK 17 starting the jar,
`scripts/check-npm-package` and `scripts/check-npm-cli` on the oldest Node the packages declare,
and `scripts/check-coursier-app` with the JVM launcher on JDK 17. Coursier's CLI comes from
`coursier/setup-action`. The reusable `native.yml` builds the native binary for linux-x64,
linux-arm64, and macos-arm64, each on its platform's oldest runner (`ubuntu-22.04`,
`ubuntu-22.04-arm`, `macos-15`) with `scripts/check-native-cli --out`, then checks the same
binary and jar again on the newest (`ubuntu-26.04`, `ubuntu-26.04-arm`, `macos-26`) with
`--binary --jar`, and with `scripts/check-coursier-app --binary --jar`, and on linux-x64 with
less cmark installed. It publishes nothing. On a release
tag every job builds at the tag's version and keeps the files it checked as `release-*`
artifacts for the release.

## Releasing

Releases go to Maven Central and GitHub Releases from `.github/workflows/release.yml`; the
version comes from the tag (`EARS_RELEASE_VERSION`). Every other build is the next minor version
as a snapshot: `-SNAPSHOT` for Maven and `-dev` for npm (`publishVersion` and
`ears.npm.npmVersion` in `build.mill`). Bump it after each minor release; a patch release doesn't
change it.

`CHANGELOG.md` says what changed: `## Unreleased`, then one `## <major>.<minor>.<patch> -
<YYYY-MM-DD>` per release, newest first, each with `### Added`, `### Changed`, and `### Fixed` as
needed. Each change adds its lines under Unreleased in its implementation commit. A release's
notes are its section, followed by `docs/release-files.md` (what each attached file needs and
how to check it), with `{version}` replaced.

1. Rename `Unreleased` to the version and date, open a new empty `Unreleased`, update every
   release version the README names (its current release, quick start, download URLs, and
   library coordinates; not "0.1.1 or newer", which is a minimum), and merge everything for the
   release to `main`; CI must pass.
2. Run `/security-review` on the changes since the last release tag, and fix or knowingly accept
   each finding before tagging. (Each batch is also reviewed before it merges.)
3. The owner pushes a tag `v<major>.<minor>.<patch>` on `main` (only admins may create `v*` tags).
4. The `tag` job checks the tag is a version on `main` and writes the release notes (a missing
   section fails here), and `checks` runs the whole CI workflow (`ci.yml`) on it; then `publish`
   waits for the owner's approval (the `release` environment, which holds the Sonatype token and
   signing key). It builds with no secrets, then signs and uploads.
5. `publish` uploads to the Central Portal for review (`--shouldRelease false`); then
   `draft-release`, the only job with a write token, creates a draft GitHub Release with the
   notes, the files the checks passed on, `SHA256SUMS`, and an attestation for each file.
6. The owner publishes the draft release (or deletes it), then inspects the deployment on the
   Central Portal and clicks Publish (or Drop to discard it). In that order, the Coursier app,
   which installs Maven Central's latest release, finds that release's native binaries already
   attached; installed in between, it falls back to the JVM. A release that exists for the
   tag fails `draft-release` untouched; delete the draft and rerun the job to redo it. Once
   `publish` has succeeded, rerun only failed or single jobs, never the whole run: that reruns
   `publish`, which uploads to the Central Portal again.

Published versions can't be deleted or reused; fix a bad release with a new patch version.

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
  - `ears/src` holds the sources for both platforms; `ears/src-jvm` and `ears/js/src` hold the
    few that differ (`LetterCase`)
  - `ears.js` — the same sources and tests built with Scala.js and run on Node
  - `ears.native` — the same sources (with `ears/src-jvm`) and tests built with Scala Native
  - `ears/cli/` — the `ears-check` command (ZIO, decline, zio-json, commonmark-java), package
    `io.github.tsnee.ears.cli`: `src` is the shared program (`Cli`, `Reading`), `src-jvm` the
    entry point (`Main`) of the JVM and Scala Native, and `js/src` Node's (`NodeMain`), with
    `js/package/` the npm package's README; `test/src` runs on every platform, `test/src-jvm`
    only on the JVM, where `OwnSpecsSuite` checks this repository's own specs
  - `ears/npm/` — the npm package, package `io.github.tsnee.ears.npm`: `src` is the pure core
    (`Api`, built and measured on the JVM), `js/src` the Scala.js export (`Facade`), `package/`
    the hand-written `index.d.ts` and README, `smoke/` the TypeScript consumer, and `tools/`
    the esbuild lockfile both packages are minified with (`ears.npm.tools`)
  - `ears/linkCheck/` — the link-check generator (ZIO, scalameta's `semanticdb-shared`),
    package `io.github.tsnee.ears.linkcheck`: `LinkCheck` picks out the public operations and
    writes the program; `Main` reads SemanticDB and writes the file; `fixture/` has one
    operation of every shape it references
  - `ears/markdown/` — the Markdown facade, package `io.github.tsnee.ears.markdown`: `src` is
    shared (`Markdown`, `Extraction`, and the line helpers `CmarkLines` and `SourceLines`), and each
    platform has its `MarkdownParser`: `src-jvm` (commonmark-java), `js/src` (commonmark.js),
    `native/src` (cmark); `test/src` is the conformance suite. Not published: `ears.cli` compiles the shared and JVM sources in
  - `ears/releaseNotes/` — the release-notes tool (ZIO, commonmark-java), package
    `io.github.tsnee.ears.releasenotes`: `Changelog` checks `CHANGELOG.md`'s form and writes a
    version's notes; `Main` is `check` and `notes`. Not published
- `openspec/` — specs `ears-classifier`, `ears-cli`, `markdown-input` (the Markdown facade, the
  same on every platform), `npm-package`, `cli-npm-package` (the CLI's npm package),
  `cli-native-binary` (the native binary), `openspec-check` (the OpenSpec gate:
  `scripts/check-openspec` and its hook), and `continuous-integration` (the GitHub
  Actions workflow), `release-publishing` (the Maven Central release workflow), `github-release`
  (the changelog and the draft GitHub Release), `test-coverage` (the coverage minimums), and the
  archived changes that built them
- `scripts/` — `check-openspec` and the Claude Code hook that runs it, `check-npm-package`,
  `check-npm-cli`, `check-native-cli`, `check-coursier-app` (all three sourcing `cli-cases`),
  `check-scalajs-link`, and `time-cli`, which times the three builds on generated inputs for
  `docs/performance.md`
- `coursier/apps.json` — the Coursier channel users install `ears-check` from
- `.github/` — the CI workflow and the native one it calls (`native.yml`), the release workflow,
  Dependabot's weekly updates to their action pins, and `tools/`, the lockfile CI installs its
  npm tools from (`npm ci --ignore-scripts`)
- `CONTRIBUTING.md` — building, testing, and how changes are made, for contributors;
  `SECURITY.md` — how to report a vulnerability (GitHub's private reporting) and what's in scope
- `CHANGELOG.md` — what changed in each release; `docs/release-files.md` — the files
  description every release's notes end with
- `docs/performance.md` — every `scripts/time-cli` run, newest first, with its commit and machine
- `docs/json-output.md` — the reference for `--output json`; a change to the document updates it,
  and removing or renaming a field, changing its type or meaning, or adding a location type
  bumps the document's `version`
- `docs/worklog.md` — dated work log; `docs/roadmap.md` — planned changes
