# Design

## Context

Observations (2026-11-11, at d8ee67b):

- **The install** (`scripts/check-npm-cli`): `npm install "$tarball"` into an empty project, with no lockfile and no `--ignore-scripts`; then `npm ls` names are compared with `ears/markdown/js/package-lock.json`'s keys (names only). With `--out`, the tarball and jar are copied to `kept/` after the cases, so after the installed code has run. CI runs it in `check` with `--out kept` before the `release-jvm` upload, and in `check-jdk17` without `--out`.
- **The pins:** `ears/markdown/js/package-lock.json` (lockfile version 3) records `commonmark` 0.31.2 (dependencies `entities ~3.0.1`, `mdurl ~1.0.1`, `minimist ~1.2.8`), `entities` 3.0.1, `mdurl` 1.0.1, and `minimist` 1.2.8, each with `resolved` and `integrity`, all under top-level `node_modules/` keys.
- **The CLI package** (`@tsnee/ears-checker-cli`, `package.json`): `dependencies: {commonmark: "0.31.2"}`, `bin: {"ears-check": "./bin/ears-check.js"}`, `type: module`, `engines: {node: ">=22"}`, no `scripts`.
- **Probe** (scratchpad, npm 11 with Node 25, a temporary cache): a project whose `package.json` depends on `file:<tarball>`, with a lockfile of the tarball's entry (`resolved: file:<tarball>`, its `sha512` integrity from `openssl dgst -sha512 -binary | base64`, its version, `bin`, `engines`, and dependencies) plus the facade lockfile's four entries, installed with `npm ci --ignore-scripts`: 5 packages, the tree exactly as pinned, and `ears-check --help` ran. With a wrong tarball integrity, `npm ci` failed with `EINTEGRITY`.
- **Install scripts** (probe after the code review, same day): a fake CLI tarball whose `package.json` declares `postinstall: touch <marker>`, installed through the helper: no marker was written, and the installed command ran. `npm ci` writes "added N packages" to standard output even with `--loglevel error`, so the helper sends npm's output to standard error.
- **Elsewhere:** `scripts/time-cli` installs the packed CLI the same unpinned way (a developer tool, not run in CI); `scripts/check-npm-package` installs the library's own tarball, which has no dependencies, with plain `npm install`.

## Goals / Non-Goals

**Goals:**

- No dependency of the CLI package is resolved fresh or runs an install script in a job that builds release files; what runs is exactly what the conformance suite runs on.
- A file kept for a release that changes while a check runs fails that check.

**Non-Goals:**

- Pinning what a user's `npm install @tsnee/ears-checker-cli` resolves: npm users get `commonmark` exactly (the package pins it) and its dependencies by its ranges, as for any npm package.

## Decisions

### 1. `npm ci` from a lockfile built for the project

- `scripts/npm-cli-project`, sourced by `check-npm-cli` and `time-cli`, defines a function that writes the project's `package.json` (depending on `file:<tarball>`) and `package-lock.json` with `jq`: the tarball's entry, its fields read from the tarball's own `package.json`, its integrity computed from the file, plus every `node_modules/` entry of the facade lockfile. Then `npm ci --ignore-scripts`. It first checks that the CLI package's `dependencies` are exactly the facade lockfile's root `dependencies`, so a drift fails clearly instead of as an npm error.
- The existing name comparison stays, now as a second check on what was installed.

### 2. Keep before running, check after

- With `--out`, the tarball and jar are copied right after packing, before the install; then the checksum of every file in the output directory is logged, including any an earlier check kept there (CI's `check-npm-package --out kept` leaves the library's tarball in the same `kept/`), and at the end each must still match. The main control is the pinned lockfile, since the same commonmark already runs in `./mill __.test`; this is a second line (code review, 2026-11-11).
- The helper's variables start with `ncp_` (sh has no local variables), and it computes the tarball's digest as a step of its own, checking its length, so an `openssl` failure is reported as such rather than as npm's integrity error.

### 3. The library's check, the same way

- `check-npm-package` adds `--ignore-scripts`: its package has no dependencies to pin, but no install here runs scripts. With `--out` it also keeps its tarball right after packing, before publint, attw, tsc, and the consumer run, and checks it unchanged at the end.

## Risks / Trade-offs

- **[Lockfile format]** The built lockfile relies on npm's lockfile version 3 and `file:` `resolved` values; the probe ran on npm 11.21.1, and CI's Node 22 and 26 bring npm 10.9.9 and 11.20.0 (nodejs.org's release index, 2026-11-11: v22.23.4 and v26.13.0). The probe gave the same results under npm 10.9.9 (run with Node 25): the pinned tree, `--help`, and `EINTEGRITY`.

## Open Questions

- None.
