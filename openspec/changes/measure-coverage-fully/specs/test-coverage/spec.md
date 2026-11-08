## MODIFIED Requirements

### Requirement: Coverage measured by the tests
When the JVM tests of `ears`, `ears.cli`, `ears.npm`, `ears.linkCheck`, `ears.markdown`, or `ears.releaseNotes` run, the build SHALL record the statement and branch coverage of that module's sources.

#### Scenario: Coverage report after tests
- **WHEN** a developer runs `./mill clean __.scoverage`, then `./mill __.test`, and then asks the build for a coverage report
- **THEN** the report gives statement and branch percentages for `ears`, for `ears.cli`, for `ears.npm`, for `ears.linkCheck`, for `ears.markdown`, and for `ears.releaseNotes`
