# Proposal

## Why

Nothing on GitHub checks a push or a pull request. Every check runs
only on this machine, mostly through a Claude Code hook, so a commit
made elsewhere, or with the hook off, can land code that doesn't
compile, tests that fail, unformatted sources, or specs that fail
strict validation or the EARS check. Packaging comes next, and each
packaging change will add a release-only publishing step, so the
workflow they extend should exist first.

## What Changes

- **A CI workflow** in `.github/workflows/`, on GitHub-hosted Linux
  runners, for every push to `main` and every pull request. It
  checks formatting, runs every test suite (JVM and Node), and runs
  `scripts/check-openspec`; the run fails if any of them fails.
- **Published actions only.** Checkout, the JDK, Node, and the
  Coursier and Mill caches each come from a published action; the
  workflow's own steps only run the project's existing commands.
  Actions are pinned to commit SHAs, and Dependabot keeps the pins
  current.
- **Least privilege.** The workflow's token can only read the
  repository, and the workflow publishes nothing. Publishing for
  releases is added by the packaging changes.
- **The OpenSpec CLI** is installed from npm at the version used
  locally (1.14.1).

Not in this change: running the CLI on JDK 17 (the JDK-flag change
adds that with its rule for the JVM flag), macOS or Windows runners,
release publishing, and status badges.

## Capabilities

### New Capabilities

- `continuous-integration`: the checks GitHub runs on pushes and
  pull requests, what they cover, and the limits on what the
  workflow may do.

### Modified Capabilities

None.

## Impact

- New files: `.github/workflows/ci.yml` and `.github/dependabot.yml`.
- No change to the build, the sources, the tests, or
  `scripts/check-openspec`.
- GitHub Actions minutes: the repository is private, so runs use
  the account's free monthly allowance; Linux runners count once
  per minute.
- No new project dependencies. The workflow uses
  `actions/checkout`, `actions/setup-java`, `actions/setup-node`,
  and `coursier/cache-action`.
