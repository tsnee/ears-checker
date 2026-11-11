# Proposal

## Why

This repository was extracted from the one it was built in with `git filter-repo`, keeping the EARS checker's code, specs, archived changes, and build and tool files with their history. The files came over as they were, so the build still describes the old repository: `build.mill` defines a `semantics` module whose sources didn't come along, nothing is set up for publishing, and `openspec/config.yaml`'s context describes the other project. There is no README or license.

A downstream project now depends on this checker as a published artifact. Until 0.1.0 is on Maven Central, it runs `io.github.tsnee:ears-checker-cli_3:0.1.0-SNAPSHOT` from `~/.ivy2/local`, so this repository has to publish that locally.

## What Changes

- **Build.** `build.mill` loses the `semantics` module. `ears`, `ears.js` and `ears.cli` become publishable, as `io.github.tsnee:ears-checker_3`, `ears-checker_sjs1_3` and `ears-checker-cli_3`, version 0.1.0-SNAPSHOT, license CC0 1.0.
- **OpenSpec context.** `openspec/config.yaml`'s `context` describes this checker; its EARS rules stay as they are.
- **New files.** `README.md`, `LICENSE` (CC0 1.0), and `docs/worklog.md`.
- **Local publish.** `./mill __.publishLocal` puts the three artifacts in `~/.ivy2/local`, where the downstream project finds the CLI.

No behavior changes: the specs, code, and tests are as extracted, and must pass as they are.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This change sets `skip_specs: true`: it is build and repository setup only.

## Impact

- `build.mill`, `openspec/config.yaml`, and the new files above.
- `~/.ivy2/local/io.github.tsnee/` on this machine.
- No new dependencies.
