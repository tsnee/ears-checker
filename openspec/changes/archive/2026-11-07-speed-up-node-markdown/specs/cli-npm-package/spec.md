## MODIFIED Requirements

### Requirement: Markdown parser dependency
The CLI npm package SHALL depend on exactly the version of `commonmark` that the Markdown conformance suite runs on Node.

#### Scenario: Dependency in the package metadata
- **WHEN** a developer builds the CLI npm package while the conformance suite's lockfile pins `commonmark` 0.31.2
- **THEN** its `package.json` lists `commonmark` at exactly `0.31.2` under `dependencies`, and nothing else
