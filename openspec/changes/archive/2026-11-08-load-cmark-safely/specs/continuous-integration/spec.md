## ADDED Requirements

### Requirement: A library planted in the current directory

If the native binary, run on Linux or macOS with its library path variable naming the current directory, runs code from a cmark library planted in the current directory, then the CI workflow SHALL fail the run.

#### Scenario: A change that loads cmark by bare name again

- **WHEN** a pushed commit makes the Linux binary try `libcmark.so` by bare name whatever `LD_LIBRARY_PATH` holds
- **THEN** the run fails at the planted-library check on Linux, saying the planted library ran
