## ADDED Requirements

### Requirement: Native CLI differences on every platform

If the native binary for linux-x64, linux-arm64, or macos-arm64 writes different standard output or messages than the JVM CLI's jar on the same arguments and input, or exits with a different status, on its platform's oldest or newest runner, then the CI workflow SHALL fail the run.

#### Scenario: A change that breaks only macOS

- **WHEN** a pushed commit makes only the macos-arm64 binary exit with a different status for a failing file
- **THEN** the run fails at that platform's native CLI check, naming the input whose statuses differ

#### Scenario: A binary that needs a newer system

- **WHEN** the linux-x64 binary built on the oldest runner passes there but doesn't start on the newest
- **THEN** the run fails at the check on the newest runner

### Requirement: Changelog failures

If `CHANGELOG.md` doesn't have the changelog's form, then the CI workflow SHALL fail the run.

#### Scenario: A misnamed heading

- **WHEN** a pushed commit changes `## Unreleased` to `## Next`
- **THEN** the run fails with the release-notes tool's message naming `Next`

### Requirement: Release version on release tags

When the CI workflow runs for a release tag, the CI workflow SHALL build every file it checks at the tag's version.

#### Scenario: Checks for v0.2.0

- **WHEN** the release run for tag `v0.2.0` runs the CI workflow
- **THEN** the npm tarballs it checks have version `0.2.0` and the jar and native binaries it checks are named for `0.2.0`

#### Scenario: A pull request

- **WHEN** the CI workflow runs for a pull request
- **THEN** it builds the snapshot version, as before

## MODIFIED Requirements

### Requirement: GitHub-hosted runners

The CI workflow SHALL run on GitHub-hosted Linux and macOS runners.

#### Scenario: Runner for a run

- **WHEN** a CI run starts
- **THEN** its jobs run on GitHub-hosted Ubuntu and macOS runners, and none runs on a Windows or self-hosted runner

## RENAMED Requirements

- FROM: `### Requirement: Linux runners`
- TO: `### Requirement: GitHub-hosted runners`
