# test-coverage Specification

## Purpose

Measures how much of the library and the command line the tests exercise, and fails the build
when a module's coverage falls below its minimum, without changing what is published.

## Requirements

### Requirement: Coverage measured by the tests
When the JVM tests of `ears`, `ears.cli`, `ears.npm`, `ears.linkCheck`, or `ears.markdown` run, the build SHALL record the statement and branch coverage of that module's sources.

#### Scenario: Coverage report after tests
- **WHEN** a developer runs `./mill __.test` and then asks the build for a coverage report
- **THEN** the report gives statement and branch percentages for `ears`, for `ears.cli`, for `ears.npm`, for `ears.linkCheck`, and for `ears.markdown`

### Requirement: Statement coverage minimum
If a module's statement coverage is below that module's statement minimum, then the coverage check SHALL fail and name the module and both percentages.

#### Scenario: Untested function in the library
- **WHEN** a change adds a function to `ears` that no test calls, and `ears`'s statement coverage drops below its minimum
- **THEN** the coverage check fails and reports `ears`'s statement coverage and its minimum

### Requirement: Branch coverage minimum
If a module's branch coverage is below that module's branch minimum, then the coverage check SHALL fail and name the module and both percentages.

#### Scenario: Untested match case in the command line
- **WHEN** a change adds a match case to `ears.cli` that no test reaches, and `ears.cli`'s branch coverage drops below its minimum
- **THEN** the coverage check fails and reports `ears.cli`'s branch coverage and its minimum

### Requirement: Coverage at the minimum
While every module's coverage is at or above its minimums, the coverage check SHALL pass.

#### Scenario: Current main
- **WHEN** the coverage check runs on a commit whose tests reach every minimum
- **THEN** it passes

### Requirement: Uninstrumented artifacts
The build SHALL publish jars that contain no coverage instrumentation.

#### Scenario: Published CLI jar
- **WHEN** `ears.cli`'s jar is built for publishing after the tests have measured coverage
- **THEN** no class in it refers to the coverage runtime
