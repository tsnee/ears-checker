# Contributing

Thank you for helping. This page is for working on ears-checker itself; the [README](README.md)
is for using it. Report a security problem privately, as [SECURITY.md](SECURITY.md) says, not
in an issue.

## What you need

- **Linux or macOS, and Git.** `./mill` fetches the Mill version and the JDK that `build.mill`'s
  header pins, so no other Scala tooling is needed.
- **A JDK 17 or later** on the `PATH`, for the check scripts, which run the jar with `java`.
- **Node 22 or later**, for the Scala.js tests and the npm packages.
- **A C compiler** (clang), for the Scala Native builds.
- **[cmark](https://github.com/commonmark/cmark) 0.30 or later**, which the native Markdown
  tests and the native CLI load when they run (`brew install cmark`, or `apt-get install
  libcmark-dev`); nothing links it.
- **jq**, for the check scripts.
- **The npm tools the check scripts use**: the [OpenSpec CLI](https://github.com/Fission-AI/OpenSpec),
  `tsc`, `publint`, and `attw`. CI installs them from `.github/tools/package-lock.json`, and you
  can too, with no install scripts run:

  ```sh
  npm ci --prefix .github/tools --ignore-scripts
  export PATH="$PWD/.github/tools/node_modules/.bin:$PATH"
  ```

- **[Scala CLI](https://scala-cli.virtuslab.org)**, for `scripts/check-scalajs-link`.

## Building and testing

- `./mill __.compile` compiles everything; `./mill __.reformat` formats it with scalafmt, and
  `./mill __.checkFormat` checks the formatting. Warnings are errors.
- `./mill __.test` runs every test: on the JVM, and, for the library, the npm package, the
  Markdown reader, and the command line, on Node; the library, the Markdown reader, and the
  command line also run natively with Scala Native.
- It measures test coverage on the JVM. `./mill ears.scoverage.htmlReport` (or `ears.cli`,
  `ears.npm`, `ears.linkCheck`, `ears.markdown`, or `ears.releaseNotes`) writes a report under
  `out/`, and `./mill <module>.scoverage.validateCoverageMinimums` checks the module's minimums,
  `statementCoverageMin` and `branchCoverageMin` in `build.mill`. Run `./mill clean __.scoverage`
  before the tests whose coverage you report, so it's measured from a clean slate, as CI does.
- `./mill ears.cli.assembly` builds the command line's jar (`out/ears/cli/assembly.dest/out.jar`),
  `./mill ears.cli.native.nativeLink` its native binary (`out/ears/cli/native/nativeLink.dest/out`),
  and `./mill ears.cli.js.npmPackage` and `./mill ears.npm.npmPackage` the two npm packages'
  directories (`./mill show <task>` prints each one's path). To try a local build as
  `ears-check`:

  ```sh
  alias ears-check="java -XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar $PWD/out/ears/cli/assembly.dest/out.jar"
  alias ears-check="$PWD/out/ears/cli/native/nativeLink.dest/out"  # or the native binary
  ```

- `./mill __.publishLocal` publishes the next version as a snapshot (`<major>.<minor>.0-SNAPSHOT`,
  `publishVersion` in `build.mill`) to `~/.ivy2/local`.

Both npm packages are minified with [esbuild](https://esbuild.github.io), which Mill installs from
`ears/npm/tools/package-lock.json` (with the network, the first time and after a clean); the
library keeps a source map that leads back to the Scala sources, and the command line ships none.

## Check scripts

These are for working on ears-checker, not part of any package.

- `scripts/check-npm-package` builds the library's npm package, checks its size and its source
  map, lints it with publint and Are the Types Wrong, installs it into an empty project, and
  type-checks and runs a TypeScript program that uses it.
- `scripts/check-npm-cli` builds the command line's npm package and jar, installs the package into
  an empty project, and runs both on this repository's Markdown and specs and on error cases,
  failing on any difference in output, messages, or exit status.
- `scripts/check-native-cli` does the same for the native binary, on the same cases
  (`scripts/cli-cases`), after checking it links no cmark. `--binary` and `--jar` check a binary
  and jar built elsewhere.
- With `--out`, each of those three keeps the files it checked, as a release does.
- `scripts/check-coursier-app` installs `ears-check` with Coursier's `cs` from the channel file
  `coursier/apps.json`, pointed at this build, and runs it against the jar on the same cases; it
  first checks that each platform's download address names that platform's release binary.
  Without options it installs the JVM launcher; `--binary <file>` makes it install that native
  binary, and fails if Coursier installs anything else. `--repo <dir>` installs from an Ivy
  repository that already holds this build (with `--jar`, so Mill doesn't run), and
  `--cases smoke` runs four cases that work anywhere, Windows included (under Git Bash).
- `scripts/check-scalajs-link` publishes the Scala.js library locally, generates a program that
  references every public operation, and links it in release mode against only that artifact,
  the way a user's application gets it.
- `scripts/check-openspec` validates every OpenSpec file strictly and checks every requirement
  with this repository's own checker.
- `scripts/time-cli` times the jar, the Node package, and the native binary on Markdown and plain
  text made from this repository's specs, at several sizes, and prints a table; add a run to
  [docs/performance.md](docs/performance.md) when a change could affect speed.

Before calling a change done, run `./mill __.checkFormat`, then `./mill clean __.scoverage`
(measuring coverage from a clean slate, as CI does), `./mill -j 1 '__:ScalaNativeModule.compile'`,
and `./mill __.test`, every module's `validateCoverageMinimums`, and all six check scripts
(`scripts/check-coursier-app` both without options and with `--binary`).

The Scala Native modules compile one at a time because Scala Native 0.5.12's compiler plugin
isn't safe to run twice at once in one JVM
([scala-native#4950](https://github.com/scala-native/scala-native/issues/4950)). If
`./mill __.compile` or `./mill __.test` fails with an exception in the plugin's
`scalanative-prepareInterop-postinline` phase, such as "tail of empty list" or "object CFuncPtr1
does not have a member method fromScalaFunction", that's the cause: run
`./mill -j 1 '__:ScalaNativeModule.compile'`, then the command again.

## Continuous integration

GitHub Actions runs on every push to `main` and every pull request: it checks formatting,
compiles, runs every test, checks each module's coverage against its minimum (each module's
percentages are on the run's summary page), checks the changelog's form, checks both npm packages
and the Node command line against the jar on the oldest and newest Node they support, checks the
Coursier app's JVM launcher on JDK 17 and 27 and on Intel macOS and Windows, links the Scala.js
library as a user gets it, and checks the OpenSpec specs. It also builds the native binary for
Linux on x64 and Arm and for macOS on Apple silicon, each on its platform's oldest runner, and
checks it against the jar there and again on the newest, both directly and installed as the
Coursier app (on Linux x64, also with less cmark installed).

## How changes are made

- **Through OpenSpec.** Each change is planned in `openspec/changes/<name>/` (a proposal, a
  design, tasks, and the specs it adds or changes), implemented, and archived, which merges its
  specs into `openspec/specs/`. A change usually has three commits: the plan, the
  implementation, and the archive.
- **Requirements are strict EARS**, with `SHALL` in uppercase as OpenSpec's validator requires;
  `openspec/config.yaml` lists the templates and wording rules. ears-checker checks its own
  specs, so `scripts/check-openspec` fails on a requirement that doesn't fit.
- **Tests first**, with zio-test; a property wherever a law can be stated. Don't weaken a test to
  make it pass.
- **The changelog:** add a line under `Unreleased` in `CHANGELOG.md` for anything a user would
  notice. `./mill ears.releaseNotes.run check CHANGELOG.md` checks its form, and
  `./mill ears.releaseNotes.run notes <version> CHANGELOG.md docs/release-files.md` writes a
  version's release notes, as a release does.
- **Code style and layout** are in [CLAUDE.md](CLAUDE.md): strongly typed functional Scala 3, a
  pure core with effects at the edges, and the build's strict compiler and WartRemover settings.
  It's written for the AI assistant that works on this repository too, and applies to everyone.

## License

By contributing, you agree that your contribution is dedicated to the public domain under
[CC0 1.0 Universal](LICENSE), like the rest of this repository.
