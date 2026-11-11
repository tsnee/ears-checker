# release-publishing Specification

## Purpose

Publishes released versions of the library and the CLI to Maven Central from a version tag, with the owner's approval and a manual review in the Central Portal before anything becomes public.

## Requirements

### Requirement: Release on a version tag

When a tag of the form `v<major>.<minor>.<patch>` is pushed, the release workflow SHALL upload that version of every published module to the Central Portal.

#### Scenario: Tag v0.1.0

- **WHEN** the owner pushes tag `v0.1.0` on a commit of `main`
- **THEN** a release run starts and, once approved, uploads `ears-checker_3`, `ears-checker_sjs1_3`, and `ears-checker-cli_3` at version `0.1.0`

### Requirement: Version tags only

The release workflow SHALL start only for tags of the form `v<major>.<minor>.<patch>`.

#### Scenario: Other tag

- **WHEN** a tag `v0.1.0-rc1` or `release-1` is pushed
- **THEN** no release run starts

#### Scenario: Branch push

- **WHEN** a commit is pushed to `main`
- **THEN** no release run starts

### Requirement: Owner approval

When a release run starts, the release workflow SHALL wait for the owner's approval before any step can read the publishing secrets.

#### Scenario: Waiting run

- **WHEN** tag `v0.1.0` is pushed and the owner hasn't approved the run
- **THEN** the run waits, and no step has run with the Sonatype token or the signing key

### Requirement: Tagged commit on main

If the tagged commit isn't on the main branch, then the release workflow SHALL fail before it uploads anything.

#### Scenario: Tag on a feature branch

- **WHEN** tag `v0.1.0` is pushed on a commit that is only on branch `add-sarif-output`
- **THEN** the run fails and nothing is uploaded

### Requirement: Checks before upload

If the format check, compilation, a test, or the OpenSpec check fails on the tagged commit, then the release workflow SHALL fail before it uploads anything.

#### Scenario: Failing test

- **WHEN** a test fails on the tagged commit
- **THEN** the run fails and nothing is uploaded

#### Scenario: Failing on JDK 17

- **WHEN** the OpenSpec check passes on JDK 27 but fails on JDK 17 for the tagged commit
- **THEN** the run fails and nothing is uploaded

### Requirement: Version from the tag

The release workflow SHALL publish every module at the version the tag names, without the leading `v`.

#### Scenario: Version in the POM

- **WHEN** a release run for tag `v0.1.0` uploads its files
- **THEN** every uploaded POM has version `0.1.0`

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

### Requirement: Review before publishing

When the release workflow uploads a release, the release workflow SHALL leave the deployment in the Central Portal for the owner to publish or drop.

#### Scenario: Upload finished

- **WHEN** a release run's upload passes the Portal's validation
- **THEN** the deployment waits in the Portal, and the version isn't on Maven Central until the owner clicks Publish

#### Scenario: Dropped deployment

- **WHEN** the owner drops the deployment in the Portal
- **THEN** nothing is published, and the same version can be uploaded again by rerunning the release

### Requirement: Complete artifacts

The release workflow SHALL upload with each module its jar, POM, sources jar, documentation jar, and a signature and checksums for each of those files.

#### Scenario: Portal validation

- **WHEN** a release run uploads version `0.1.0`
- **THEN** the Central Portal's validation of the deployment reports no missing file, signature, or POM field

### Requirement: Scala.js source references

The release workflow SHALL publish Scala.js IR whose source file references point to the tagged sources on GitHub.

#### Scenario: Source reference in a released IR file

- **WHEN** release `0.1.0` is published
- **THEN** every source file reference in `ears-checker_sjs1_3`'s `.sjsir` files starts with `https://raw.githubusercontent.com/tsnee/ears-checker/v0.1.0/`, and none is a `file:` path

### Requirement: Relative source references outside releases

While no release version is set, the build SHALL give Scala.js IR source file references relative to the repository root.

#### Scenario: Local build

- **WHEN** `./mill ears.js.jar` runs on a developer machine
- **THEN** the `.sjsir` files reference sources such as `ears/src/io/github/tsnee/ears/Classifier.scala`, with no absolute path

### Requirement: Signed with the release key

The release workflow SHALL sign every uploaded file with the release key whose public key is on public keyservers.

#### Scenario: Signature check

- **WHEN** a published `.asc` signature is checked with the public key fetched from `keyserver.ubuntu.com`
- **THEN** it verifies as a good signature by fingerprint `B7D3EA3ABB9B7F5B09AEF10FFDAF9649810B085A`

### Requirement: Pinned actions in releases

The release workflow SHALL reference each action by a full commit SHA.

#### Scenario: Checkout action

- **WHEN** the release workflow uses the checkout action
- **THEN** it refers to it by a 40-character commit SHA

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
