# Spec Delta

## ADDED Requirements

### Requirement: Several input files

When the EARS checker is given several input files, the EARS checker SHALL check the requirements in every file in one run.

#### Scenario: Two plain-text files

- **WHEN** the checker is given two plain-text files that each contain one requirement
- **THEN** it checks two requirements

### Requirement: Format inferred per file

While no input format is selected, when the EARS checker is given several input files, the EARS checker SHALL infer each file's format from that file's name.

#### Scenario: Markdown and JSON together

- **WHEN** the checker is given "notes.md" and "change.json" with no format option
- **THEN** it reads "notes.md" as Markdown and "change.json" as OpenSpec JSON

### Requirement: Findings in file order

When the EARS checker is given several input files, the EARS checker SHALL report findings in the order in which the files were given.

#### Scenario: Files given out of alphabetical order

- **WHEN** the checker is given "b.txt" and then "a.txt", each containing one requirement that lacks the modal keyword
- **THEN** it reports the finding for "b.txt" before the finding for "a.txt"

### Requirement: Unreadable file stops the run

If an input file cannot be read or parsed, then the EARS checker SHALL stop without reading any later input file.

#### Scenario: Two missing files

- **WHEN** the checker is given "missing.txt" and then "absent.txt", neither of which exists
- **THEN** it writes a message naming "missing.txt" to standard error, and nothing naming "absent.txt"

### Requirement: No findings after an unreadable file

If an input file cannot be read or parsed, then the EARS checker SHALL report no findings.

#### Scenario: Failing file before a missing one

- **WHEN** the checker is given "notes.txt", which contains "The scheduler lists upcoming events.", and then "missing.txt", which does not exist
- **THEN** it writes nothing to standard output

## MODIFIED Requirements

### Requirement: Passing exit status

While every input can be read and parsed, when every checked requirement passes, the EARS checker SHALL exit with status 0.

#### Scenario: All requirements pass

- **WHEN** the checker reads plain text "The scheduler shall list upcoming events."
- **THEN** it exits with status 0

### Requirement: Failing exit status

While every input can be read and parsed, when at least one checked requirement fails, the EARS checker SHALL exit with status 1.

#### Scenario: One requirement fails

- **WHEN** the checker reads plain text "The scheduler lists upcoming events."
- **THEN** it exits with status 1

### Requirement: Unreadable input status

If any input cannot be read or parsed in its format, then the EARS checker SHALL exit with status 2.

#### Scenario: Malformed JSON

- **WHEN** the checker reads "{not json" as OpenSpec JSON
- **THEN** it exits with status 2

#### Scenario: Missing file beside a failing file

- **WHEN** the checker is given "missing.txt", which does not exist, and "notes.txt", which contains "The scheduler lists upcoming events."
- **THEN** it exits with status 2

### Requirement: Unreadable input message

If an input cannot be read or parsed in its format, then the EARS checker SHALL write the cause to standard error.

#### Scenario: Missing file

- **WHEN** the checker is given the file "missing.txt" that does not exist
- **THEN** it writes a message naming "missing.txt" to standard error
