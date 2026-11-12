## ADDED Requirements

### Requirement: CI run recorder test

When the CI workflow runs, the CI workflow SHALL run the CI run recorder's test, which checks the recorder's choice of runs with a stand-in for `gh`.

#### Scenario: A pull request

- **WHEN** CI runs on a pull request
- **THEN** a step runs `scripts/test-ci-run-record`, and the job fails if it fails
