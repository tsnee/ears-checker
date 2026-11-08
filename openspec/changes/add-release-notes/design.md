# Design

## Context

Observations (2026-11-07):

- **Releases today:** `release.yml` has three jobs: `tag` (the tag is a version on `main`),
  `checks` (calls `ci.yml`), and `publish` (the `release` environment, the owner's approval,
  `__.publishArtifacts`, then the Central Portal upload with `--shouldRelease false`). Every job's
  token is `contents: read`, as the `release-publishing` requirement "Read-only token in
  releases" says, so no job can create a GitHub Release.
- **Tags:** `v0.1.0` (`18b8d15`, 2026-11-03) and `v0.1.1` (`52e9c40`, 2026-11-05), with no
  GitHub Releases. 23 commits lead to `v0.1.0` and 6 more to `v0.1.1`; `v0.1.1` fixed Scala.js
  linking without scala-java-locales.
- **Versions:** `ears.publishVersion` reads `EARS_RELEASE_VERSION` (else `0.2.0-SNAPSHOT`), and
  `npmVersion` follows it (`-dev` outside releases). Today only `publish` sets it, so the jar,
  tarballs, and binary `ci.yml` checks in a release run are snapshots.
- **Packing:** `check-npm-package` and `check-npm-cli` pack into a temporary directory and
  delete it; `check-native-cli` always builds its own binary and jar.
- **macOS minimum:** the binary built on this Mac (macOS 27.2) declares `minos 27.2`
  (`otool -l`), so it won't start on any older macOS. A clang probe gave `minos 13.0` with
  `-mmacosx-version-min=13.0` and with `MACOSX_DEPLOYMENT_TARGET=13.0`. Mill's Scala Native
  module has `nativeCompileOptions` and `nativeLinkingOptions`.
- **Runners** (actions/runner-images README): x64 `ubuntu-22.04`, `ubuntu-24.04`
  (`ubuntu-latest`), `ubuntu-26.04`; arm64 `ubuntu-22.04-arm`, `ubuntu-24.04-arm`,
  `ubuntu-26.04-arm`; arm64 `macos-15` and `macos-26` (`macos-latest`); `macos-14` is
  deprecated; Intel macOS only as `-intel` or `-large` images. CI's last run on `ubuntu-latest`
  reported image `ubuntu-26.04`. Ubuntu 22.04 has glibc 2.35 and `libcmark0.30.2`.
- **Actions to pin:** `actions/upload-artifact` v7.0.2 (`cf430e030ddbb5b0abf93d22962f4752f3646cd9`),
  `actions/download-artifact` v8.0.2 (`9000827ccba6bdab643e8b6fd33ac0654aef8333`),
  `actions/attest-build-provenance` v4.2.2 (`4d101475d8b20a2381f78447822ac1eab6504dd8`).
- **Repository rules:** one ruleset, "Release tags", on tags (only admins create `v*` tags).
- **commonmark-java:** the facade uses 0.31.0; 0.32.0 is the latest on Maven Central.

## Goals / Non-Goals

**Goals:**
- A changelog written before each release, and a draft GitHub Release made from it with every
  file a user might download, each one the file a check passed on, with checksums and
  provenance.
- Native binaries checked on each platform they're released for, on the oldest and newest
  runner, in CI as well as in releases.
- Release notes for v0.1.0 and v0.1.1.

**Non-Goals:**
- Windows and Intel macOS binaries; signing or notarizing the macOS binary; Homebrew or other
  packages.
- Publishing to npm (the roadmap's npm item): the tarballs are attached for the owner to publish.
- Changing what goes to Maven Central.

## Decisions

### 1. The changelog

- `CHANGELOG.md` at the root: `# Changelog`, then `## Unreleased`, then one
  `## <major>.<minor>.<patch> - <YYYY-MM-DD>` per release, newest first, each with
  `### Added`, `### Changed`, `### Fixed` as needed (Keep a Changelog's sections, without its
  link references).
- Each change adds its lines under `Unreleased` in its implementation commit. Before tagging,
  the release's pull request renames `Unreleased` to the version and date and opens a new empty
  `Unreleased`. CLAUDE.md's Releasing steps say so.
- Written now: `0.1.0` and `0.1.1` from the history, and `Unreleased` with this batch's changes
  (the classifier on npm, logging, the CLI on Node and natively, inline code, the npm packages'
  size, cmark at run time).

### 2. The release-notes tool: `ears.releaseNotes`

- A developer-only JVM module like `ears.linkCheck`: package
  `io.github.tsnee.ears.releasenotes`, never published, no product module depends on it, held to
  tests and coverage minimums (measured with the others, and in CI's coverage summary).
- Pure core (`Changelog`): parses the changelog with commonmark-java (the facade's version,
  shared in `build.mill`) with block source spans, finds the second-level headings, and checks
  their form and order; a version's section is the source text between its heading and the
  next, kept verbatim. `notes(changelog, version, files)` is that section followed by the files
  description with `{version}` replaced. Failures are values naming the heading or version.
- Shell (`Main`, ZIO): `check <changelog>` and `notes <version> <changelog> [<files>]`, reading
  files and writing the notes to standard output; logging as the CLI does.
- A parser, not line matching, since a `## ` line inside a code block isn't a heading.
- CLAUDE.md's rule that Markdown is read only through the facade is about requirement text:
  the facade gives sentences, and this tool needs headings and source. The rule's wording
  changes to say it covers the checker, and this module may use commonmark-java directly.

### 3. The files description

- `docs/release-files.md`, with `{version}`: a table of the files and what each needs (the jar:
  JDK 17 or later; the tarballs: Node 22 or later, and what `npm install` of each gives; the
  binaries: cmark 0.30 or later, glibc 2.35 or later, macOS 15 or later); how to check them
  (`sha256sum -c SHA256SUMS`, `gh attestation verify <file> --repo tsnee/ears-checker`); and, for
  macOS, that a binary downloaded by a browser is quarantined and how to clear it (decision 7).

### 4. Every attached file is a checked file

- `check-npm-package --out <dir>` and `check-npm-cli --out <dir>` keep the tarball they
  installed (and the CLI check the jar it ran) in `<dir>`. `check-native-cli --out <dir>` keeps
  the binary it checked and the jar it ran there; `--binary <file> --jar <file>` checks a binary and jar built elsewhere,
  skipping the build. Defaults are as today. The scripts stay POSIX `sh`.
- When `ci.yml` runs for a release tag (only ever called from `release.yml`, since tags don't
  trigger it), each job first sets `EARS_RELEASE_VERSION` from the tag, so every file is built
  at the release's version; then it uploads what the checks kept as a workflow artifact
  (`release-jvm`: the jar as `ears-check-<version>.jar` and both tarballs). Pull requests and
  `main` upload nothing.
- The workflow logs each kept file's SHA-256, so the run shows the attached file was checked.
- The jobs that build release files install their npm tools (the OpenSpec CLI, `tsc`,
  publint, attw) from `.github/tools/package-lock.json` with `npm ci --ignore-scripts`, not
  `npm install -g`: an unpinned dependency's install script could otherwise change a file the
  release then attests (security review, 2026-11-07). None of the 142 locked packages has an
  install script.

### 5. Native binaries on every platform: `native.yml`

- A reusable workflow, called by `ci.yml` (so release runs get it through `checks`):

  | Platform | Built and checked on | Checked again on | Install cmark |
  |---|---|---|---|
  | linux-x64 | `ubuntu-22.04` | `ubuntu-26.04` | `apt-get install libcmark-dev` |
  | linux-arm64 | `ubuntu-22.04-arm` | `ubuntu-26.04-arm` | `apt-get install libcmark-dev` |
  | macos-arm64 | `macos-15` | `macos-26` | `brew install cmark` |

- The build job runs `check-native-cli --out`, and uploads the binary and its jar as an artifact;
  the second job downloads them and runs `check-native-cli --binary --jar`. On a release tag the
  binary is renamed `ears-check-<version>-<platform>` and kept for the release.
- Building on the oldest image means the Linux binaries need only glibc 2.35 (the build fails if
  the newest `GLIBC_` symbol version `objdump -T` finds is newer than 2.35); checking on the newest shows
  newer systems run them. This replaces the roadmap's older-distribution container: the oldest
  runner already is one.
- `check`'s "Check the native CLI" step moves to `native.yml`; the step with less cmark moves to
  the linux-x64 job on `ubuntu-26.04`. `check` keeps the cmark install, for the native tests.
- The macOS binary is linked with `-mmacosx-version-min=15.0` (`nativeCompileOptions` and
  `nativeLinkingOptions`, on macOS only): `macos-15` is the oldest supported runner, so 15.0 is
  the oldest release that's checked. The build fails unless `otool -l` shows `minos 15.0`.
- The Linux build jobs also run the Markdown conformance suite natively, against Ubuntu 22.04's
  cmark 0.30.2, the oldest the binaries support; `check` runs it against a newer cmark.

### 6. The release workflow

- `tag`: also runs `ears.releaseNotes` `notes` for the version and uploads the notes as an
  artifact, so a missing section fails before `checks` and `publish`.
- `publish`: unchanged; it needs `tag` and `checks`, so every platform's checks pass before
  anything is uploaded.
- `draft-release` (new): needs `publish`; `permissions: contents: write, id-token: write,
  attestations: write`; no environment (it reads no secrets) and no checkout (it runs no code
  from the repository). Steps: fail if the repository's releases (drafts included) have one for
  the tag, first, so a rerun attests nothing; download the run's release artifacts and notes;
  check they're exactly the release's files and write `SHA256SUMS`; attest every file with
  `attest-build-provenance`; then `gh release create <tag> --draft --verify-tag --title
  <version> --notes-file notes.md` with the files.
- `gh` (preinstalled, GitHub's own) creates the release, rather than a third-party action, so
  no code outside GitHub's runs with a write token.
- A rerun after the draft exists fails without touching it; the owner deletes the draft and
  reruns the job.

### 7. The macOS binary is unsigned

- A binary downloaded by a browser gets macOS's quarantine attribute, and Gatekeeper refuses to
  open an unsigned one ("Apple could not verify…"). `curl` doesn't set the attribute. The files
  description says to download with `curl` or to clear it with
  `xattr -d com.apple.quarantine ears-check-<version>-macos-arm64`. Signing needs a paid Apple
  developer account, so it's out of scope; this is called out to the owner before release.

### 8. Backfilling v0.1.0 and v0.1.1

- A one-off script (scratchpad) the owner runs: notes from each section via the tool (no files
  description: no files were built), then `gh release create v0.1.0 --verify-tag --title 0.1.0
  --latest=false` and `gh release create v0.1.1 --verify-tag --title 0.1.1 --latest`, published
  directly, since they describe releases already on Maven Central. Their notes point there.

### 9. Docs

- README: a downloads section (the release page, checksums, attestations, the macOS note) beside
  each way to run the CLI.
- CLAUDE.md: the changelog in the workflow, the Releasing steps (rename `Unreleased`; publish
  the draft with the Central deployment), `ears.releaseNotes` in the build, coverage, and
  layout, `native.yml`, and the facade rule's wording.
- Roadmap: the item comes off.

## Risks / Trade-offs

- **[Untried runners]** The native build hasn't run on Arm Linux or on GitHub's macOS images, or
  with Mill fetching its JDK there. Apply runs it on a pull request first; a platform that
  can't build is reported to the owner rather than dropped.
- **[CI time]** Six more jobs per run, in parallel with `check`; free on this public
  repository. Apply records their times. If the macOS queue makes runs slow, the owner can
  choose to run `native.yml` only on release tags.
- **[ubuntu-22.04's retirement]** When GitHub retires it, the Linux builds move into a
  `debian:bookworm` (glibc 2.36) or similar container.
- **[A partial release]** `publish` succeeds and `draft-release` fails: the Central deployment
  waits for review either way; the owner reruns the failed job.
- **[Attestations on drafts]** Attestations are stored by GitHub, apart from the release, and
  verified by a file's digest, so they hold for the published release.

## Open Questions

- None; the owner confirms the macOS binary's distribution (decision 7) and CI's extra jobs
  (decision 5) when reviewing this plan.
