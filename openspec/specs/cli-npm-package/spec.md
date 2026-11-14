# cli-npm-package Specification

## Purpose

Packages the `ears-check` command for Node as the npm package `@tsnee/ears-checker-cli`, separate from the library package `@tsnee/ears-checker`.

## Requirements

### Requirement: CLI package name

The CLI npm package SHALL be named `@tsnee/ears-checker-cli`.

#### Scenario: Name in the package metadata

- **WHEN** a developer builds the CLI npm package and reads its `package.json`
- **THEN** its `name` is `@tsnee/ears-checker-cli`

### Requirement: Command name

The CLI npm package SHALL provide one command, named `ears-check`.

#### Scenario: Running it through npx

- **WHEN** a project that has installed the packed CLI package runs `npx ears-check --help`
- **THEN** the usage text for `ears-check` is printed and the exit status is 0

### Requirement: CLI package version

The CLI npm package SHALL carry the same version as the library npm package.

#### Scenario: Development build

- **WHEN** a developer builds the CLI npm package with no release version set while the build's snapshot version is `0.3.0-SNAPSHOT`
- **THEN** its `package.json` has `version: "0.3.0-dev"`, as the library npm package does

### Requirement: CLI package license

The CLI npm package SHALL declare the license `CC0-1.0` and include the license text.

#### Scenario: License in the package

- **WHEN** a developer builds the CLI npm package
- **THEN** its `package.json` has `license: "CC0-1.0"` and the package contains a `LICENSE` file

### Requirement: CLI package repository URL

The CLI npm package SHALL name `git+https://github.com/tsnee/ears-checker.git` as its repository URL.

#### Scenario: Repository in the package metadata

- **WHEN** a developer builds the CLI npm package and reads its `package.json`
- **THEN** `repository` is `{"type": "git", "url": "git+https://github.com/tsnee/ears-checker.git"}`

### Requirement: CLI package Node versions

The CLI npm package SHALL declare Node 22 as the oldest Node version it supports.

#### Scenario: Engines in the package metadata

- **WHEN** a developer builds the CLI npm package and reads its `package.json`
- **THEN** `engines.node` is `>=22`

### Requirement: No build machine paths in the CLI package

The CLI npm package SHALL contain no file path from the machine that built it.

#### Scenario: Built on a developer machine

- **WHEN** a developer whose repository is under `/Users/alex/src` builds the CLI npm package
- **THEN** no file in the package contains `/Users/alex`

### Requirement: No source map in the CLI package

The CLI npm package SHALL contain no source map.

#### Scenario: Packed CLI package

- **WHEN** a developer packs the CLI npm package
- **THEN** no file in it ends in `.map`, and its `main.js` has no `sourceMappingURL` comment

### Requirement: CLI code size

The CLI npm package SHALL ship its code in a `main.js` of at most 2,100,000 bytes.

#### Scenario: Development build

- **WHEN** a developer builds the CLI npm package
- **THEN** its `main.js` is at most 2,100,000 bytes

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
