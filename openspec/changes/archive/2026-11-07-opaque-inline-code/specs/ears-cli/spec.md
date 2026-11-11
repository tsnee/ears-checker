## ADDED Requirements

### Requirement: UTF-8 byte-order mark

When an input starts with a UTF-8 byte-order mark, the EARS checker SHALL read the input as if the mark were absent.

#### Scenario: Plain text with a byte-order mark

- **WHEN** the checker is given "notes.txt", which starts with a UTF-8 byte-order mark and then `Scheduler shall list events.`
- **THEN** the missing-article fix it reports is `The scheduler shall list events.`, with no byte-order mark in it

#### Scenario: OpenSpec JSON with a byte-order mark

- **WHEN** the checker is given "spec.json", OpenSpec JSON that starts with a UTF-8 byte-order mark
- **THEN** it checks the requirements in it, as it would without the mark

## MODIFIED Requirements

### Requirement: Input that isn't UTF-8

If an input is not valid UTF-8, then the EARS checker SHALL write to standard error the encoding that the input's byte-order mark names, or else the line and column of its first invalid byte, and exit with status 2.

#### Scenario: A Latin-1 file

- **WHEN** the checker is given "notes.txt", whose second line starts with `l` followed by the Latin-1 byte for `é`
- **THEN** it writes `Cannot read notes.txt: not valid UTF-8 at line 2, column 2` to standard error and exits with status 2

#### Scenario: Standard input in UTF-16

- **WHEN** text in UTF-16 with a byte-order mark is piped into the checker
- **THEN** it writes `Cannot read standard input: UTF-16, not UTF-8` to standard error and exits with status 2

#### Scenario: A UTF-32 file

- **WHEN** the checker is given "notes.txt", which starts with a UTF-32 byte-order mark
- **THEN** it writes `Cannot read notes.txt: UTF-32, not UTF-8` to standard error and exits with status 2
