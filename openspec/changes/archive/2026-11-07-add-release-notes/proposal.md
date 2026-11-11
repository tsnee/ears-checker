# Proposal

## Why

A release today is only a Maven Central deployment: nothing says what changed, and the jar, the npm tarballs, and the native binary exist only for someone who builds them. 0.2.0 is the first release with a native binary and npm packages, so it needs a place users can read what changed and download each file, with a way to check that the file is the one the release built.

## What Changes

- A `CHANGELOG.md` with an `Unreleased` section and a section per version, written before tagging; sections for 0.1.0 and 0.1.1 are written from the history.
- A developer-only Scala module, `ears.releaseNotes`, that reads the changelog with commonmark-java: it checks the changelog's form (in CI), and it writes a version's release notes from its section and a fixed description of the release's files.
- The release workflow, after the Central Portal upload, creates a **draft** GitHub Release for the tag with the notes and these files: the CLI jar, both npm tarballs (the ones the owner will publish), a native binary for linux-x64, linux-arm64, and macos-arm64, a `SHA256SUMS` file, and a build-provenance attestation for each file. The owner publishes the draft when publishing the Central Portal deployment. A tag with no changelog section fails before anything is uploaded.
- Every attached file is the one a check ran on: the check scripts can keep what they checked (`--out`), and `check-native-cli` can check a binary built elsewhere (`--binary`, `--jar`).
- Native binaries are checked on every platform, in CI as well as in releases: each is built on its platform's oldest runner (`ubuntu-22.04`, `ubuntu-22.04-arm`, `macos-15`), checked there, and checked again on the newest (`ubuntu-26.04`, `ubuntu-26.04-arm`, `macos-26`). The macOS binary declares macOS 15 as its minimum, not the build machine's release.
- CI builds the release's version when it runs for a release tag, so the files it checks are the ones attached.
- GitHub Releases for v0.1.0 and v0.1.1, made by hand from their changelog sections (notes only: no files were built for them).

## Capabilities

### New Capabilities

- `github-release`: the changelog, the release notes written from it, and the draft GitHub Release with the release's files, checksums, and attestations.

### Modified Capabilities

- `release-publishing`: the release fails before upload without a changelog section, and one job may write to the repository's releases.
- `continuous-integration`: native binaries checked on macOS and Arm runners as well as Linux x64 (the "Linux runners" requirement becomes "GitHub-hosted runners"), the changelog's form checked, and the release's version built on release tags.
- `cli-native-binary`: the oldest glibc and macOS release the binaries run on.

## Impact

- `.github/workflows/`: `ci.yml` (the version on tags, the files kept, the changelog check), a new reusable `native.yml` (the per-platform builds and checks), and `release.yml` (the notes in `tag`, the `draft-release` job).
- `build.mill`: the `ears.releaseNotes` module (with coverage minimums) and the macOS deployment target for the native binary.
- `scripts/check-npm-package`, `check-npm-cli`, `check-native-cli`: new switches.
- New actions, pinned: `actions/upload-artifact`, `actions/download-artifact`, `actions/attest-build-provenance`.
- Docs: `CHANGELOG.md`, the README (downloads and verifying them), CLAUDE.md's release steps, the roadmap.
