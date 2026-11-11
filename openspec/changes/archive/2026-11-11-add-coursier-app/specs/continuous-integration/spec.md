## ADDED Requirements

### Requirement: Coursier app differences

If the command installed from the Coursier channel file, as the JVM launcher or as a native binary, writes different standard output or messages than the JVM CLI's jar on the same arguments and input, or exits with a different status, then the CI workflow SHALL fail the run.

#### Scenario: A dropped JVM option

- **WHEN** a pushed commit removes `--sun-misc-unsafe-memory-access=allow` from the app's JVM options
- **THEN** the run fails at the Coursier app check on JDK 27, naming the input whose messages differ

### Requirement: Coursier app on the oldest JDK

The CI workflow SHALL run the Coursier app check for the JVM launcher on JDK 17, as well as on JDK 27.

#### Scenario: JDK 17

- **WHEN** a CI run checks a pull request
- **THEN** the Coursier app check runs the JVM launcher once on JDK 17 and once on JDK 27

### Requirement: Coursier native install

If installing the Coursier app on linux-x64, linux-arm64, or macos-arm64, with that platform's native binary available at the app's download address, gives a command that doesn't run that exact binary, then the CI workflow SHALL fail the run.

#### Scenario: A JVM launcher instead of the binary

- **WHEN** a pushed commit changes the app's launcher type so that Coursier never uses a native binary
- **THEN** the run fails at the Coursier app check of each native recheck job, saying the installed command isn't the native binary

### Requirement: Coursier download addresses

If the Coursier app's download address for linux-x64, linux-arm64, or macos-arm64 doesn't name that platform's native binary in the GitHub Release of the version installed, then the CI workflow SHALL fail the run.

#### Scenario: Swapped platforms

- **WHEN** a pushed commit gives the app's linux-arm64 address the linux-x64 file name
- **THEN** the run fails at the Coursier app check, naming linux-arm64 and both file names

### Requirement: Coursier app without a native binary

The CI workflow SHALL check the Coursier app's JVM launcher on Intel macOS and on Windows on x64, which have no native binary in the release.

#### Scenario: Intel macOS and Windows

- **WHEN** a CI run checks a commit
- **THEN** the Coursier app check installs the JVM launcher on `macos-15-intel` and on `windows-latest` and compares it with the jar
