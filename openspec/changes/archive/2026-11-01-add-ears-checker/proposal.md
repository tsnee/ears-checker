# Proposal

## Why

This repo now requires every requirement to be strict EARS, but nothing checks that: OpenSpec's strict validation only requires an uppercase SHALL or MUST somewhere in the statement. No mature EARS checker exists (the ones found are regex style packs or abandoned), and other planned work will need an EARS parser anyway. Building it as a standalone library plus CLI gives this repo enforcement now and gives a candidate open-source tool later.

## What Changes

- New Mill module `ears`: a pure Scala 3 library that classifies one requirement sentence as one of the six EARS templates and returns a syntax tree (template, clauses, system name, response, with source spans), or returns diagnostics with spans and suggested fixes. Two profiles: classic EARS (lowercase "shall") and OpenSpec (uppercase "SHALL"). It has no dependency on ZIO, so it can move to its own repository unchanged. A Scala.js build of the same sources runs the same tests on Node, so the library stays portable; it isn't packaged.
- New Mill module `ears.cli`: a command-line checker that reads plain text (one requirement per line), Markdown, or OpenSpec's `show --json` output for a change or a spec; prints findings as text or JSON; and exits nonzero when any requirement fails.
- `scripts/check-openspec` additionally runs the CLI over every OpenSpec change and spec after strict validation, so the existing Claude Code hook enforces EARS too.
- Out of scope, for later changes: Scala.js/npm packaging, native binaries, SARIF output, a pre-commit framework hook, a GitHub Action, an MCP server, an LSP server, weak-word and passive-voice checks, detecting two responses joined by "and" inside one SHALL clause, and checking that the system is named consistently across requirements.

## Capabilities

### New Capabilities

- `ears-classifier`: classifying a single requirement sentence against the EARS templates under a profile, producing a syntax tree or diagnostics with spans and fixes, and rendering a tree back to canonical text.
- `ears-cli`: the command-line checker: input formats, requirement extraction, output formats, and exit status.
- `openspec-check`: the repository's combined OpenSpec gate (strict validation plus EARS checking) used by the Claude Code hook.

### Modified Capabilities

None. There are no existing specs.

## Impact

- `build.mill`: two new modules under the strict `Base` settings. `ears` follows the pure rules (all WartRemover warts, including `Any`); `ears.cli` uses ZIO and drops only the `Any` wart.
- New dependencies: a parser-combinator library for `ears`; for `ears.cli`, ZIO, a JSON library, a command-line argument library, and a Markdown parser. Choices and versions are in the design.
- `scripts/check-openspec` gains an EARS step; the hook and `CLAUDE.md` workflow notes stay valid but `CLAUDE.md` gains the new modules in its layout section.
