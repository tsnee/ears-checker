# Proposal

## Why

Both npm packages, the library `@tsnee/ears-checker` and the command line `@tsnee/ears-checker-cli`, are built and checked on every release, but they reach users only as tarballs attached to the GitHub Release: nobody can `npm install` or `npx` them. 0.2.0 is released, the owner has an npm account, and npm now offers staged publishing, which lets the release workflow put a version on npm without being able to make it public: only the owner, with a second factor, can.

## What Changes

- **0.2.0 goes on npm by hand.** npm sets up a trusted publisher only for a package that already exists, so the owner publishes 0.2.0 of both packages from their terminal, after checking each tarball against the release's `SHA256SUMS` and attestation. (A one-off; it has no provenance, since it isn't published from CI.)
- **Later releases are staged from the release workflow.** A new `npm` job, after `draft-release`, stages exactly the two tarballs the release's checks passed on with `npm stage publish`, authenticated by npm's trusted publishing (OIDC: no npm token is stored anywhere). The trusted publisher may stage only, never publish. Its token has `id-token: write` and reads the repository's contents, nothing more.
- **The owner approves on npm.** After publishing the GitHub Release and the Central Portal deployment, the owner runs `scripts/approve-npm-release <version>`, which downloads each staged tarball, checks it against the GitHub Release's `SHA256SUMS` and attestation, and approves it with `npm stage approve` (npm asks for the second factor). A version dropped on the Central Portal is rejected on npm instead.
- **npm is pinned for that job**, at a version with `npm stage` (11.15.0 or later), in its own lockfile.
- **Fallback.** If a staged version turns out to carry no provenance, the job publishes directly (`npm publish`) behind its own environment's approval instead: the original plan in `add-npm-package`'s Decision 9.
- **The next version is 0.3.0**: `0.3.0-SNAPSHOT` for Maven and `0.3.0-dev` for npm between releases. The specs stop naming the current snapshot version, so later bumps don't change them.
- **Documentation**: the README's quick start installs from npm (always by the scoped names) and shows the npm badge; both package READMEs drop "it isn't on npm yet"; CLAUDE.md's Releasing steps and `docs/release-files.md` describe staging and approval; CHANGELOG's Unreleased says the packages are on npm.

## Capabilities

### New Capabilities

- `npm-publishing`: putting a release's npm packages on npm: staging them from the release workflow with trusted publishing, and the owner's checked approval.

### Modified Capabilities

- `release-publishing`: the snapshot version is the next minor version after the latest release, not a named version; and the read-only token requirement allows the `npm` job an OIDC token.
- `npm-package`: the development version is the build's snapshot version with `-dev`, not a named version.
- `cli-npm-package`: the development build's scenario gives its version as a condition.

## Impact

- `.github/workflows/release.yml`: the `npm` job, in the `npm` environment (restricted to `v*` tags, with no reviewer: the owner's approval happens on npm).
- A new lockfile for the pinned npm, beside `.github/tools`.
- `scripts/approve-npm-release` (new), run by the owner.
- `build.mill`: the snapshot version.
- README.md, both package READMEs, CLAUDE.md, CONTRIBUTING.md if it names the version, `docs/release-files.md`, `docs/json-output.md`'s example, CHANGELOG.md, `docs/roadmap.md`.
- Outside the repository, done by the owner: 2FA on the npm account, the hand publish of 0.2.0, the `npm` GitHub environment, a stage-only trusted publisher on each package (`npm trust github`, within two days of the first staged release, since an unused one expires), and each package's publishing access set to require 2FA and disallow tokens.
