# ears-cli Specification

## Purpose
A command-line tool that finds requirements in plain text, Markdown, or OpenSpec output, checks
each one with the EARS classifier, and reports findings for people, scripts, and hooks.

## Requirements

### Requirement: Plain-text input
When the EARS checker reads plain text, the EARS checker SHALL check each nonblank line as one requirement.

#### Scenario: Two lines
- **WHEN** the checker reads a file containing "The scheduler SHALL list upcoming events." and "The scheduler SHALL hide past events." on separate lines
- **THEN** it checks two requirements

### Requirement: Markdown input
When the EARS checker reads Markdown, the EARS checker SHALL check each sentence outside code that contains the modal keyword in any letter case.

#### Scenario: Requirement inside a list item
- **WHEN** the checker reads Markdown with a heading, a list item "The scheduler SHALL list upcoming events.", and a fenced code block containing "SHALL"
- **THEN** it checks one requirement

### Requirement: OpenSpec change input
When the EARS checker reads the JSON that OpenSpec prints for a change, the EARS checker SHALL check the text of every added and modified requirement.

#### Scenario: Change with added and removed requirements
- **WHEN** the checker reads change JSON with one ADDED requirement and one REMOVED requirement
- **THEN** it checks one requirement

### Requirement: OpenSpec spec input
When the EARS checker reads the JSON that OpenSpec prints for a spec, the EARS checker SHALL check the text of every requirement.

#### Scenario: Spec with two requirements
- **WHEN** the checker reads spec JSON with two requirements
- **THEN** it checks two requirements

### Requirement: Markdown inferred from file name
While no input format is selected, when the input file name ends in .md, the EARS checker SHALL read the input as Markdown.

#### Scenario: Markdown file name
- **WHEN** the checker is given the file "requirements.md" with no format option
- **THEN** it reads the file as Markdown

### Requirement: OpenSpec JSON inferred from file name
While no input format is selected, when the input file name ends in .json, the EARS checker SHALL read the input as OpenSpec JSON.

#### Scenario: JSON file name
- **WHEN** the checker is given the file "change.json" with no format option
- **THEN** it reads the file as OpenSpec JSON

### Requirement: Plain text by default
While no input format is selected, when the input has no .md or .json file name, the EARS checker SHALL read the input as plain text.

#### Scenario: Standard input without a format
- **WHEN** text is piped to the checker with no file argument and no format option
- **THEN** it reads the input as plain text

### Requirement: Standard input
When no input file is given, the EARS checker SHALL read standard input.

#### Scenario: Piped input
- **WHEN** "The scheduler SHALL list upcoming events." is piped to the checker with no file argument
- **THEN** it checks one requirement

### Requirement: Default profile for OpenSpec input
While no profile is selected, when the EARS checker reads OpenSpec JSON, the EARS checker SHALL use the OpenSpec profile.

#### Scenario: Uppercase keyword passes by default
- **WHEN** the checker reads OpenSpec JSON whose only requirement is "The scheduler SHALL list upcoming events." with no profile option
- **THEN** it reports no findings

### Requirement: Default profile for other input
While no profile is selected, when the EARS checker reads plain text or Markdown, the EARS checker SHALL use the classic profile.

#### Scenario: Lowercase keyword passes by default
- **WHEN** the checker reads plain text "The scheduler shall list upcoming events." with no profile option
- **THEN** it reports no findings

### Requirement: Selected profile
Where a profile is selected, the EARS checker SHALL classify every requirement under that profile.

#### Scenario: OpenSpec profile on plain text
- **WHEN** the checker reads plain text "The scheduler shall list upcoming events." with the OpenSpec profile selected
- **THEN** it reports a wrong-case finding

### Requirement: Finding contents
The EARS checker SHALL report each finding with the requirement's location, the diagnostic, and the suggested fix when one exists.

#### Scenario: Finding in an OpenSpec change
- **WHEN** the checker reads change JSON for change "add-reminders" whose requirement "Reminder sent" in capability "reminders" lacks the modal keyword
- **THEN** the finding names "add-reminders", "reminders", and "Reminder sent" along with a missing-modal diagnostic

### Requirement: Text output
Where text output is selected, the EARS checker SHALL write one human-readable line per finding to standard output.

#### Scenario: One finding as text
- **WHEN** the checker finds one problem with text output selected
- **THEN** it writes exactly one line to standard output

### Requirement: Default output
While no output format is selected, the EARS checker SHALL use text output.

#### Scenario: No output option
- **WHEN** the checker finds one problem with no output option
- **THEN** it writes one human-readable line to standard output

### Requirement: JSON output
Where JSON output is selected, the EARS checker SHALL write all findings as one JSON document to standard output.

#### Scenario: No findings as JSON
- **WHEN** the checker finds no problems with JSON output selected
- **THEN** it writes one JSON document with an empty list of findings

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
