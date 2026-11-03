# Spec Delta

## ADDED Requirements

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
