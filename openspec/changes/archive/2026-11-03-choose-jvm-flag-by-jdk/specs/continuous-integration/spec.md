# Spec Delta

## ADDED Requirements

### Requirement: OpenSpec check on JDK 17

The CI workflow SHALL run the OpenSpec check with JDK 17 as the Java runtime, as well as with the JDK the build uses.

#### Scenario: JDK-23-only flag

- **WHEN** a pushed commit makes `scripts/check-openspec` always start the EARS checker with `--sun-misc-unsafe-memory-access=allow`
- **THEN** the OpenSpec check fails on JDK 17 and the run fails

#### Scenario: Clean commit on both JDKs

- **WHEN** a pushed commit passes the OpenSpec check on JDK 17 and on JDK 27
- **THEN** the run succeeds
