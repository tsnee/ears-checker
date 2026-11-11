# openspec-check Specification

## Purpose

The repository's single gate for OpenSpec content: strict OpenSpec validation plus EARS checking of every requirement, run automatically after Claude Code edits OpenSpec files.

## Requirements

### Requirement: Strict validation

When the OpenSpec check runs, the OpenSpec check SHALL validate every change and every spec in strict mode, so that warnings fail.

#### Scenario: Requirement description over the length limit

- **WHEN** the check runs against a change with a requirement description of 600 characters
- **THEN** the check fails

### Requirement: Archived tasks

When the OpenSpec check runs, the OpenSpec check SHALL verify that every archived change has all tasks completed.

#### Scenario: Archived change with an open task

- **WHEN** the check runs against an archived change whose tasks file has one unchecked task
- **THEN** the check fails

### Requirement: EARS checking

When the OpenSpec check runs, the OpenSpec check SHALL check every requirement in every active change and every spec under the OpenSpec EARS profile.

#### Scenario: Requirement that is not EARS

- **WHEN** the check runs against a change whose only requirement is "The scheduler MUST list upcoming events."
- **THEN** the check fails with a weak-modal finding for that requirement

### Requirement: Failing status

If strict validation or EARS checking fails, then the OpenSpec check SHALL exit with a nonzero status.

#### Scenario: EARS failure only

- **WHEN** strict validation passes and one requirement fails EARS checking
- **THEN** the check exits with a nonzero status

### Requirement: Report to Claude Code

When Claude Code edits a file under openspec/ and the OpenSpec check fails, the OpenSpec check SHALL return its report to Claude Code as a blocking hook error.

#### Scenario: Edit introduces a weak modal verb

- **WHEN** Claude Code edits a spec so that a requirement reads "The scheduler MUST list upcoming events."
- **THEN** the hook exits with status 2 and its message includes the weak-modal finding

### Requirement: Single EARS checker run

When the OpenSpec check runs, the OpenSpec check SHALL start the EARS checker once for all active changes and specs.

#### Scenario: Two changes and four specs

- **WHEN** the check runs with two active changes and four specs
- **THEN** it starts the EARS checker once, and the report covers requirements from all six

### Requirement: JDK 17 through 22

While the Java runtime on the PATH is JDK 17 through 22, the OpenSpec check SHALL run the EARS checker.

#### Scenario: Check on JDK 17

- **WHEN** the check runs with Temurin 17 as the `java` on the PATH and every requirement is EARS
- **THEN** the EARS checker starts, the check reports that every requirement passes, and it exits with status 0

#### Scenario: Finding on JDK 17

- **WHEN** the check runs with Temurin 17 as the `java` on the PATH and an active change has a requirement that reads "The scheduler MUST list upcoming events."
- **THEN** the check reports the weak-modal finding and fails, as it does on newer JDKs

### Requirement: Unsafe warnings on newer JDKs

While the Java runtime on the PATH is JDK 23 or newer, the OpenSpec check SHALL keep `sun.misc.Unsafe` warnings out of its output.

#### Scenario: Check on JDK 27

- **WHEN** the check runs with JDK 27 as the `java` on the PATH
- **THEN** its output has no line that starts with `WARNING:` and mentions `sun.misc.Unsafe`
