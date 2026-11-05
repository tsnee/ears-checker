# continuous-integration Specification

## Purpose

Checks every push to `main` and every pull request on GitHub, with the same build, test, format,
and OpenSpec checks run locally, and limits what that automation may do.

## Requirements

### Requirement: Checks on pushes to main
When a commit is pushed to the main branch, the CI workflow SHALL run every check on that commit.

#### Scenario: Push to main
- **WHEN** a commit that changes `README.md` is pushed to `main`
- **THEN** a CI run starts for that commit and runs the format, compile, test, and OpenSpec checks

### Requirement: Checks on pull requests
When a pull request is opened or updated, the CI workflow SHALL run every check on the result of merging it.

#### Scenario: Pull request opened
- **WHEN** a pull request from branch `add-sarif-output` into `main` is opened
- **THEN** a CI run starts for the pull request and runs the format, compile, test, and OpenSpec checks

#### Scenario: Pull request updated
- **WHEN** a new commit is pushed to the branch of an open pull request
- **THEN** a CI run starts for the updated pull request

### Requirement: Superseded pull request runs
When a pull request is updated while its previous run is in progress, the CI workflow SHALL cancel that previous run.

#### Scenario: Two quick pushes
- **WHEN** a second commit is pushed to a pull request's branch while the run for the first commit is still in progress
- **THEN** the run for the first commit is cancelled and the run for the second commit continues

### Requirement: Compile failures
If any module fails to compile, then the CI workflow SHALL fail the run.

#### Scenario: Compiler warning
- **WHEN** a pushed commit adds an unused import to a source under `ears/src/`
- **THEN** the run fails, because warnings are compile errors

### Requirement: Test failures
If any test fails on the JVM, on Node, or on Scala Native, then the CI workflow SHALL fail the run.

#### Scenario: Failing JVM test
- **WHEN** a pushed commit makes one test in `ears.cli.test` fail
- **THEN** the run fails

#### Scenario: Failing Node test
- **WHEN** a pushed commit makes a test fail only in the Scala.js build `ears.js.test`
- **THEN** the run fails

#### Scenario: Failing Scala Native test
- **WHEN** a pushed commit makes a conformance fixture fail only in the Scala Native build `ears.markdown.native.test`
- **THEN** the run fails

### Requirement: Format failures
If any Scala source differs from its scalafmt formatting, then the CI workflow SHALL fail the run.

#### Scenario: Unformatted source
- **WHEN** a pushed commit indents one line of a source under `ears/cli/src/` by three spaces instead of two
- **THEN** the run fails

### Requirement: OpenSpec check failures
If the repository's OpenSpec check fails, then the CI workflow SHALL fail the run.

#### Scenario: Requirement that isn't EARS
- **WHEN** a pushed commit adds a change whose requirement reads "The scheduler MUST list upcoming events."
- **THEN** the run fails and its log shows the weak-modal finding

#### Scenario: Strict validation warning
- **WHEN** a pushed commit adds a requirement whose description is longer than 500 characters
- **THEN** the run fails, because strict validation treats the warning as an error

### Requirement: Successful runs
When every check passes, the CI workflow SHALL report the run as successful.

#### Scenario: Clean commit
- **WHEN** a pushed commit compiles, passes every test, is formatted, and passes the OpenSpec check
- **THEN** the run succeeds and the commit shows a passing check on GitHub

### Requirement: Linux runners
The CI workflow SHALL run on GitHub-hosted Linux runners.

#### Scenario: Runner for a run
- **WHEN** a CI run starts
- **THEN** its job runs on a GitHub-hosted Ubuntu runner and on no macOS or Windows runner

### Requirement: Published actions
The CI workflow SHALL take every setup and caching step from a published GitHub Action.

#### Scenario: Setting up the JDK
- **WHEN** the workflow needs a JDK on the runner
- **THEN** it installs it with a published action rather than with its own download script

### Requirement: Pinned actions
The CI workflow SHALL reference each action by a full commit SHA.

#### Scenario: Action reference
- **WHEN** the workflow uses the checkout action
- **THEN** it refers to it by a 40-character commit SHA, not by a tag or branch name

### Requirement: Read-only token
The CI workflow SHALL limit its GitHub token to reading the repository's contents.

#### Scenario: Token permissions
- **WHEN** a CI run starts
- **THEN** its token can read the repository and can't push commits, create releases, or write packages

### Requirement: No publishing
The CI workflow SHALL publish no artifact.

#### Scenario: Run on main
- **WHEN** a CI run for a push to `main` succeeds
- **THEN** nothing is published to Maven Central, npm, or GitHub Releases

### Requirement: Pinned OpenSpec CLI
The CI workflow SHALL run the OpenSpec check with a pinned version of the OpenSpec CLI.

#### Scenario: New OpenSpec CLI release
- **WHEN** a newer OpenSpec CLI is published to npm
- **THEN** CI runs keep using the pinned version until the workflow is changed

### Requirement: OpenSpec check on JDK 17
The CI workflow SHALL run the OpenSpec check with JDK 17 as the Java runtime, as well as with the JDK the build uses.

#### Scenario: JDK-23-only flag
- **WHEN** a pushed commit makes `scripts/check-openspec` always start the EARS checker with `--sun-misc-unsafe-memory-access=allow`
- **THEN** the OpenSpec check fails on JDK 17 and the run fails

#### Scenario: Clean commit on both JDKs
- **WHEN** a pushed commit passes the OpenSpec check on JDK 17 and on JDK 27
- **THEN** the run succeeds

### Requirement: Coverage failures
If the coverage check fails, then the CI workflow SHALL fail the run.

#### Scenario: Coverage below a minimum
- **WHEN** a pull request's tests pass but leave `ears.cli`'s statement coverage below its minimum
- **THEN** the run fails

### Requirement: Coverage summary
When the tests have run, the CI workflow SHALL write each module's statement and branch coverage to the run's summary page.

#### Scenario: Passing run
- **WHEN** a run's tests and coverage check pass
- **THEN** the run's summary page shows the statement and branch percentages for `ears` and `ears.cli`

#### Scenario: Coverage below a minimum
- **WHEN** a run's tests pass and its coverage check fails
- **THEN** the run's summary page still shows each module's percentages

### Requirement: npm package failures
If the npm package fails to build, to install into an empty project, or to classify a sentence on Node, then the CI workflow SHALL fail the run.

#### Scenario: Broken export
- **WHEN** a pushed commit renames the exported `classify` function
- **THEN** the run fails at the npm package check

### Requirement: TypeScript declaration failures
If a TypeScript consumer of the installed npm package fails to type-check against the package's declarations under Node or bundler module resolution, then the CI workflow SHALL fail the run.

#### Scenario: Declaration out of step
- **WHEN** a pushed commit removes `template` from the declared result of a matching sentence
- **THEN** the run fails at the npm package check

### Requirement: Package lint failures
If publint or Are the Types Wrong reports a problem with the packed npm package, then the CI workflow SHALL fail the run.

#### Scenario: Missing declaration file
- **WHEN** a pushed commit stops copying `index.d.ts` into the package
- **THEN** the run fails at the npm package check

### Requirement: npm package check on the oldest Node
The CI workflow SHALL run the npm package check on the oldest Node version the package declares, as well as on the Node version the build uses.

#### Scenario: Node 22
- **WHEN** a CI run checks a pull request
- **THEN** the npm package check runs once on Node 22 and once on Node 26

### Requirement: Scala.js library link failures
If a Scala.js program whose only dependency is the Scala.js library, as the build publishes it, fails to link for release, then the CI workflow SHALL fail the run.

#### Scenario: JVM-only reference
- **WHEN** a pushed commit makes `Requirement.render` call `toUpperCase(java.util.Locale.ROOT)`
- **THEN** the run fails at the Scala.js library check, which reports the reference to `java.util.Locale`

### Requirement: Every public operation linked
When the CI workflow checks the Scala.js library, the CI workflow SHALL link a program that reaches every public operation of the library.

#### Scenario: New extension method
- **WHEN** a pushed commit adds a public extension method to `Requirement`, and nothing else
- **THEN** the Scala.js library check links that method's body too, with no other file changed

#### Scenario: Private member
- **WHEN** a pushed commit adds a method to a `private[ears]` object
- **THEN** the program the check links doesn't reference that method directly

### Requirement: Operations the check can't reach
If the library has a public operation that the Scala.js library check can't reference, then the CI workflow SHALL fail the run and name that operation.

#### Scenario: Overloaded operation
- **WHEN** a pushed commit adds a second public `classify` method to `Requirement`, overloading the first
- **THEN** the run fails at the Scala.js library check, naming `Requirement.classify` as an operation it can't reference
