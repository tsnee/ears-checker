## ADDED Requirements

### Requirement: No dependencies in the CLI package

The CLI npm package SHALL declare no dependencies.

#### Scenario: Dependencies in the package metadata

- **WHEN** a developer builds the CLI npm package and reads its `package.json`
- **THEN** it has no `dependencies` entry

### Requirement: Parser module in the CLI package

The CLI npm package SHALL hold the Markdown parser's WebAssembly module as a file beside `main.js`.

#### Scenario: The packed module

- **WHEN** a developer packs the CLI npm package and lists its files
- **THEN** it holds `cmark-gfm.wasm` next to `main.js`, with the SHA-256 checksum the repository records for the module

## REMOVED Requirements

### Requirement: Markdown parser dependency

**Reason**: The CLI reads Markdown with cmark-gfm's WebAssembly module, which the package holds, instead of the `commonmark` package.

**Migration**: None for users: `npx @tsnee/ears-checker-cli` installs one package and nothing with it.
