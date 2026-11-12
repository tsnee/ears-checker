## MODIFIED Requirements

### Requirement: CI run records

When `scripts/ci-run-record` is given a run of the CI or Release workflow, the script SHALL append one JSON record per job of each of that run's attempts, with the run, attempt, commit, workflow, job, conclusion, start, end, minutes rounded up, and whether the job was carried over from an earlier attempt, to `docs/measurements/ci-runs.jsonl`.

#### Scenario: A finished run

- **WHEN** a developer records run 43065132303
- **THEN** the file gains ten records, and a kept query sums them to 45 minutes

#### Scenario: A rerun

- **WHEN** a developer records run 43031719238, whose second attempt reran two jobs
- **THEN** the kept query gives its second attempt 12 minutes, from the two rerun jobs only

## ADDED Requirements

### Requirement: Other workflows refused

If `scripts/ci-run-record` is given a run of a workflow other than CI or Release, then the script SHALL fail with a message naming the run's workflow.

#### Scenario: A Dependabot run

- **WHEN** a developer records run 43140165200, a "Dependabot Updates" run
- **THEN** the script fails with a message naming "Dependabot Updates"

#### Scenario: A Release run

- **WHEN** a developer records run 43085781522, the 0.2.0 release run, which the file already holds
- **THEN** the script accepts it and says it's already recorded, rather than refusing it

### Requirement: Nothing written for a refused run

If `scripts/ci-run-record` refuses a run, then the script SHALL leave the records file and its directory as they were.

#### Scenario: A new records file

- **WHEN** a developer records the Dependabot run with `--records` naming a file in a directory that doesn't exist
- **THEN** the script fails, and neither the directory nor the file exists afterward
