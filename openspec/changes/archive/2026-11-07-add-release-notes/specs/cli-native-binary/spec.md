## ADDED Requirements

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
