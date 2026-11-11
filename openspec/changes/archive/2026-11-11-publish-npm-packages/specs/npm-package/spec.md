## MODIFIED Requirements

### Requirement: Development version

While no release version is set, the npm package SHALL carry the build's snapshot version with `-dev` in place of `-SNAPSHOT`.

#### Scenario: Development build

- **WHEN** a developer builds the npm package with no release version set while the build's snapshot version is `0.3.0-SNAPSHOT`
- **THEN** its `package.json` has `version: "0.3.0-dev"`
