# Tasks

No change to the build, sources, tests, or `scripts/check-openspec`.
If one seems needed for CI to pass, stop and ask first. Pushing,
opening pull requests, and merging happen in the user's own shell;
Claude reads results with `gh` (read-only) and never pushes.

## 1. Workflow files

- [x] 1.1 Write `.github/workflows/ci.yml` as in design Decisions 1–6 (triggers, PR-only cancelling, `permissions: contents: read`, `persist-credentials: false`, 30-minute timeout, Temurin 27, Node 26, `coursier/cache-action`, OpenSpec CLI 1.14.1, then `./mill __.checkFormat`, `./mill __.compile`, `./mill __.test`, `scripts/check-openspec`); verify `yq` parses it and `actionlint .github/workflows/ci.yml` reports nothing (the user installs `actionlint` with `brew install actionlint`; if it is missing, stop and ask)
- [x] 1.2 Check the pins: every `uses:` in `ci.yml` is a 40-character SHA with its release in a comment, and each SHA equals `gh api repos/<owner>/<repo>/commits/<tag> --jq .sha` for that tag; verify with `yq` listing the `uses:` values next to the `gh` results
- [x] 1.3 Write `.github/dependabot.yml`: the `github-actions` ecosystem, directory `/`, weekly, with every update in one group; verify `yq` parses it
- [x] 1.4 Update `CLAUDE.md`: add `.github/` to Layout, say under Build and test that CI runs the same four commands on pushes to `main` and pull requests, and drop GitHub Actions CI from Likely next steps; add one line on CI to the README's Building section; verify by reading both with `mq`

## 2. Runs on GitHub

- [x] 2.1 After the user pushes branch `add-github-actions-ci` and opens a pull request into `main`, read the run with `gh run list` and `gh run view --log`; verify every step succeeds, the job ran on an Ubuntu runner, and the log's "GITHUB_TOKEN Permissions" lists only `Contents: read` and the `Metadata: read` GitHub always grants
- [ ] 2.2 Ask the user to push one more commit to the pull request (any small fix from review, or an empty commit); verify the new run restores the Coursier and Mill caches (`cache-hit-coursier` and `cache-hit-mill` true in the log) and is faster than the first
- [ ] 2.3 Failure check on a throwaway branch: the user pushes `ci-failure-check` (from the pull request branch, with one line in `ears/cli/src/io/github/tsnee/ears/cli/Main.scala` indented by three spaces) and opens a draft pull request; verify the run fails at the format step and no later step runs, then the user closes the pull request and deletes the branch
- [ ] 2.4 After the user merges the pull request, read the run for the push to `main`; verify it succeeds and that it wasn't cancelled by a later push

## 3. Wrap-up

- [x] 3.1 Run `./mill __.test` and `scripts/check-openspec` locally; verify both pass and `git status` shows no change outside `.github/`, `CLAUDE.md`, `README.md`, `docs/worklog.md`, and this change
- [x] 3.2 Add a dated line to `docs/worklog.md`; verify by reading it with `mq`

## Workflow follow-up

- Commit the plan and the implementation as separate commits; the user pushes.
- After the user enables Dependabot version updates if GitHub asks, check the repository's Dependabot page shows no configuration error.
- Verify, then archive (syncing the `continuous-integration` spec into `openspec/specs/`), and commit the archive.
