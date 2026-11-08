## Purpose

The changelog, the release notes written from it, and the draft GitHub Release a release run
creates for its tag: the notes, the release's files as they were checked, a checksum file, and a
build-provenance attestation for each file.

## ADDED Requirements

### Requirement: Draft release after upload
When the release workflow has uploaded a release to the Central Portal, the release workflow SHALL create a draft GitHub Release for the tag.

#### Scenario: Tag v0.2.0
- **WHEN** the owner approves the release run for tag `v0.2.0` and its upload to the Central Portal succeeds
- **THEN** a draft GitHub Release titled `0.2.0` exists for tag `v0.2.0`, and it isn't shown to anyone without write access until the owner publishes it

#### Scenario: Failed upload
- **WHEN** a release run's upload to the Central Portal fails
- **THEN** no GitHub Release is created for its tag

### Requirement: Release files
The draft GitHub Release SHALL carry the CLI jar, both npm tarballs, a native binary for each of linux-x64, linux-arm64, and macos-arm64, and a `SHA256SUMS` file.

#### Scenario: Files of 0.2.0
- **WHEN** the release run for tag `v0.2.0` creates its draft
- **THEN** the draft carries `ears-check-0.2.0.jar`, `tsnee-ears-checker-0.2.0.tgz`, `tsnee-ears-checker-cli-0.2.0.tgz`, `ears-check-0.2.0-linux-x64`, `ears-check-0.2.0-linux-arm64`, `ears-check-0.2.0-macos-arm64`, and `SHA256SUMS`

### Requirement: Checked release files
The release workflow SHALL attach each release file byte for byte as its check ran on it.

#### Scenario: The Linux x64 binary
- **WHEN** the draft for `v0.2.0` is created
- **THEN** the SHA-256 of its `ears-check-0.2.0-linux-x64` equals that of the binary the native CLI check passed on in the same run, which the run's log shows

#### Scenario: An npm tarball
- **WHEN** the owner downloads `tsnee-ears-checker-cli-0.2.0.tgz` from the draft
- **THEN** it's the tarball the Node CLI check installed and ran in the same run, at version `0.2.0`

### Requirement: Checksum file
The `SHA256SUMS` file SHALL list the SHA-256 of every other file the draft GitHub Release carries.

#### Scenario: Checking downloads
- **WHEN** a user downloads every file of a release into one directory and runs `sha256sum -c SHA256SUMS` there
- **THEN** it reports `OK` for each file

### Requirement: Build provenance
The release workflow SHALL attest the build provenance of every file the draft GitHub Release carries.

#### Scenario: Verifying a binary
- **WHEN** a user runs `gh attestation verify ears-check-0.2.0-macos-arm64 --repo tsnee/ears-checker --signer-workflow tsnee/ears-checker/.github/workflows/release.yml --source-ref refs/tags/v0.2.0`
- **THEN** it succeeds, and it fails for a file the release workflow didn't build for tag `v0.2.0`

### Requirement: Notes from the changelog
The draft GitHub Release's notes SHALL be the changelog's section for its version, followed by the description of the release's files.

#### Scenario: Notes of 0.2.0
- **WHEN** `CHANGELOG.md` has a section `0.2.0 - 2026-11-20` and the release run for `v0.2.0` creates its draft
- **THEN** the draft's notes start with that section's text and then describe each file, what each native binary needs (cmark 0.30 or later, the oldest glibc and macOS release), and how to check the files

### Requirement: Existing release for the tag
If a GitHub Release, draft or published, already exists for the tag, then the release workflow SHALL fail without changing it.

#### Scenario: Rerunning a release
- **WHEN** the owner reruns the draft-release job of the release run for `v0.2.0` after its draft was created
- **THEN** the draft-release job fails with a message naming the existing release, and the existing release keeps its notes and files

### Requirement: Changelog form
If the changelog's second-level headings aren't `Unreleased` followed by headings of the form `<major>.<minor>.<patch> - <YYYY-MM-DD>`, newest version first, then the release-notes tool SHALL fail with a message naming the first heading that doesn't fit.

#### Scenario: Versions out of order
- **WHEN** `CHANGELOG.md` has `## 0.1.0 - 2026-11-03` before `## 0.1.1 - 2026-11-05`
- **THEN** the tool fails, naming `0.1.1 - 2026-11-05`

#### Scenario: A heading without a date
- **WHEN** `CHANGELOG.md` has a heading `## 0.2.0`
- **THEN** the tool fails, naming `0.2.0`

### Requirement: Missing version section
If the changelog has no section for the requested version, or that section has no text, then the release-notes tool SHALL fail with a message naming the version.

#### Scenario: Only Unreleased
- **WHEN** the tool is asked for version `0.2.0` and `CHANGELOG.md` has only `Unreleased`, `0.1.1`, and `0.1.0` sections
- **THEN** it fails with a message naming `0.2.0` and saying to rename `Unreleased`
