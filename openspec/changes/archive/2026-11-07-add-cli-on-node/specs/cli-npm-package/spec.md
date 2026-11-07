## Purpose

Packages the `ears-check` command for Node as the npm package `@tsnee/ears-checker-cli`,
separate from the library package `@tsnee/ears-checker`.

## ADDED Requirements

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

### Requirement: Markdown parser dependency
The CLI npm package SHALL depend on exactly the version of `mdast-util-from-markdown` that the Markdown conformance suite runs on Node.

#### Scenario: Dependency in the package metadata
- **WHEN** a developer builds the CLI npm package while the conformance suite's lockfile pins `mdast-util-from-markdown` 2.1.0
- **THEN** its `package.json` lists `mdast-util-from-markdown` at exactly `2.1.0` under `dependencies`, and nothing else

### Requirement: CLI package version
The CLI npm package SHALL carry the same version as the library npm package.

#### Scenario: Development build
- **WHEN** a developer builds the CLI npm package with no release version set
- **THEN** its `package.json` has `version: "0.2.0-dev"`

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
