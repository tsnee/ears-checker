# Proposal

## Why

The README grew one change at a time and now reads as a reference for whoever works on the
repository: build commands, check scripts, and coverage sit beside the command line, and a user
looking for "how do I run it" has to read 250 lines. 0.2.0 brings the first native binaries,
GitHub Releases, and npm packages, so new users will arrive by several routes; they need a short
way in, an honest statement of what the checker does and doesn't check, and a way to report a
security problem.

## What Changes

- **README.md, rewritten for users:** badges (CI, Maven Central, license); a quick start for
  each way to get it (a release binary, the jar, Coursier, npm, Maven and sbt); what
  it checks and its limits; the command line and output formats (linking `docs/json-output.md`);
  the Scala library and the npm package; releases and verifying downloads; links to
  CONTRIBUTING.md and SECURITY.md.
- **CONTRIBUTING.md (new):** what the README's "Building" section holds now, plus how changes are
  made here: OpenSpec changes with strict EARS requirements, the checks to run before a change is
  done, and the changelog.
- **SECURITY.md (new):** the supported versions, how to report a vulnerability privately, and
  what the project treats as security-relevant (the CLI reads untrusted files; the native binary
  loads cmark; release files are attested).
- Every command and output in the README is checked against a real run before it's committed.

No behavior changes: no spec changes (`skip_specs`).

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- None.

## Impact

- `README.md`, new `CONTRIBUTING.md` and `SECURITY.md`; links to them from CLAUDE.md's layout.
- The npm packages' own READMEs (`ears/npm/package/README.md`, `ears/cli/js/package/README.md`)
  are checked for claims the rewrite contradicts.
- `CHANGELOG.md` (Unreleased), the roadmap, the work log.
- A repository setting for the owner: private vulnerability reporting, which SECURITY.md points
  to (see design).
