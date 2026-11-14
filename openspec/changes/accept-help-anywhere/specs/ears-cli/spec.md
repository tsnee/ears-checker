## ADDED Requirements

### Requirement: Help text

When the options include `--help` and the arguments are otherwise valid and name no input file, the EARS checker SHALL write its usage text to standard output.

#### Scenario: Help alone

- **WHEN** the checker is run with `--help`
- **THEN** standard output has the usage text, and standard error has nothing

#### Scenario: Help after an option

- **WHEN** the checker is run with `--output json --help`
- **THEN** standard output has the usage text, and standard error has nothing

#### Scenario: Help before an option

- **WHEN** the checker is run with `--help --verbose`
- **THEN** standard output has the usage text

### Requirement: Help exit status

When the options include `--help` and the arguments are otherwise valid and name no input file, the EARS checker SHALL exit with status 0.

#### Scenario: Help after an option

- **WHEN** the checker is run with `--output json --help`
- **THEN** it exits with status 0

### Requirement: Help reads no input

When the options include `--help` and the arguments are otherwise valid and name no input file, the EARS checker SHALL read no input.

#### Scenario: Standard input beside help

- **WHEN** the checker is run with `--help`, and reading standard input would fail
- **THEN** standard error has nothing, and it exits with status 0

### Requirement: Help with input files

If the options include `--help` and the arguments name an input file, then the EARS checker SHALL reject the arguments with the usage text and exit status 2 before reading any input.

#### Scenario: Help after a file

- **WHEN** a user runs `ears-check notes.txt --help`
- **THEN** the checker reads nothing, writes `--help can't be given with input files: leave out --help, or name a file called --help after --.` and the usage text to standard error, and exits with status 2

#### Scenario: A glob that matches a file named --help

- **WHEN** a user runs `ears-check *` in a directory holding "--help" and "notes.txt", so that the shell passes `--help notes.txt`
- **THEN** the checker reads nothing and exits with status 2

### Requirement: Help after the options end

When `--help` follows `--` in the arguments, the EARS checker SHALL read it as a file name.

#### Scenario: A file named --help

- **WHEN** the checker is run with `-- --help`, and no file "--help" exists
- **THEN** it writes `Cannot read --help` to standard error and exits with status 2
