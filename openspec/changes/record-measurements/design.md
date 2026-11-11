# Design

## Context

Observations (2026-11-11, at c0460a4):

- **time-cli** prints its header (date, `git describe`, `uname -sm`, Node and Java versions) and
  a Markdown table with one row per input (`md xN`, `txt xN`) and a column per build; the numbers
  exist nowhere else. `docs/performance.md` holds three such runs (24 rows, three builds each)
  and the compile-rounds table, which already has records and a query
  (`docs/measurements/native-compile-rounds.jsonl` and `.jq`, committed in c0460a4).
- **Coverage:** `./mill <module>.scoverage.xmlReport` writes `out/<module>/scoverage/xmlReport.dest/
  scoverage.xml`, whose root element carries `statement-count`, `statements-invoked`,
  `statement-rate`, and `branch-rate` (checked for `ears`: 684, 656, 95.91, 92.04, as the console
  report shows). `yq -p xml` reads it. CI's "Report coverage" step prints `consoleReport` text to
  the run's summary page.
- **CI runs:** `gh run view <id> --json jobs` gives each job's `name`, `startedAt`,
  `completedAt`, `conclusion`, and `databaseId`; Claude has been rounding each job up to whole
  minutes and summing (the memory "Report CI minutes"). This branch has 24 runs.
- **Reruns** (found while backfilling, same day): `gh run view <id> --attempt 2` lists every
  job under a new id, but the jobs that weren't rerun keep attempt 1's start and end; summing
  attempt 2 naively gave run 43031719238 51 minutes for a rerun of two jobs (12 minutes). So each
  record says whether its job was carried over, and the query counts only the jobs that ran. The
  attempt's own start (`run_started_at` from `gh api .../runs/<id>/attempts/<n>`) separates them:
  in run 43031719238's attempt 2 (started 14:00:37) the two rerun jobs started after it and the
  eight carried ones before (checked after the code review; it gave the same flags as comparing
  with attempt 1 on all 195 records, without depending on job names).
- **Coverage's time:** scoverage stamps the XML report when it's generated, not when the tests
  ran; the measurement files the instrumented tests write (`out/<module>/scoverage/data.dest/
  scoverage.measurements.*`) carry the test time, so the newest one's is recorded.
- **Rule:** structured records written by the tool, the queries kept, fields added when a
  question needs them (global CLAUDE.md, 2026-11-11).

## Goals / Non-Goals

**Goals:**
- Every figure the docs or Claude report about timing, coverage, or CI minutes comes from records
  in `docs/measurements/` through a kept query.

**Non-Goals:**
- Changing CI: its summary page keeps `consoleReport`'s text, and no job writes to the
  repository.
- Recording other projects' runs, or older coverage figures (only percentages in prose exist).

## Decisions

### 1. JSON Lines in `docs/measurements/`, one file per kind, queries beside them

- `time-cli.jsonl`, `coverage.jsonl`, `ci-runs.jsonl`, appended to; `<kind>.jq` beside each,
  documented by a comment with the command that runs it. One record per measurement, flat, with
  its date, commit (`git describe --always --dirty`), and what produced it, so any field can be
  grouped or filtered later.

### 2. The scripts write the records

- `time-cli` gains `--records <file>` (default `docs/measurements/time-cli.jsonl`, an empty value
  for none) and keeps printing its table, now rendered by `time-cli.jq` from the run's records,
  so the printed table and the stored records can't disagree. Each record carries a run id (the
  run's start time) so a query can select one run.
- `scripts/coverage-records` runs each measured module's `xmlReport` and reads the root
  attributes with `yq`; `scripts/ci-run-record <run id>` reads `gh run view --json`, skips an
  attempt already recorded, and rounds each finished job up to whole minutes.
- Each script makes a run's records in a temporary file, checks their count (the attempt's jobs,
  or the six modules), and only then appends them, so a failure partway leaves nothing half
  recorded; `time-cli` instead appends each record as it's measured, so a run that fails later
  keeps the timings already taken. A relative `--records` path is relative to where the script
  is run. Messages go to standard error, and a failure names the command and its input.
- They're POSIX `sh`, like the other scripts, checked with `sh -n` and `shellcheck`, and tested by
  running them against known inputs (the spec's scenarios), as the other check scripts are.

### 3. Backfill

- The three `time-cli` runs: their tables are parsed into records once (with `mq` and `jq`),
  and `time-cli.jq` must render each back identically.
- This branch's CI runs: `scripts/ci-run-record` over each finished run on
  `batch-release-0.2.0`.

## Risks / Trade-offs

- **[Records in the repository grow]** One line per cell, module, or job: a few hundred lines a
  month at the current pace.
- **[Backfilled timing records lack fields]** The old runs' Java version and the like are only in
  their headers' prose; the parser copies what each header states and leaves the rest out.

## Open Questions

- None. It lands before the 0.2.0 tag, so the release's own CI run is recorded with the new
  script (the owner's choice, 2026-11-11).
