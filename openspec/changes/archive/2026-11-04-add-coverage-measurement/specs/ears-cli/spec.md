# Spec Delta

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

### Requirement: Unreadable input message
If an input cannot be read or parsed in its format, then the EARS checker SHALL write the cause to standard error.

#### Scenario: Missing file
- **WHEN** the checker is given the file "missing.txt" that does not exist
- **THEN** it writes a message naming "missing.txt" to standard error

#### Scenario: Unreadable standard input
- **WHEN** the checker is given no file and reading standard input fails with the error "Stream closed"
- **THEN** it writes a message naming standard input and "Stream closed" to standard error

#### Scenario: Directory given as a file
- **WHEN** the checker is given "notes", which is a directory
- **THEN** it writes a message naming "notes" and the operating system's reason to standard error
