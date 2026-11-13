# ears-cli Specification

## Purpose

A command-line tool that finds requirements in plain text, Markdown, or OpenSpec output, checks each one with the EARS classifier, and reports findings for people, scripts, and hooks.

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

#### Scenario: Several inputs as JSON

- **WHEN** the checker is given "a.txt" and "b.txt" with JSON output selected, and each has one requirement
- **THEN** it writes one JSON document whose `checked` is 2, followed by one line break

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

### Requirement: UTF-8 byte-order mark

When an input starts with a UTF-8 byte-order mark, the EARS checker SHALL read the input as if the mark were absent.

#### Scenario: Plain text with a byte-order mark

- **WHEN** the checker is given "notes.txt", which starts with a UTF-8 byte-order mark and then `Scheduler shall list events.`
- **THEN** the missing-article fix it reports is `The scheduler shall list events.`, with no byte-order mark in it

#### Scenario: OpenSpec JSON with a byte-order mark

- **WHEN** the checker is given "spec.json", OpenSpec JSON that starts with a UTF-8 byte-order mark
- **THEN** it checks the requirements in it, as it would without the mark

### Requirement: Same results on Scala Native

The EARS checker SHALL write the same standard output and exit with the same status on Scala Native as on the JVM, for the same arguments and input.

#### Scenario: A failing Markdown file

- **WHEN** the checker runs as `ears-check --output json notes.md` natively and on the JVM, and `notes.md` has a requirement with the wrong modal case
- **THEN** both write the same JSON to standard output and both exit with status 1

### Requirement: Same messages on Scala Native

The EARS checker SHALL write the same messages to standard error on Scala Native as on the JVM, apart from log entries.

#### Scenario: A missing file

- **WHEN** the checker runs as `ears-check missing.txt` natively and on the JVM
- **THEN** both write `Cannot read missing.txt` to standard error and both exit with status 2

### Requirement: Missing cmark

If the native checker must read a Markdown input and finds no cmark library of release 0.30 or later, then the EARS checker SHALL write a message naming the cmark packages to install to standard error and exit with status 2.

#### Scenario: No cmark installed

- **WHEN** the native checker runs as `ears-check notes.md` on a system with no cmark library
- **THEN** it writes a message that names `brew install cmark` and `apt install cmark` to standard error and exits with status 2

#### Scenario: Plain text without cmark

- **WHEN** the native checker runs as `ears-check notes.txt` on a system with no cmark library
- **THEN** it checks `notes.txt` as it would with cmark installed

### Requirement: Libraries tried for Markdown

If the native checker finds no usable cmark library for a Markdown input, then the EARS checker SHALL list each library it tried, and why it couldn't use it, in that input's error log entry.

#### Scenario: No cmark installed, in the log

- **WHEN** the native checker runs as `ears-check notes.md` on Linux with no cmark library
- **THEN** its error log entry for `notes.md` has an `attempts` detail naming `libcmark.so` and the loader's error for it

### Requirement: JSON format version

Where JSON output is selected, the EARS checker SHALL write the format version 1 as the document's `version` field.

#### Scenario: Version of a document

- **WHEN** the checker reads plain text "The scheduler shall list upcoming events." with JSON output selected
- **THEN** the document starts with `{"version":1,`

### Requirement: JSON checker release

Where JSON output is selected, the EARS checker SHALL write the version of the release that wrote the document as its `checker` field.

#### Scenario: A release build

- **WHEN** the checker built for release 0.2.0 reads plain text "The scheduler shall list upcoming events." with JSON output selected
- **THEN** it writes `{"version":1,"checker":"0.2.0","checked":1,"findings":[]}`

#### Scenario: A snapshot build

- **WHEN** a checker built between releases, after 0.1.1, writes JSON output
- **THEN** its `checker` field is the next version as a snapshot, such as "0.2.0-SNAPSHOT"

### Requirement: JSON findings

Where JSON output is selected, the EARS checker SHALL write each finding as an object with its location, the requirement's text, the diagnostic's kind, its message, its span, and its fix.

#### Scenario: A finding on a line

- **WHEN** the checker reads the file "reqs.txt" whose line 2 is "The scheduler should list upcoming events." with JSON output selected
- **THEN** the finding has the location `{"type":"line","file":"reqs.txt","line":2}`, the requirement "The scheduler should list upcoming events.", the kind "weak-modal", the diagnostic's message, a span with `start` and `end`, and the fix "The scheduler shall list upcoming events."

### Requirement: JSON spans

Where JSON output is selected, the EARS checker SHALL give each finding's span as the half-open range of UTF-16 code units in the finding's requirement text that the diagnostic is about.

#### Scenario: Span of a weak modal

- **WHEN** the checker reads plain text "The scheduler should list upcoming events." with JSON output selected
- **THEN** the finding's span is `{"start":14,"end":20}`, the position of "should" in the requirement

#### Scenario: Span after a character outside the Basic Multilingual Plane

- **WHEN** the checker reads plain text "The 📅 scheduler should list upcoming events." with JSON output selected
- **THEN** the finding's span counts the calendar emoji as two code units, so its `start` is 17

### Requirement: JSON line locations

Where JSON output is selected, when a finding is in plain text, Markdown, or standard input, the EARS checker SHALL give its location as an object with the type "line", the file name, and the 1-based line the requirement starts on.

#### Scenario: Standard input location

- **WHEN** the checker reads "The scheduler lists upcoming events." from standard input with JSON output selected
- **THEN** the finding's location is `{"type":"line","file":"<stdin>","line":1}`

### Requirement: JSON change locations

Where JSON output is selected, when a finding is in a requirement of an OpenSpec change, the EARS checker SHALL give its location as an object with the type "change" and the change's, capability's, and requirement's names.

#### Scenario: Change requirement location

- **WHEN** the checker reads change JSON for change "add-reminders" whose requirement "Reminder sent" in capability "reminders" lacks the modal keyword, with JSON output selected
- **THEN** the finding's location is `{"type":"change","change":"add-reminders","capability":"reminders","requirement":"Reminder sent"}`

### Requirement: JSON spec locations

Where JSON output is selected, when a finding is in a requirement of an OpenSpec spec, the EARS checker SHALL give its location as an object with the type "spec" and the spec's and requirement's names.

#### Scenario: Spec requirement location

- **WHEN** the checker reads spec JSON for spec "reminders" whose requirement "Upcoming" lacks the modal keyword, with JSON output selected
- **THEN** the finding's location is `{"type":"spec","spec":"reminders","requirement":"Upcoming"}`

### Requirement: JSON finding without a fix

Where JSON output is selected, when a diagnostic has no fix, the EARS checker SHALL write the finding's fix as null.

#### Scenario: Missing modal keyword

- **WHEN** the checker reads plain text "The scheduler lists upcoming events." with JSON output selected
- **THEN** the finding's kind is "missing-modal" and its fix is `null`

### Requirement: Control characters in text output

Where text output is selected, the EARS checker SHALL write each ISO control character, U+2028, and U+2029 that a finding takes from the input, other than a run of spaces, tabs, line feeds, vertical tabs, form feeds, and carriage returns shown as one space, as a backslash, `u`, and its code in four lowercase hexadecimal digits.

#### Scenario: An escape sequence in a requirement

- **WHEN** a plain-text requirement with a missing comma contains the escape character (U+001B) followed by `[2K`
- **THEN** its finding's `Fix:` sentence shows `\u001b[2K` and standard output contains no U+001B

#### Scenario: A line separator in a requirement name

- **WHEN** an OpenSpec requirement's name contains U+2028 and its requirement has a finding
- **THEN** the finding is one line, with the name showing `\u2028`

#### Scenario: A vertical tab in a requirement

- **WHEN** a plain-text requirement with a missing comma contains a vertical tab between two words
- **THEN** its finding's `Fix:` sentence shows one space there, on the JVM, on Node, and natively

### Requirement: Control characters in messages

The EARS checker SHALL write each ISO control character, U+2028, and U+2029 in a message on standard error that isn't a log entry, other than the line breaks of its own usage text, as a backslash, `u`, and its code in four lowercase hexadecimal digits.

#### Scenario: A file name with an escape sequence

- **WHEN** a user checks a missing file whose name contains U+001B
- **THEN** the `Cannot read` message shows `\u001b` in the name and standard error contains no U+001B

#### Scenario: A file name with a line feed

- **WHEN** a user checks a missing file whose name contains a line feed
- **THEN** the `Cannot read` message is one line, showing `\u000a` in the name

### Requirement: Warnings in text output

Where text output is selected, the EARS checker SHALL write one line per warning to standard output, with the warning's location, the word `warning`, its kind, and its message.

#### Scenario: A table row with extra cells

- **WHEN** the checker reads `notes.md`, whose two-column table has the body row `| C | one | two | three |` on line 9, with text output
- **THEN** standard output has the line `notes.md:9: warning: table-extra-cells: This table row has 4 cells and its header 2, so GitHub doesn't show the last 2.`

### Requirement: Warnings in line order

Where text output is selected, the EARS checker SHALL write an input's findings and warnings in the order of their lines.

#### Scenario: A warning between two findings

- **WHEN** an input has findings on lines 3 and 12 and a warning on line 9
- **THEN** the warning's line comes between the two findings' lines

### Requirement: Warnings in JSON output

Where JSON output is selected, the EARS checker SHALL write each warning as an object with its location, kind, and message in the document's `warnings` array.

#### Scenario: A JSON warning

- **WHEN** the checker reads the same `notes.md` with JSON output
- **THEN** the document's `warnings` array holds `{"location":{"type":"line","file":"notes.md","line":9},"kind":"table-extra-cells","message":"This table row has 4 cells and its header 2, so GitHub doesn't show the last 2."}`

#### Scenario: No warnings

- **WHEN** the checker reads input with no warnings, with JSON output
- **THEN** the document's `warnings` field is an empty array

### Requirement: Exit status without warnings

The EARS checker SHALL set the exit status from the findings, unaffected by warnings.

#### Scenario: Only a warning

- **WHEN** the checker reads a Markdown file whose only problem is a table row with extra cells
- **THEN** it writes the warning and exits with status 0

### Requirement: Warnings logged

When the EARS checker reports a warning, the EARS checker SHALL log a warning entry with the input's name, the warning's line and kind, and its message.

#### Scenario: Logged warning

- **WHEN** the checker reports the warning for line 9 of `notes.md`
- **THEN** its log has a WARN entry naming `notes.md`, line 9, and `table-extra-cells`
