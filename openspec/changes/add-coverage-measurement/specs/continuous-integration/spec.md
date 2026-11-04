# Spec Delta

## ADDED Requirements

### Requirement: Coverage failures
If the coverage check fails, then the CI workflow SHALL fail the run.

#### Scenario: Coverage below a minimum
- **WHEN** a pull request's tests pass but leave `ears.cli`'s statement coverage below its minimum
- **THEN** the run fails

### Requirement: Coverage summary
When the tests have run, the CI workflow SHALL write each module's statement and branch coverage to the run's summary page.

#### Scenario: Passing run
- **WHEN** a run's tests and coverage check pass
- **THEN** the run's summary page shows the statement and branch percentages for `ears` and `ears.cli`

#### Scenario: Coverage below a minimum
- **WHEN** a run's tests pass and its coverage check fails
- **THEN** the run's summary page still shows each module's percentages
