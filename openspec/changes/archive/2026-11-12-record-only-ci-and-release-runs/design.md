# Design

## Context

`scripts/ci-run-record` (POSIX sh) reads a run's summary with `gh run view <run> --json attempt,status,headSha,headBranch,event,workflowName`, then writes one record per job of each attempt it hasn't recorded. The summary already carries `workflowName`. Observed 2026-11-12: run 43140165200's `workflowName` is "Dependabot Updates", run 43085781522's is "Release", and CI runs' is "CI" (`.github/workflows/ci.yml`'s `name: CI`; `release.yml`'s `name: Release`). The records file holds only CI and Release runs today (`jq 'select(.workflow != "CI" and .workflow != "Release")'` finds none after the hand removal).

## Goals / Non-Goals

**Goals:** a run of any other workflow is refused before anything is written, with a message saying which workflow it was.

**Non-Goals:** a switch to record other workflows: nothing reports from them, and one can be added if a query ever needs them.

## Decisions

### 1. Check the workflow's name, right after the summary

Right after reading the summary, before the `status` check and before the records file or its directory is created, the script fails unless `workflowName` is "CI" or "Release": `ci-run-record: run <run> is a "<name>" run; only CI and Release runs are recorded`. A missing name reads as `null` and is refused. It's checked by name because the summary has it and both names are set in this repository's workflow files, each with a comment saying the script relies on it; a rename of either workflow would make the script refuse it, which the message makes plain. (Changed after review: the first version checked the workflow after the status, so an unfinished Dependabot run was told to wait, and created the records file and its directory first.)

_Alternative considered:_ the workflow's file path (`workflow_id`, through another `gh api` call): steadier across renames, at the cost of a second request per run. Names haven't changed since the workflows were written.

### 2. A repeatable test, and real runs

`scripts/test-ci-run-record` (developer-only, like the check scripts) puts a stand-in `gh` on the `PATH` that answers the script's three calls from each case's workflow name and status, and checks: a CI run and a Release run are recorded; a Dependabot run, a run with no workflow name, and an unfinished Dependabot run are refused with the file unchanged and a message naming the workflow; and a refused run with `--records` in a missing directory creates nothing. CI's `check` job runs it, and it's in the gate (`continuous-integration` delta). Nothing reaches GitHub. Besides it, the real runs of the spec's scenarios (the Dependabot run and the recorded Release run) are run once by hand on a scratch copy of the records file. (Added after review: the first version had only the hand-run checks, which a later edit moving the check below the write would have passed.)

## Risks / Trade-offs

- **A renamed workflow** is refused until the script's names change with it; the message says what was seen.
