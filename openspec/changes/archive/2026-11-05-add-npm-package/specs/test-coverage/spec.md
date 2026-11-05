## MODIFIED Requirements

### Requirement: Coverage measured by the tests
When the JVM tests of `ears`, `ears.cli`, or `ears.npm` run, the build SHALL record the statement and branch coverage of that module's sources.

#### Scenario: Coverage report after tests
- **WHEN** a developer runs `./mill __.test` and then asks the build for a coverage report
- **THEN** the report gives statement and branch percentages for `ears`, for `ears.cli`, and for `ears.npm`
