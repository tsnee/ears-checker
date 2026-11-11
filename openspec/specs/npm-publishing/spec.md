# npm-publishing Specification

## Purpose

Puts each release's npm packages, `@tsnee/ears-checker` and `@tsnee/ears-checker-cli`, on npm: the release workflow stages them with npm's trusted publishing, and only the owner's checked approval makes them public.

## Requirements

### Requirement: Staged on a release

When a release run has created its draft GitHub Release, the release workflow SHALL stage the release's two npm tarballs on npm at the tag's version.

#### Scenario: Tag v0.3.0

- **WHEN** a release run for tag `v0.3.0` creates its draft GitHub Release
- **THEN** `@tsnee/ears-checker` 0.3.0 and `@tsnee/ears-checker-cli` 0.3.0 are staged on npm

### Requirement: Staged, never published

The release workflow SHALL put npm packages on npm only as staged versions, which no one can install until they are approved.

#### Scenario: Before approval

- **WHEN** a release run for tag `v0.3.0` has staged both packages and the owner hasn't approved them
- **THEN** `npm view @tsnee/ears-checker@0.3.0` and `npm view @tsnee/ears-checker-cli@0.3.0` find no such version

### Requirement: The checked tarballs

The release workflow SHALL stage, byte for byte, the npm tarballs that the release's checks passed on and that the draft GitHub Release attaches.

#### Scenario: Same checksum

- **WHEN** the owner downloads the staged `@tsnee/ears-checker-cli` 0.3.0 tarball from npm
- **THEN** its SHA-256 is the one `SHA256SUMS` on the GitHub Release gives for `tsnee-ears-checker-cli-0.3.0.tgz`

### Requirement: No stored npm credential

The release workflow SHALL authenticate to npm with trusted publishing.

#### Scenario: Repository secrets

- **WHEN** the repository's and its environments' secrets are listed
- **THEN** none of them is an npm token

### Requirement: Nothing staged after a failed upload

If the Central Portal upload or the draft GitHub Release fails, then the release workflow SHALL stage nothing on npm.

#### Scenario: Portal upload fails

- **WHEN** a release run for tag `v0.3.0` fails to upload to the Central Portal
- **THEN** neither package has a 0.3.0 staged on npm

### Requirement: Package name and version before staging

If an npm tarball's `package.json` doesn't name the expected package at the tag's version, then the release workflow SHALL fail before it stages either package.

#### Scenario: Wrong version

- **WHEN** a release run for tag `v0.3.0` finds `tsnee-ears-checker-0.3.0.tgz` declaring version `0.3.0-dev`
- **THEN** the run fails, and neither package has a 0.3.0 staged on npm

### Requirement: Provenance

The release workflow SHALL stage each npm package with a provenance statement naming this repository's release workflow and the tag.

#### Scenario: Provenance on npm

- **WHEN** `@tsnee/ears-checker` 0.3.0 has been approved
- **THEN** npm shows its provenance as built by `.github/workflows/release.yml` in `tsnee/ears-checker` at tag `v0.3.0`

### Requirement: Approving a checked release

When the owner runs the approval script for a version, the approval script SHALL approve each staged package of that version whose tarball matches the GitHub Release's `SHA256SUMS` and attestation.

#### Scenario: Approving 0.3.0

- **WHEN** the owner runs `scripts/approve-npm-release --version 0.3.0` and both staged tarballs match the release
- **THEN** npm asks for the owner's second factor, and both packages are published at 0.3.0

### Requirement: Mismatched tarball left staged

If a staged tarball's SHA-256 differs from the GitHub Release's `SHA256SUMS` entry for it, or its attestation doesn't verify, then the approval script SHALL leave that package unapproved.

#### Scenario: Different bytes

- **WHEN** the staged `@tsnee/ears-checker` 0.3.0 tarball's SHA-256 isn't the one `SHA256SUMS` gives for `tsnee-ears-checker-0.3.0.tgz`
- **THEN** the script reports the mismatch, approves nothing for that package, and exits with a non-zero status

### Requirement: Approval only of a published release

If the GitHub Release for the version is still a draft, then the approval script SHALL approve nothing.

#### Scenario: Draft release

- **WHEN** the owner runs `scripts/approve-npm-release --version 0.3.0` before publishing the draft GitHub Release for `v0.3.0`
- **THEN** the script says the release is still a draft, approves nothing, and exits with a non-zero status

### Requirement: Approval only once on Maven Central

If the version isn't on Maven Central, then the approval script SHALL approve nothing.

#### Scenario: Deployment not yet published

- **WHEN** the owner runs `scripts/approve-npm-release --version 0.3.0` after publishing the GitHub Release for `v0.3.0` but before 0.3.0 reaches Maven Central
- **THEN** the script says 0.3.0 isn't on Maven Central, approves nothing, and exits with a non-zero status
