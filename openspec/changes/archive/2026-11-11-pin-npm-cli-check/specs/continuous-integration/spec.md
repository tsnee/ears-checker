## ADDED Requirements

### Requirement: Pinned Node CLI install

When the Node CLI check installs the packed CLI package, the check SHALL install each of its dependencies at the version and checksum the Markdown facade's lockfile records.

#### Scenario: A newer dependency on npm

- **WHEN** a newer `entities` 3.0.x is published to npm and the Node CLI check runs
- **THEN** it installs `entities` 3.0.1, the version the lockfile records

#### Scenario: A checksum that doesn't match

- **WHEN** a package the check installs doesn't match the checksum its lockfile records
- **THEN** the check fails before running the Node CLI, reporting the integrity error

### Requirement: No install scripts in the Node CLI install

When the Node CLI check installs the packed CLI package, the check SHALL install it with npm's install scripts disabled.

#### Scenario: A package with a postinstall script

- **WHEN** a package the check installs declares a `postinstall` script
- **THEN** the install finishes without running that script

### Requirement: Release files kept before dependencies run

When the Node CLI check keeps the tarball and jar for a release, the check SHALL copy them to its output directory before any code from the CLI's dependencies runs.

#### Scenario: Kept before the install

- **WHEN** the Node CLI check runs with an output directory
- **THEN** its log lists the kept tarball and jar, with their checksums, before the install

### Requirement: Kept files unchanged

If a file in the Node CLI check's output directory changes between the check keeping its files and the check ending, then the check SHALL fail.

#### Scenario: A kept file that changes

- **WHEN** the library tarball another check kept in the same output directory changes while the Node CLI check runs its cases
- **THEN** the check fails, naming the file and both checksums
