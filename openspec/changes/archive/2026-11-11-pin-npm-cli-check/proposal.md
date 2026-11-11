# Proposal

## Why

The whole-repository security review before 0.2.0 (2026-11-11) found that `scripts/check-npm-cli`,
which CI's `check` job runs before it uploads the jar and both npm tarballs a release attaches,
installs the packed CLI with a plain `npm install`. That resolves commonmark's dependencies
(`entities ~3.0.1`, `mdurl ~1.0.1`, `minimist ~1.2.8`) fresh from the registry and runs their
install scripts; afterwards the check compares the installed packages with the Markdown facade's
lockfile by name only. A compromised release of one of them could rewrite the jar or a tarball
before the script copies them to `kept/`, and the release would then attest and checksum the
tampered files. Every other npm install here uses `npm ci --ignore-scripts` from a lockfile.

## What Changes

- `scripts/check-npm-cli` installs the packed CLI with `npm ci --ignore-scripts`, from a lockfile
  it builds: the tarball's own entry (its checksum, version, and `bin`) plus the entries of
  `ears/markdown/js/package-lock.json`, so each dependency comes at the version and checksum the
  conformance suite runs on, and a mismatch stops the install (`EINTEGRITY`).
- With `--out`, it keeps the tarball and the jar before any dependency's code runs, logs the
  checksum of every file in the output directory (including the library's tarball, which CI's
  `check-npm-package` keeps there first), and fails if any changes before it ends.
- `scripts/time-cli` installs the CLI the same way (a shared, sourced helper).
- `scripts/check-npm-package` installs the library's tarball with `--ignore-scripts` too, and with
  `--out` keeps it right after packing and checks it unchanged at the end.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `continuous-integration`: the Node CLI check installs pinned dependencies with no install
  scripts, and keeps the release files before running them.

## Impact

- `scripts/check-npm-cli`, `scripts/time-cli`, `scripts/check-npm-package`, a new sourced helper
  `scripts/npm-cli-project`.
- CLAUDE.md, CONTRIBUTING.md (how the check installs), SECURITY.md if it describes the build,
  the roadmap, the work log.
- No change to either npm package, the code, or the workflows' steps.
