# Tasks

All work is on branch `batch-npm`. The owner pushes from their own shell, and does everything on npm and in GitHub's settings; Claude reads runs with `gh`.

## 1. 0.2.0 on npm (the owner, after the 0.2.0 release is published)

- [x] 1.1 The owner enables 2FA on the npm account and runs the hand-publish script (decision 1) with `--dry-run`, then for real; `npm view @tsnee/ears-checker@0.2.0 version` and `npm view @tsnee/ears-checker-cli@0.2.0 version` print `0.2.0`, and `npx @tsnee/ears-checker-cli --help` prints the usage
- [x] 1.2 The owner sets each package's publishing access to "Require two-factor authentication and disallow tokens"; `npm access get status` (or the package's settings page) shows it

## 2. The next version

- [x] 2.1 `publishVersion`'s snapshot becomes `0.3.0-SNAPSHOT` (decision 8); `./mill show ears.publishVersion` and `./mill show ears.npm.npmVersion` print `0.3.0-SNAPSHOT` and `0.3.0-dev`, and `./mill __.test` passes
- [x] 2.2 The documents that name the snapshot (`docs/json-output.md`'s example, CONTRIBUTING.md or README.md if they do); `grep` finds no `0.2.0-SNAPSHOT` or `0.2.0-dev` outside archives, the changelog, the work log, and `ears-cli`'s example (which is tied to "after 0.1.1"), and no main spec names the current snapshot once the change is archived

## 3. Staging from the release workflow

- [x] 3.1 `.github/npm/package.json` and `package-lock.json` pinning `npm` 11.21.1 (decision 3); `npm ci --prefix .github/npm --ignore-scripts` with a temporary cache, then `.github/npm/node_modules/.bin/npm --version` prints `11.21.1`
- [x] 3.2 The `npm` job in `release.yml` (decision 2): `needs: [tag, draft-release]`, environment `npm`, `contents: read` and `id-token: write`, a sparse checkout of `.github/npm`, the tarballs' names and `package.json` checked before staging either, then `npm stage publish --provenance --access public` for each, every action pinned by SHA. Its check step is rehearsed locally on 0.2.0's release tarballs (pass) and on a tarball repacked with version `0.2.0-dev` (fail, naming it); YAML parses with `yq`
- [x] 3.3 The workflow's header comment and the `draft-release` comment say where npm comes in

## 4. Approval

- [ ] 4.1 `scripts/approve-npm-release` (decision 4); `bash -n` and `shellcheck` pass. Against 0.2.0's published release it downloads `SHA256SUMS` and reports nothing staged for either package, approving nothing and exiting non-zero; against a draft release (or a missing tag, `v9.9.9`) it refuses before touching npm
- [x] 4.2 CLAUDE.md's Releasing section (decision 5: the owner's order, the dropped-deployment case, the one-time setup before 0.3.0 and the two-day limit), its Layout (the script and `.github/npm`), and CONTRIBUTING.md if it lists scripts; verify with `mq`

## 5. Documents for users

- [x] 5.1 README: the quick start installs from npm by the scoped names (`npx @tsnee/ears-checker-cli`, `npm install @tsnee/ears-checker`), never an unscoped name; the npm badge joins the others; "it isn't on npm yet" is gone. Both package READMEs likewise. Check every command in them against the published 0.2.0 in an empty directory with a temporary cache
- [x] 5.2 `docs/release-files.md`: the npm tarballs are also on npm, the same files (how to compare them); `ears.releaseNotes.run notes 0.2.0 CHANGELOG.md docs/release-files.md` renders it. (Provenance isn't claimed: a release's notes are rendered before npm can show it; it's added once 0.3.0 shows it, in the follow-up.)
- [x] 5.3 CHANGELOG's Unreleased (the packages on npm, staged releases with provenance from 0.3.0), the roadmap (the npm item done, its follow-ups), and the work log; `ears.releaseNotes.run check CHANGELOG.md` passes

## 6. Checks, review, commits

- [x] 6.1 Run the gate (CLAUDE.md's list) and report coverage from `scripts/coverage-records`
- [x] 6.2 Run `/code-review` in a subagent on this change, and `/security-review` in another (the release workflow gains a job with an OIDC token); the owner chooses which findings to fix. The owner took seven of the code review's eight findings and one security suggestion: the approval script requires the version on Maven Central; the `npm` job stages each package whatever became of the other, with `--ignore-scripts`, and checks only the tarballs' `package.json` (`draft-release` already checks the file names); the `npm` environment was created now, with a `v*` tag rule and no reviewer; CONTRIBUTING.md lists the approval script; the release notes say the packages are on npm only once approved; and two workflow comments were fixed. For the fourth finding (npm's second-factor prompt), only the assumption was recorded
- [ ] 6.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes, from the records
- [ ] 6.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- After CI passes and verify is clean, archive the change (creating `npm-publishing`).
- At the 0.3.0 release (decisions 5 to 7), the owner, within two days before tagging: adds the stage-only trust on each package (the `npm` environment exists since 2026-11-11, with its `v*` tag rule). After the release: run `scripts/approve-npm-release --version 0.3.0`; check `npm stage list --json`'s shape against the script, and that npm shows provenance for both packages. If it doesn't, plan design A (decision 6).
