## MODIFIED Requirements

### Requirement: Oldest glibc

The Linux native binaries SHALL run with glibc 2.35 or later.

#### Scenario: Ubuntu 22.04

- **WHEN** a released linux-x64 or linux-arm64 binary runs on Ubuntu 22.04, which has glibc 2.35, with no cmark package installed
- **THEN** it writes the same output and exits with the same status as the jar on every case of the native CLI check
