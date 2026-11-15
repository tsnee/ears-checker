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

#### Scenario: A file that isn't OpenSpec JSON

- **WHEN** the checker is given "notes.json", which contains `{not json`, as OpenSpec JSON
- **THEN** it writes `Cannot parse notes.json: not OpenSpec change or spec JSON: (expected '"' got 'n')`, and an error log entry giving the same cause, to standard error

#### Scenario: Standard input that isn't OpenSpec JSON

- **WHEN** the checker is given no file and reads `{not json` from standard input as OpenSpec JSON
- **THEN** it writes `Cannot parse standard input: not OpenSpec change or spec JSON: (expected '"' got 'n')`, and an error log entry giving the same cause, to standard error

#### Scenario: The second of two files isn't OpenSpec JSON

- **WHEN** the checker is given "change.json", which is OpenSpec change JSON, and "notes.json", which contains `{not json`
- **THEN** its message on standard error names `notes.json` and not `change.json`
