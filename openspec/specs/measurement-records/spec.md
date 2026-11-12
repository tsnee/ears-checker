# measurement-records Specification

## Purpose

Keeps what the developer scripts measure as structured records, written by the scripts themselves, with the queries that turn them into the figures the docs report.

## Requirements

### Requirement: Timing records

When `scripts/time-cli` times a build on an input, the script SHALL append one JSON record of that timing, with its date, commit, machine, runtime versions, build, input, input size, and seconds, to its records file.

#### Scenario: One run of time-cli

- **WHEN** a developer runs `scripts/time-cli --builds jvm --copies 8`
- **THEN** `docs/measurements/time-cli.jsonl` gains two records, one for the Markdown input and one for the plain text, each naming the build `jvm`, 8 copies, and the commit

### Requirement: Timing table from records

The repository SHALL keep a query that renders any `scripts/time-cli` run's Markdown table in `docs/performance.md` from that run's records.

#### Scenario: A backfilled run

- **WHEN** a developer renders the "After speed-up-node-markdown" run from its records with the kept query
- **THEN** the table is identical to the one in `docs/performance.md`

### Requirement: Coverage records

When `scripts/coverage-records` runs after the tests, the script SHALL append one JSON record per measured module, with the date, commit, module, the time the tests measured it, and the statement count, statements invoked, statement rate, and branch rate from scoverage's XML report, to `docs/measurements/coverage.jsonl`.

#### Scenario: After the gate

- **WHEN** a developer runs `scripts/coverage-records` after `./mill __.test`
- **THEN** the file gains six records, and the one for `ears.cli` has the statement count and rates that `./mill ears.cli.scoverage.consoleReport` shows

### Requirement: CI run records

When `scripts/ci-run-record` is given a run of the CI or Release workflow, the script SHALL append one JSON record per job of each of that run's attempts, with the run, attempt, commit, workflow, job, conclusion, start, end, minutes rounded up, and whether the job was carried over from an earlier attempt, to `docs/measurements/ci-runs.jsonl`.

#### Scenario: A finished run

- **WHEN** a developer records run 43065132303
- **THEN** the file gains ten records, and a kept query sums them to 45 minutes

#### Scenario: A rerun

- **WHEN** a developer records run 43031719238, whose second attempt reran two jobs
- **THEN** the kept query gives its second attempt 12 minutes, from the two rerun jobs only

### Requirement: No duplicate CI records

If `scripts/ci-run-record` is given a run whose jobs the records file already holds, then the script SHALL leave the file unchanged.

#### Scenario: Recording a run twice

- **WHEN** a developer records the same finished run twice
- **THEN** the file holds that run's records once

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
