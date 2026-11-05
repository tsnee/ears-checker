## MODIFIED Requirements

### Requirement: Snapshot version outside releases
While no release version is set, the build SHALL use version `0.2.0-SNAPSHOT`.

#### Scenario: Local publish
- **WHEN** `./mill __.publishLocal` runs on a developer machine
- **THEN** it publishes version `0.2.0-SNAPSHOT`

### Requirement: Malformed release version
If the release version is set to anything but `<major>.<minor>.<patch>`, then the build SHALL fail without producing artifacts.

#### Scenario: Pre-release suffix
- **WHEN** `EARS_RELEASE_VERSION=0.1.0-rc1 ./mill __.publishLocal` runs
- **THEN** the build fails with a message naming the bad value, and nothing is published to `~/.ivy2/local`

#### Scenario: Empty value
- **WHEN** `EARS_RELEASE_VERSION` is set but empty
- **THEN** the build uses `0.2.0-SNAPSHOT`, as when it is unset
