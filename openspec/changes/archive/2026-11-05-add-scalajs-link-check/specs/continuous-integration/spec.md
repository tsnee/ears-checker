## ADDED Requirements

### Requirement: Scala.js library link failures

If a Scala.js program whose only dependency is the Scala.js library, as the build publishes it, fails to link for release, then the CI workflow SHALL fail the run.

#### Scenario: JVM-only reference

- **WHEN** a pushed commit makes `Requirement.render` call `toUpperCase(java.util.Locale.ROOT)`
- **THEN** the run fails at the Scala.js library check, which reports the reference to `java.util.Locale`

### Requirement: Every public operation linked

When the CI workflow checks the Scala.js library, the CI workflow SHALL link a program that reaches every public operation of the library.

#### Scenario: New extension method

- **WHEN** a pushed commit adds a public extension method to `Requirement`, and nothing else
- **THEN** the Scala.js library check links that method's body too, with no other file changed

#### Scenario: Private member

- **WHEN** a pushed commit adds a method to a `private[ears]` object
- **THEN** the program the check links doesn't reference that method directly

### Requirement: Operations the check can't reach

If the library has a public operation that the Scala.js library check can't reference, then the CI workflow SHALL fail the run and name that operation.

#### Scenario: Overloaded operation

- **WHEN** a pushed commit adds a second public `classify` method to `Requirement`, overloading the first
- **THEN** the run fails at the Scala.js library check, naming `Requirement.classify` as an operation it can't reference
