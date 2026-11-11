## ADDED Requirements

### Requirement: Release notes before upload
If `CHANGELOG.md` on the tagged commit has no section for the tagged version, then the release workflow SHALL fail before it uploads anything.

#### Scenario: Unreleased not renamed
- **WHEN** the owner pushes tag `v0.2.0` on a commit whose `CHANGELOG.md` has no `0.2.0` section
- **THEN** the run fails with a message naming `0.2.0`, and nothing is uploaded to the Central Portal or GitHub Releases

### Requirement: Release files before upload
If any release file's check fails on any of its platforms, then the release workflow SHALL fail before it uploads anything.

#### Scenario: A macOS-only failure
- **WHEN** the macOS binary of the tagged commit writes different output from the jar on the newest macOS runner
- **THEN** the run fails, and nothing is uploaded to the Central Portal or GitHub Releases

## MODIFIED Requirements

### Requirement: Read-only token in releases
The release workflow SHALL limit its GitHub token to reading the repository's contents in every job except the one that creates the draft GitHub Release.

#### Scenario: Token permissions
- **WHEN** a release run starts
- **THEN** every job's token but the draft-release job's can read the repository and can't push commits, create releases, or move tags

#### Scenario: The draft-release job
- **WHEN** the draft-release job runs
- **THEN** its token can create releases and attestations, and the job runs no code from the repository or its dependencies: it only downloads the run's files and calls GitHub
