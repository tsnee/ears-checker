# cli-native-binary Specification

## Purpose
The native `ears-check` binary, built with Scala Native: a release build that needs no JVM or
Node, links only the platform's system libraries, and finds cmark when it runs.

## Requirements

### Requirement: System libraries only
The native binary SHALL link no library other than the platform's system libraries.

#### Scenario: Linked libraries on macOS
- **WHEN** a developer builds the native binary on macOS and lists its linked libraries with `otool -L`
- **THEN** it lists only libraries under `/usr/lib` and no cmark library

#### Scenario: Linked libraries on Linux
- **WHEN** a developer builds the native binary on Linux and lists its needed libraries with `ldd`
- **THEN** no cmark library is among them

### Requirement: Release build
The native binary SHALL be built with Scala Native's release optimizations.

#### Scenario: The CI build
- **WHEN** CI builds the native binary
- **THEN** it's linked in Scala Native's full release mode
