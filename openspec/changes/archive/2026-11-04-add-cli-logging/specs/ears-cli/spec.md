# Spec Delta

## ADDED Requirements

### Requirement: Log destination

The EARS checker SHALL write every log entry to standard error.

#### Scenario: Verbose run with findings

- **WHEN** the checker is run with `--verbose` on "notes.txt", which contains "The scheduler lists upcoming events."
- **THEN** standard output holds only the finding, and every log entry is on standard error

### Requirement: Default log level

While `--verbose` is not given, the EARS checker SHALL omit log entries below the warning level.

#### Scenario: Passing input

- **WHEN** the checker is given "notes.txt", which contains "The scheduler shall list upcoming events."
- **THEN** it writes nothing to standard error

### Requirement: Verbose log level

When `--verbose` is given, the EARS checker SHALL write log entries at every level from debug up.

#### Scenario: Passing input with verbose

- **WHEN** the checker is run with `--verbose` on "notes.txt", which contains "The scheduler shall list upcoming events."
- **THEN** standard error holds info and debug entries, and standard output is empty

### Requirement: Input read entries

While `--verbose` is given, when an input has been read, the EARS checker SHALL log an entry with its name, format, profile, size in characters, and number of requirements.

#### Scenario: Markdown file

- **WHEN** the checker is run with `--verbose` on "notes.md", which lists two requirements
- **THEN** standard error has an entry naming "notes.md", the Markdown format, the classic profile, the file's size, and 2 requirements

### Requirement: Outcome entry

While `--verbose` is given, when the run ends, the EARS checker SHALL log an entry with the number of findings and the exit status.

#### Scenario: Failing input

- **WHEN** the checker is run with `--verbose` on a file with one requirement that lacks a modal
- **THEN** standard error has an entry with 1 finding and exit status 1

### Requirement: Unreadable input entry

If an input cannot be read or parsed in its format, then the EARS checker SHALL log an error entry with the input's name, format, and profile, the error's type and message, and the exit status.

#### Scenario: Directory given as a file

- **WHEN** the checker is given "notes", which is a directory, without `--verbose`
- **THEN** standard error has the existing message and an error entry naming "notes", the plain-text format, the classic profile, the error's type, the operating system's reason, and exit status 2

### Requirement: Invalid arguments entry

If the arguments are invalid, then the EARS checker SHALL log an error entry with the arguments and each reason they were rejected.

#### Scenario: Unknown format

- **WHEN** the checker is run with `--format yaml`
- **THEN** standard error has the usage text and an error entry with the arguments and the unknown format "yaml"

### Requirement: Log line format

The EARS checker SHALL write each log entry as one line with a timestamp, the level, the message, and the entry's details as `key=value` pairs.

#### Scenario: Error with a multi-line message

- **WHEN** an error entry's message or a detail spans several lines
- **THEN** the entry is still one line, with each line break written as `\n`
