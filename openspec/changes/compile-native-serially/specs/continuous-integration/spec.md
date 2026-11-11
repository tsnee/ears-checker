## ADDED Requirements

### Requirement: Scala Native modules compiled one at a time
The CI workflow SHALL compile the Scala Native modules one at a time, each after the previous one's compile has finished.

#### Scenario: Compile step's log
- **WHEN** a CI run compiles from a fresh checkout
- **THEN** its log shows no two of `ears.native`, `ears.native.test`, `ears.markdown.native`, `ears.markdown.native.test`, `ears.cli.native`, and `ears.cli.native.test` compiling at the same time
