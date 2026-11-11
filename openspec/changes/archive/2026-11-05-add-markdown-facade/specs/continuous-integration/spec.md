## MODIFIED Requirements

### Requirement: Test failures

If any test fails on the JVM, on Node, or on Scala Native, then the CI workflow SHALL fail the run.

#### Scenario: Failing JVM test

- **WHEN** a pushed commit makes one test in `ears.cli.test` fail
- **THEN** the run fails

#### Scenario: Failing Node test

- **WHEN** a pushed commit makes a test fail only in the Scala.js build `ears.js.test`
- **THEN** the run fails

#### Scenario: Failing Scala Native test

- **WHEN** a pushed commit makes a conformance fixture fail only in the Scala Native build `ears.markdown.native.test`
- **THEN** the run fails
