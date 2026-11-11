## ADDED Requirements

### Requirement: Scala.js linking without extra libraries

The EARS classifier SHALL link for Scala.js with no libraries beyond its declared dependencies.

#### Scenario: Application with no other dependencies

- **WHEN** a Scala.js application whose only dependency is the classifier's Scala.js artifact calls `Requirement.classify` and is linked for release
- **THEN** linking succeeds, with no reference to a class such as `java.util.Locale` that only another library provides

### Requirement: Diagnostic kind names

The EARS classifier SHALL give each diagnostic kind a distinct name in kebab case.

#### Scenario: Wrong case

- **WHEN** a caller asks for the name of the wrong-case diagnostic kind
- **THEN** the name is `wrong-case`
