# coursier-app Specification

## Purpose

Describes `ears-check` as a Coursier app in a channel file in this repository, so `cs install` sets up the command: the native binary where a release has one for the platform, and the JVM launcher elsewhere.

## Requirements

### Requirement: Channel file

The channel file `coursier/apps.json` SHALL describe exactly one app, named `ears-check`.

#### Scenario: Installing from the channel

- **WHEN** a user runs `cs install --channel https://raw.githubusercontent.com/tsnee/ears-checker/main/coursier/apps.json ears-check` and then `ears-check --help`
- **THEN** the usage text for `ears-check` is printed and the exit status is 0

### Requirement: Latest release

The `ears-check` app SHALL install the latest release of `io.github.tsnee:ears-checker-cli_3` on Maven Central.

#### Scenario: Updating after a release

- **WHEN** a user who installed the app at 0.2.0 runs `cs update ears-check` after 0.2.1 is released on Maven Central
- **THEN** `ears-check --output json` reports `"checker":"0.2.1"`

### Requirement: JVM options

When the `ears-check` app's JVM launcher starts the JVM, the launcher SHALL pass `-XX:+IgnoreUnrecognizedVMOptions`, `--sun-misc-unsafe-memory-access=allow`, and `--enable-native-access=ALL-UNNAMED` to it.

#### Scenario: No warning on a new JDK

- **WHEN** a user runs the installed JVM launcher on JDK 27
- **THEN** standard error has no warning about `sun.misc.Unsafe`

#### Scenario: No warning from the launcher on Windows

- **WHEN** a user on Windows runs the installed JVM launcher on JDK 27
- **THEN** standard error has no warning about a restricted method

#### Scenario: Starting on JDK 17

- **WHEN** a user runs the installed JVM launcher on JDK 17
- **THEN** the JVM starts and checks the input

### Requirement: Native binary where a release has one

Where the platform is linux-x64, linux-arm64, or macos-arm64 and the GitHub Release of the version it installs has that platform's native binary, the `ears-check` app SHALL install that native binary.

#### Scenario: Installing on Apple silicon

- **WHEN** a user on macos-arm64 installs the app while release 0.2.0 is the latest
- **THEN** the installed command runs the file `ears-check-0.2.0-macos-arm64` attached to GitHub Release `v0.2.0`, byte for byte

### Requirement: JVM launcher otherwise

If the platform has no native binary in the release, or the release's native binary can't be downloaded, then the `ears-check` app SHALL install the JVM launcher.

#### Scenario: Installing on Windows

- **WHEN** a user on Windows on x64 installs the app
- **THEN** the installed command runs the CLI on the JVM
