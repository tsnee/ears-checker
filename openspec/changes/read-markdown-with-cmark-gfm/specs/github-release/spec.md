## MODIFIED Requirements

### Requirement: Release files

The draft GitHub Release SHALL carry the CLI jar, both npm tarballs, a native binary for each of linux-x64, linux-arm64, and macos-arm64, a `THIRD-PARTY-NOTICES.txt` file with cmark-gfm's license notices, and a `SHA256SUMS` file.

#### Scenario: Files of 0.2.0

- **WHEN** the release run for tag `v0.3.0` creates its draft
- **THEN** the draft carries `ears-check-0.3.0.jar`, `tsnee-ears-checker-0.3.0.tgz`, `tsnee-ears-checker-cli-0.3.0.tgz`, `ears-check-0.3.0-linux-x64`, `ears-check-0.3.0-linux-arm64`, `ears-check-0.3.0-macos-arm64`, `THIRD-PARTY-NOTICES.txt`, and `SHA256SUMS`

### Requirement: Notes from the changelog

The draft GitHub Release's notes SHALL be the changelog's section for its version, followed by the description of the release's files.

#### Scenario: Notes of 0.2.0

- **WHEN** `CHANGELOG.md` has a section `0.3.0 - 2026-12-04` and the release run for `v0.3.0` creates its draft
- **THEN** the draft's notes start with that section's text and then describe each file, what each native binary needs (the oldest glibc and macOS release), and how to check the files
