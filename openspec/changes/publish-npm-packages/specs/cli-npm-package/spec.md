## MODIFIED Requirements

### Requirement: CLI package version

The CLI npm package SHALL carry the same version as the library npm package.

#### Scenario: Development build

- **WHEN** a developer builds the CLI npm package with no release version set while the build's snapshot version is `0.3.0-SNAPSHOT`
- **THEN** its `package.json` has `version: "0.3.0-dev"`, as the library npm package does
