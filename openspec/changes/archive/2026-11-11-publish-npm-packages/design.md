# Design

## Context

- Every release already builds, checks, and attaches both npm tarballs: `check` keeps them with the jar in the `release-jvm` artifact (`scripts/check-npm-cli --out kept`), and `draft-release` attaches them to the draft GitHub Release with `SHA256SUMS` and an attestation each. Nothing puts them on npm.
- The release workflow's jobs: `tag`, `checks` (all of `ci.yml`), `publish` (environment `release`, the owner's approval, the Sonatype secrets), and `draft-release` (the only job with a write token; it checks out nothing).
- `add-npm-package`'s Decision 9 planned a separate `npm` job behind its own environment approval, running `npm publish` with trusted publishing. Since then npm added staged publishing.

Observations, 2026-11-11:

- npm's trusted publishing needs npm 11.5.1 or later and Node 22.14.0 or later, `id-token: write`, and GitHub-hosted runners; provenance is automatic for a public repository and package; `repository.url` must match the repository (both packages name `git+https://github.com/tsnee/ears-checker.git`). A configuration names the user, repository, workflow file, and optionally an environment; npm doesn't check it when it's saved, and one that hasn't been used to publish within two days expires (docs.npmjs.com/trusted-publishers, last updated 2026-10-06).
- `npm trust` (the CLI for the same configuration) needs npm 11.15.0, 2FA on the account, and a package that already exists on the registry; `--allow-stage-publish` and `--allow-publish` choose what the trust may run (docs.npmjs.com/cli/v11/commands/npm-trust).
- Staged publishing needs npm 11.15.0 and Node 22.14.0. `npm stage publish` never asks for a second factor; `npm stage approve` and `npm stage reject` always do, as does approving on npmjs.com. A staged version takes its version number, so the same version can't then be published another way. Tokens from a trust relationship can run `npm stage publish` and `npm publish` but no other `npm stage` subcommand. `npm stage download <stage-id>` fetches a staged tarball for inspection. The `--provenance` flag's description says automatic provenance via trusted publishing applies, but no page says outright that a staged version keeps it, nor how long a staged version lasts (docs.npmjs.com/staged-publishing, edited 2026-11-10; `npm help stage` in npm 11.21.1).
- `npm stage publish ./<file>.tgz --dry-run` with npm 11.21.1 took a packed tarball as it is: the same shasum as the file, "staged with tag latest and default access".
- `--access` defaults to public for a new package and leaves an existing one's alone; the hand publish of 0.2.0 still passes `--access public`, as `npm publish` of a new scoped package defaults to restricted.
- Node 26.13.1 bundles npm 11.20.0; npm's latest is 12.2.2 and its latest 11.x is 11.21.2, both released 2026-11-04; 11.21.1 was released 2026-10-15.
- After the owner published 0.2.0 by hand (2026-11-11), npm listed only a placeholder version, `0.0.0-stage`, for a few minutes while it processed each new package ("may take a few minutes to become available"); the placeholder stays in each package's version list. `npm stage list <package> --json` then printed `[]` for both: a JSON array, empty when nothing is staged. A staged entry's fields are still unseen.
- The `release` environment allows only `v*` tags and requires the owner's review.

## Goals / Non-Goals

**Goals:**

- Both packages on npm from 0.2.0 on, installable as `npx @tsnee/ears-checker-cli` and `npm install @tsnee/ears-checker`.
- From 0.3.0 on, the release workflow stages exactly the tarballs it checked, with no npm credential stored anywhere and no way for the workflow to make a version public.
- The owner's approval checks the staged bytes against the release before approving.

**Non-Goals:**

- Provenance for 0.2.0: it's published by hand, so it has none. Its tarballs carry GitHub attestations on the release.
- Dependabot updates for the pinned npm: Dependabot is set up for actions only; the pin is raised by hand, like `.github/tools`.
- An unscoped `ears-check` package on npm.

## Decisions

### 1. 0.2.0 by hand, from the release's own tarballs

npm configures a trusted publisher only for an existing package, so the owner publishes 0.2.0 first, from their terminal: a one-off script (kept in the session's scratchpad, not the repository, since it's never run again) downloads both tarballs and `SHA256SUMS` from the GitHub Release, checks each against its checksum and attestation and its `package.json` against the expected name and version, and runs `npm publish ./<file>.tgz --access public`, which asks for the second factor. The bytes on npm are the release's, so no build happens outside CI.

_Alternative considered:_ staging 0.2.0 by hand and approving it. Staging a new package makes npm create a placeholder version, `0.0.0-stage`, which users would see.

### 2. A staging job in the release workflow, after the draft release

A new job, `npm`, `needs: [tag, draft-release]`, so it runs only once the Central Portal upload and the draft GitHub Release have succeeded:

- **Environment `npm`**, allowed only for `v*` tags, with no reviewer. The trusted publisher names it, so npm accepts a token only from a tag's run of `release.yml` in that environment. No reviewer, because the owner's approval happens on npm, with a second factor (Decision 4); a second approval in GitHub would guard nothing more.
- **Permissions:** `contents: read` and `id-token: write`, nothing else.
- **Steps:** a sparse checkout of `.github/npm` only (`persist-credentials: false`); Node 26 from `actions/setup-node` at its pinned SHA; `npm ci --prefix .github/npm --ignore-scripts`; `actions/download-artifact` of `release-jvm`; a check that each tarball's `package/package.json` names its package at the tag's version (with `tar` and `jq`), before staging either; then `npm stage publish ./<file>.tgz --provenance --access public --ignore-scripts` for the library, then the command line, with the pinned npm, each tried whatever became of the other, and the step failing at the end with the names of those not staged. (Changed after review: the first version also checked that exactly the two tarballs were there, which `draft-release` already checks for the same artifact; staged without `--ignore-scripts`, which npm doesn't need for a tarball but costs nothing; and stopped at the first failure, so a rerun after a partial staging never reached the command line.)
- It runs no code from the repository: only the pinned npm, on files from this run.

_Alternatives considered:_

- **Design A**, `npm publish` behind the `npm` environment's approval: anyone who could get a run past that approval could publish. It stays the fallback (Decision 6).
- **Staging before the Central Portal upload**, so npm waits with everything else: then a failed upload leaves staged versions to reject by hand.

### 3. npm pinned in its own lockfile

`.github/npm/package.json` and its `package-lock.json` pin `npm` itself at 11.21.1, the latest 11.x at least two weeks old today (11.15.0 is the floor for `npm stage`). Not in `.github/tools`: that one's `node_modules/.bin` goes on CI's `PATH`, so it would replace the runner's npm in every CI job. The job calls `.github/npm/node_modules/.bin/npm` by path. A step prints its version.

### 4. Approval by `scripts/approve-npm-release`, in the owner's terminal

`scripts/approve-npm-release --version <version>` (Bash 3.2, `set -euo pipefail`):

1. Refuses if the GitHub Release for `v<version>` is missing or still a draft (`gh release view --json isDraft`), or if the version isn't on Maven Central yet (its CLI POM on `repo1.maven.org`), so npm is always last, and a deployment dropped on the Central Portal never gets its npm packages approved. (Added after review: the first version checked only the GitHub Release.)
2. Downloads `SHA256SUMS` from it.
3. For each package: finds the staged version's stage id (`npm stage list <package> --json`), downloads its tarball (`npm stage download`), and checks its SHA-256 against `SHA256SUMS` and its attestation (`gh attestation verify` with the release workflow as signer and the tag as source ref). Then `npm stage approve <id>`, which asks for the second factor.
4. Carries on past one package's failure, prints each package's status, and exits non-zero if any wasn't approved.

The `npm stage list --json` output's shape isn't documented and can't be seen until something is staged, so the script parses it defensively (it fails, approving nothing, on any shape it doesn't recognize) and a task checks it at the first staged release (Decision 7). Until then the script is exercised against 0.2.0's release, which has nothing staged: the draft check, the download, and the "nothing staged" report.

_Alternative considered:_ approving on npmjs.com. The page shows npm's own checksums, not the release's SHA-256, so it can't show that the staged bytes are the ones the release attests.

### 5. The owner's order, and a dropped deployment

CLAUDE.md's Releasing steps become: publish the draft GitHub Release, then publish the Central Portal deployment, then run `scripts/approve-npm-release`. If the owner drops the deployment instead, they reject both staged versions (`npm stage reject`, with the second factor); the GitHub Release is already published by then, so the fix ships as a new patch version. The `npm` GitHub environment was created during apply (2026-11-11), before anything uses it, since GitHub would otherwise create it on the job's first run with no rule at all: its one deployment rule is the tag pattern `v*` (a tag rule; as a branch rule, a branch named `v…` could stage), and it has no reviewer. One-time setup before the first staged release (0.3.0), within two days of tagging it: on each package `npm trust github <package> --file release.yml --repo tsnee/ears-checker --env npm --allow-stage-publish`, and set each package's publishing access to "Require two-factor authentication and disallow tokens".

### 6. Fallback if a staged version has no provenance

If 0.3.0, once approved, shows no provenance on npm, a follow-up change switches the job to design A: the trust gets `--allow-publish` in place of `--allow-stage-publish`, the job runs `npm publish` with the `npm` environment requiring the owner's review, and the approval script is retired. 0.3.0 itself then stays without provenance; its tarballs still carry the release's attestations.

### 7. Checks that need a real release

Two things can be checked only at 0.3.0's release, and the tasks list them for then:

- `npm stage list --json`'s shape. If the script doesn't recognize it, the owner approves from the script's report on the same checks, done by hand (`npm stage download`, `shasum -a 256`, `gh attestation verify`), and the script is fixed in the next change.
- Provenance on the approved version (Decision 6).

### 8. The next version, 0.3.0, and specs without the current version

`publishVersion`'s snapshot becomes `0.3.0-SNAPSHOT` (npm: `0.3.0-dev`), and the documents that name the current snapshot follow.

The specs stop naming the current snapshot, so a version bump no longer changes them: `release-publishing`'s snapshot requirement says "the next minor version after the latest release, with the suffix `-SNAPSHOT`", `npm-package`'s development version is "the build's snapshot version with `-dev` in place of `-SNAPSHOT`", and the scenarios that named the current version (`release-publishing`'s "Local publish" and "Empty value", the two packages' "Development build") give it as a condition ("the latest release is 0.2.0", "the build's snapshot version is `0.3.0-SNAPSHOT`") or not at all.

The specs' other version numbers stay: each is a scenario's concrete example tied to its own condition ("the release run for tag `v0.2.0`", "a checker built between releases, after 0.1.1", "a user who installed the app at 0.2.0"), which stays true after any release, and the project's rules want scenarios with concrete values. The build doesn't derive the snapshot from the latest tag; the bump stays a hand edit after each minor release, as CLAUDE.md says.

## Risks / Trade-offs

- **Partial staging.** The job tries both packages whatever became of the other, and fails naming those it didn't stage. A rerun stages whichever is missing; the one already staged fails again, harmlessly, since its version is taken. (Added after review: the first version stopped at the first failure, so a rerun never reached the command line. Whether rejecting a staged version frees its number is unknown, so nothing relies on it.) Rerunning only this job never reruns `publish`.
- **A staged version may expire** before it's approved; the docs don't say when. The owner approves the same day; if it's gone, rerun the `npm` job.
- **The trust expires if unused for two days**, and npm doesn't check it when it's saved: a typo shows only when the job fails. Set it up just before tagging 0.3.0 and expect to rerun the job after fixing it.
- **The approval script's parsing is unverified** until 0.3.0 (Decision 7).
- **Unverified assumptions:** that staging carries provenance (Decision 6); that a staged version lasts at least a day; that `npm stage list --json` gives the stage id with each version; that `npm stage list` and `npm stage download` never ask for a second factor (npm's table says only `approve` and `reject` do), since the script captures their output and npm prompts only on a terminal; and that rejecting a staged version doesn't free its number for a new staging. Checked today: the npm versions, staging a tarball as it is (dry run), the trust and staging requirements, the `release` environment's rules, the packages' repository URL, and Node 26's bundled npm.
