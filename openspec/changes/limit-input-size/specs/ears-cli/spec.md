## ADDED Requirements

### Requirement: Input size limit

If an input holds more bytes than the input size limit, then the EARS checker SHALL read no more of it than the limit and one byte.

#### Scenario: A standard input that never ends

- **WHEN** the checker is given no file, with `--max-input-bytes 1000`, and standard input that keeps supplying bytes without ending
- **THEN** it exits with status 2 without waiting for standard input to end, on the JVM, on Node, and natively

#### Scenario: A file over the limit

- **WHEN** the checker is given "notes.txt", which holds 1001 bytes, with `--max-input-bytes 1000`
- **THEN** it exits with status 2 and writes no finding for it

#### Scenario: A file at the limit

- **WHEN** the checker is given "notes.txt", which holds 1000 bytes, with `--max-input-bytes 1000`
- **THEN** it checks the file as usual

### Requirement: Oversized input message

If an input holds more bytes than the input size limit, then the EARS checker SHALL write a message naming the input and the limit, and how to raise it, to standard error.

#### Scenario: A file over the limit

- **WHEN** the checker is given "notes.md", which holds more than 10,485,760 bytes, without `--max-input-bytes`
- **THEN** it writes `Cannot read notes.md: more than 10485760 bytes; --max-input-bytes raises the limit`, and an error log entry with the limit, to standard error

#### Scenario: Standard input over the limit

- **WHEN** the checker is given no file, with `--max-input-bytes 1000`, and standard input holds 1001 bytes
- **THEN** it writes `Cannot read standard input: more than 1000 bytes; --max-input-bytes raises the limit` to standard error

### Requirement: Default input size limit

While `--max-input-bytes` isn't given, the EARS checker SHALL use 10,485,760 bytes (10 MiB) as the input size limit.

#### Scenario: A file of 10 MiB

- **WHEN** the checker is given "notes.txt", which holds 10,485,760 bytes of requirements, without `--max-input-bytes`
- **THEN** it checks the file as usual

### Requirement: Input size limit option

Where `--max-input-bytes` is given with a whole number from 1 to 104,857,600, the EARS checker SHALL use that number of bytes as the input size limit for every input.

#### Scenario: A smaller limit

- **WHEN** the checker is given "a.txt" and "b.txt", which hold 500 and 2000 bytes, with `--max-input-bytes 1000`
- **THEN** it checks "a.txt" and stops at "b.txt" with status 2

#### Scenario: The largest limit

- **WHEN** the checker is given `--max-input-bytes 104857600` and a file of 20 MiB of requirements
- **THEN** it checks the file as usual

### Requirement: Invalid input size limit

If `--max-input-bytes` is given with anything other than a whole number from 1 to 104,857,600, then the EARS checker SHALL reject the arguments with the usage text and exit status 2 before reading any input.

#### Scenario: Zero

- **WHEN** the checker is run with `--max-input-bytes 0`
- **THEN** it writes the usage text with a reason naming `--max-input-bytes` to standard error and exits with status 2, reading no input

#### Scenario: Over the largest limit

- **WHEN** the checker is run with `--max-input-bytes 104857601`
- **THEN** it writes the usage text with a reason naming `--max-input-bytes` to standard error and exits with status 2

#### Scenario: Not a number

- **WHEN** the checker is run with `--max-input-bytes 10M`
- **THEN** it writes the usage text with a reason naming `--max-input-bytes` to standard error and exits with status 2

## MODIFIED Requirements

### Requirement: Unreadable input status

If any input cannot be read or parsed in its format, then the EARS checker SHALL exit with status 2.

#### Scenario: Malformed JSON

- **WHEN** the checker reads "{not json" as OpenSpec JSON
- **THEN** it exits with status 2

#### Scenario: Missing file beside a failing file

- **WHEN** the checker is given "missing.txt", which does not exist, and "notes.txt", which contains "The scheduler lists upcoming events."
- **THEN** it exits with status 2

#### Scenario: Unreadable standard input

- **WHEN** the checker is given no file and reading standard input fails
- **THEN** it exits with status 2

#### Scenario: Directory given as a file

- **WHEN** the checker is given "notes", which is a directory
- **THEN** it exits with status 2

#### Scenario: A file over the size limit

- **WHEN** the checker is given a file that holds more bytes than the input size limit
- **THEN** it exits with status 2
