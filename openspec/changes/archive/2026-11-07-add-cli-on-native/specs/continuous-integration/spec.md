## ADDED Requirements

### Requirement: Native CLI differences
If the native binary writes different standard output or messages than the JVM CLI's jar on the same arguments and input, or exits with a different status, then the CI workflow SHALL fail the run.

#### Scenario: A changed exit status on one platform
- **WHEN** a pushed commit makes only the native binary exit with a different status for a failing file
- **THEN** the run fails at the native CLI check, naming the input whose statuses differ
