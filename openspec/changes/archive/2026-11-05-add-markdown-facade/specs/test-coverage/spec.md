## MODIFIED Requirements

### Requirement: Coverage measured by the tests
When the JVM tests of `ears`, `ears.cli`, `ears.npm`, `ears.linkCheck`, or `ears.markdown` run, the build SHALL record the statement and branch coverage of that module's sources.

#### Scenario: Coverage report after tests
- **WHEN** a developer runs `./mill __.test` and then asks the build for a coverage report
- **THEN** the report gives statement and branch percentages for `ears`, for `ears.cli`, for `ears.npm`, for `ears.linkCheck`, and for `ears.markdown`
