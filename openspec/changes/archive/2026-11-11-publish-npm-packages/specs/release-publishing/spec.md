## MODIFIED Requirements

### Requirement: Snapshot version outside releases

While no release version is set, the build SHALL use the next minor version after the latest release, with the suffix `-SNAPSHOT`.

#### Scenario: Local publish

- **WHEN** the latest release is 0.2.0 and `./mill __.publishLocal` runs on a developer machine
- **THEN** it publishes version `0.3.0-SNAPSHOT`

### Requirement: Malformed release version

If the release version is set to anything but `<major>.<minor>.<patch>`, then the build SHALL fail without producing artifacts.

#### Scenario: Pre-release suffix

- **WHEN** `EARS_RELEASE_VERSION=0.1.0-rc1 ./mill __.publishLocal` runs
- **THEN** the build fails with a message naming the bad value, and nothing is published to `~/.ivy2/local`

#### Scenario: Empty value

- **WHEN** `EARS_RELEASE_VERSION` is set but empty
- **THEN** the build uses the same snapshot version as when it is unset

### Requirement: Read-only token in releases

The release workflow SHALL limit its GitHub token to reading the repository's contents in every job except the one that creates the draft GitHub Release and the one that stages the npm packages.

#### Scenario: Token permissions

- **WHEN** a release run starts
- **THEN** every job's token but the draft-release job's and the npm job's can read the repository and can't push commits, create releases, or move tags

#### Scenario: The draft-release job

- **WHEN** the draft-release job runs
- **THEN** its token can create releases and attestations, and the job runs no code from the repository or its dependencies: it only downloads the run's files and calls GitHub

#### Scenario: The npm job

- **WHEN** the npm job runs
- **THEN** its token can read the repository and request an OIDC token for npm, and can't push commits, create releases, or move tags, and the job runs no code from the repository or its dependencies but the pinned npm
