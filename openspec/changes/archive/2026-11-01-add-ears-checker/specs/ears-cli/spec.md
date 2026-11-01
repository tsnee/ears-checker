# Spec Delta

## Purpose

A command-line tool that finds requirements in plain text, Markdown, or OpenSpec output, checks
each one with the EARS classifier, and reports findings for people, scripts, and hooks.

## ADDED Requirements

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
When every checked requirement passes, the EARS checker SHALL exit with status 0.

#### Scenario: All requirements pass
- **WHEN** the checker reads plain text "The scheduler shall list upcoming events."
- **THEN** it exits with status 0

### Requirement: Failing exit status
When at least one checked requirement fails, the EARS checker SHALL exit with status 1.

#### Scenario: One requirement fails
- **WHEN** the checker reads plain text "The scheduler lists upcoming events."
- **THEN** it exits with status 1

### Requirement: Unreadable input status
If the input cannot be read or parsed in the selected format, then the EARS checker SHALL exit with status 2.

#### Scenario: Malformed JSON
- **WHEN** the checker reads "{not json" as OpenSpec JSON
- **THEN** it exits with status 2

### Requirement: Unreadable input message
If the input cannot be read or parsed in the selected format, then the EARS checker SHALL write the cause to standard error.

#### Scenario: Missing file
- **WHEN** the checker is given the file "missing.txt" that does not exist
- **THEN** it writes a message naming "missing.txt" to standard error
