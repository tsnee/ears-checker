# cli-native-binary Specification

## Purpose

The native `ears-check` binary, built with Scala Native: a release build that needs no JVM or Node, links only the platform's system libraries, and finds cmark when it runs.

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

### Requirement: Oldest glibc

The Linux native binaries SHALL run with glibc 2.35 or later.

#### Scenario: Ubuntu 22.04

- **WHEN** a released linux-x64 or linux-arm64 binary runs on Ubuntu 22.04, which has glibc 2.35, with cmark's runtime package installed
- **THEN** it writes the same output and exits with the same status as the jar on every case of the native CLI check

### Requirement: Oldest macOS

The macOS native binary SHALL declare macOS 15.0 as the oldest release it runs on.

#### Scenario: The binary's minimum

- **WHEN** a developer reads the macos-arm64 binary's build version with `otool -l`
- **THEN** it shows `minos 15.0`, whichever macOS release built it
