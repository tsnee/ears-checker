## ADDED Requirements

### Requirement: Node CLI differences
If the Node CLI, installed from its packed npm package, writes different standard output or messages than the JVM CLI's jar on the same arguments and input, or exits with a different status, then the CI workflow SHALL fail the run.

#### Scenario: A changed message on one platform
- **WHEN** a pushed commit changes the Node entry point's "no such file" message only
- **THEN** the run fails at the Node CLI check, naming the input whose messages differ

### Requirement: Node CLI check on the oldest Node
The CI workflow SHALL run the Node CLI check on the oldest Node version the CLI package declares, as well as on the Node version the build uses.

#### Scenario: Node 22
- **WHEN** a CI run checks a pull request
- **THEN** the Node CLI check runs once on Node 22 and once on Node 26
