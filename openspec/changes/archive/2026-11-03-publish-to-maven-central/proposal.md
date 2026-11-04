# Proposal

## Why

The checker is public but unreleased: using it means cloning the
repository and building a jar, and the one downstream project runs a
`0.1.0-SNAPSHOT` from `~/.ivy2/local` on this machine. Publishing
0.1.0 to Maven Central lets anyone add the library as a dependency or
run the CLI with `cs launch`, and it's what the later packaging
changes (Coursier app, GraalVM binaries, npm) build on. Everything
the Central Portal needs is ready: the verified `io.github.tsnee`
namespace, a publishing token, and a signing key on public keyservers,
with all three in the repository's `release` environment.

## What Changes

- **A release workflow** (`.github/workflows/release.yml`). Pushing a
  tag `v<major>.<minor>.<patch>` starts it. It waits for the owner's
  approval (the `release` environment), checks that the tagged commit
  is on `main` and passes every check, then signs and uploads the
  three modules to the Central Portal **for review**: nothing is
  published until the owner clicks Publish in the Portal.
- **Version from the tag.** `publishVersion` reads the release version
  from an environment variable that only the release workflow sets;
  every other build stays `0.1.0-SNAPSHOT`.
- **A tag ruleset**, so only the owner can create, move, or delete
  `v*` tags.
- **Docs.** The README shows the dependency line and `cs launch`
  for the released version alongside the build-from-source steps;
  CLAUDE.md describes how to release.

Not in this change: GitHub Releases and downloadable binaries (the
GraalVM change), the Coursier app descriptor, npm, publishing
`-SNAPSHOT` versions to Central, and switching the downstream project
to the published version (it does that after 0.1.0 is out).

## Capabilities

### New Capabilities

- `release-publishing`: how a release is triggered, approved, checked,
  versioned, signed, and uploaded to Maven Central, and the limits on
  what the release workflow may do.

### Modified Capabilities

None. The CI workflow is unchanged and still publishes nothing.

## Impact

- New: `.github/workflows/release.yml`. Changed: `build.mill`
  (`publishVersion`), `README.md`, `CLAUDE.md`.
- GitHub settings: a tag ruleset for `v*` (the `release` environment
  and its secrets already exist).
- Permanent once published: the coordinates
  `io.github.tsnee:ears-checker_3`, `ears-checker_sjs1_3`, and
  `ears-checker-cli_3`, version `0.1.0`, and their POM metadata.
- No new build dependencies: Mill 1.3.1's
  `mill.javalib.SonatypeCentralPublishModule` does the signing and
  upload.
