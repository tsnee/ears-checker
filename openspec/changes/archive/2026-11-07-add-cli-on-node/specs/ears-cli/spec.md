## ADDED Requirements

### Requirement: Same results on Node

The EARS checker SHALL write the same standard output and exit with the same status on Node as on the JVM, for the same arguments and input.

#### Scenario: A failing Markdown file

- **WHEN** the checker runs as `ears-check --output json notes.md` on Node and on the JVM, and `notes.md` has a requirement with the wrong modal case
- **THEN** both write the same JSON to standard output and both exit with status 1

#### Scenario: Piped input

- **WHEN** `The scheduler shall list upcoming events.` is piped into `ears-check` on Node and on the JVM
- **THEN** neither writes to standard output and both exit with status 0

### Requirement: Same messages on Node

The EARS checker SHALL write the same messages to standard error on Node as on the JVM, apart from log entries.

#### Scenario: A missing file

- **WHEN** the checker runs as `ears-check missing.txt` on Node and on the JVM
- **THEN** both write `Cannot read missing.txt` to standard error and both exit with status 2

#### Scenario: A directory

- **WHEN** the checker runs as `ears-check notes` on Node and on the JVM, and `notes` is a directory
- **THEN** both write `Cannot read notes` to standard error and both exit with status 2

### Requirement: Arguments on Node

When the checker runs on Node, the EARS checker SHALL read its arguments from the command line it was started with.

#### Scenario: Options and files

- **WHEN** a user runs `ears-check --profile openspec --output json notes.md` on Node
- **THEN** the checker reads `notes.md` with the OpenSpec profile and writes JSON

### Requirement: Input that isn't UTF-8

If an input is not valid UTF-8, then the EARS checker SHALL write the line and column of its first invalid byte to standard error and exit with status 2.

#### Scenario: A Latin-1 file

- **WHEN** the checker is given "notes.txt", whose second line starts with `l` followed by the Latin-1 byte for `é`
- **THEN** it writes `Cannot read notes.txt: not valid UTF-8 at line 2, column 2` to standard error and exits with status 2

#### Scenario: Standard input in UTF-16

- **WHEN** text in UTF-16 with a byte-order mark is piped into the checker
- **THEN** it writes `Cannot read standard input: not valid UTF-8 at line 1, column 1` to standard error and exits with status 2

### Requirement: Empty file name

If a file argument is empty, then the EARS checker SHALL reject the arguments with the usage text and exit status 2 before reading any input.

#### Scenario: An empty argument after a file

- **WHEN** a user runs `ears-check notes.md ""`
- **THEN** the checker reads neither, writes `A file name can't be empty.` and the usage text to standard error, and exits with status 2

### Requirement: UTF-8 output

The EARS checker SHALL write standard output and standard error as UTF-8, whatever the locale.

#### Scenario: The C locale

- **WHEN** the checker runs under `LC_ALL=C` and reports a fix that contains `café`
- **THEN** standard output holds `é` as the bytes C3 A9

## MODIFIED Requirements

### Requirement: Unreadable input message

If an input cannot be read or parsed in its format, then the EARS checker SHALL write a message naming the input, and a log entry giving the cause, to standard error.

#### Scenario: Missing file

- **WHEN** the checker is given the file "missing.txt" that does not exist
- **THEN** it writes `Cannot read missing.txt`, and an error log entry with the platform's description of the error, to standard error

#### Scenario: Unreadable standard input

- **WHEN** the checker is given no file and reading standard input fails with the error "Stream closed"
- **THEN** it writes `Cannot read standard input`, and an error log entry containing "Stream closed", to standard error

#### Scenario: Directory given as a file

- **WHEN** the checker is given "notes", which is a directory
- **THEN** it writes `Cannot read notes`, and an error log entry with the platform's description of the error, to standard error

#### Scenario: File the user may not read

- **WHEN** the checker is given "secret.txt", which the user has no permission to read
- **THEN** it writes `Cannot read secret.txt`, and an error log entry with the platform's description of the error, to standard error
