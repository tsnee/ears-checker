# Design

## Context

See proposal.md for why, and `specs/continuous-integration/spec.md`
for the required behavior. What the checks need on a clean runner:

- `./mill` downloads Mill 1.3.1's native launcher, and Mill
  downloads JDK 27 for the build itself (the `build.mill` header
  pins it). Neither needs a JDK on the runner.
- `ears.js` tests run on Node.
- `scripts/check-openspec` needs the `openspec` CLI (npm package
  `@fission-ai/openspec`, 1.14.1 locally), `jq`, and a `java` on the
  PATH that accepts `--sun-misc-unsafe-memory-access=allow`, which
  takes JDK 23 or newer. The Ubuntu runner's default `java` is
  older, so the runner needs a newer one installed.
- `ubuntu-latest` ships `jq` and `git`.

Current releases (looked up 2026-11-02): `actions/checkout` v7.0.1,
`actions/setup-java` v6.1.0, `actions/setup-node` v7.1.0,
`coursier/cache-action` v9.0.1. Temurin publishes JDK 27, and the
current Node LTS is 26.

## Goals / Non-Goals

**Goals:**
- A run on a warm cache finishes in a few minutes, with one job.
- The workflow runs the project's own commands unchanged, so a
  green run means the same thing as a clean local run.

**Non-Goals:**
- Changing `scripts/check-openspec`, the build, or the hook. The
  JDK-flag change revisits how the script starts `java`.
- Status badges, required status checks on `main`, and branch
  protection. Branch protection is a repository setting the user
  makes on GitHub.

## Decisions

### 1. One job, project commands in order

```text
checkout -> setup-java (Temurin 27) -> setup-node (26)
  -> coursier/cache-action
  -> npm install -g @fission-ai/openspec@1.14.1
  -> ./mill __.checkFormat
  -> ./mill __.compile
  -> ./mill __.test
  -> scripts/check-openspec
```

Format runs first because it is the cheapest to fail on. Compile
gets its own step even though `__.test` compiles, so a compile error
shows up as a compile failure in the run summary. Each step's exit
status fails the job, so the run fails if any check fails.

Alternative: parallel jobs per check. Each would download Mill, the
JDK, and the dependencies again, and minutes are billed per job,
rounded up, so one job is cheaper. If runs get slow, splitting
Node tests off is the first candidate.

### 2. A runner JDK just for the check script

`actions/setup-java` installs Temurin 27, the JDK the build uses, so
`scripts/check-openspec` runs the jar on the same major version as
the tests. Mill still downloads its own JDK 27 for the build, which
the cache keeps.

Alternative: point the script at Mill's JDK. That changes the
script, which belongs to the JDK-flag change.

### 3. Caching with coursier/cache-action

It caches `~/.cache/coursier/v1` (dependencies and Mill's JDK
download) and `~/.cache/mill` (the launcher), keyed on `build.mill`
and the `mill` launcher. `actions/setup-java` and
`actions/setup-node` use their own tool caches; neither needs
`cache:` here, since there is no Maven, Gradle, or npm lock file.

### 4. Triggers and cancelling

`push` on `main` and `pull_request` (opened, synchronize, reopened).
Pushes to other branches run only once they have a pull request,
which saves minutes. A `concurrency` group per pull request (or per
ref for pushes) cancels a run in progress only for pull requests:
every commit on `main` keeps its own result.

### 5. Pins and updates

Every `uses:` names a full commit SHA with the release in a comment
(`actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`).
A tag can be moved to other code; a SHA can't. `.github/dependabot.yml`
enables Dependabot's `github-actions` updates weekly, so the pins get
pull requests instead of going stale.

The OpenSpec CLI version is a literal in the install step. Dependabot
can't see it (there is no `package.json`), which matches the spec:
it changes only when the workflow is edited.

### 6. Least privilege

Top-level `permissions: contents: read`. `actions/checkout` runs
with `persist-credentials: false`, so later steps can't reuse the
token to push. A job timeout (30 minutes) stops a hung build from
using up the allowance.

## Risks / Trade-offs

- [The workflow can't be run from this sandbox: pushing goes
  through the user's shell] → Lint locally with `actionlint`, then
  the user pushes a branch and opens a pull request; Claude reads
  the results with `gh run view`, which only reads.
- [A first run downloads Mill, JDK 27 twice (Mill's and the
  runner's), and every dependency] → The cache makes later runs
  fast; only the first run per cache key is slow.
- [Dependabot opens a pull request per action update] → Weekly and
  grouped into one pull request, so at most one a week.
- [The OpenSpec CLI pin drifts behind local installs] → Bump the
  workflow and the local CLI together; the check script's output
  is the same, so a mismatch shows as a validation difference.

## Migration Plan

None. Removing `.github/workflows/ci.yml` turns CI off.
