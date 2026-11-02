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
If any test fails on the JVM or on Node, then the CI workflow SHALL fail the run.

#### Scenario: Failing JVM test
- **WHEN** a pushed commit makes one test in `ears.cli.test` fail
- **THEN** the run fails

#### Scenario: Failing Node test
- **WHEN** a pushed commit makes a test fail only in the Scala.js build `ears.js.test`
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
