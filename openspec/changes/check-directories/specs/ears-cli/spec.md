## ADDED Requirements

### Requirement: Directory input

When the EARS checker is given a directory, the EARS checker SHALL check every file whose name ends in .md or .txt in the directory and in its subdirectories.

#### Scenario: Files in a subdirectory

- **WHEN** the checker is given "docs", which holds "scheduling.md" and a subdirectory "billing" that holds "invoices.md"
- **THEN** it checks the requirements in both files

#### Scenario: Markdown and plain text

- **WHEN** the checker is given "docs", which holds "scheduling.md" and "billing.txt", with no format option
- **THEN** it reads "scheduling.md" as Markdown and "billing.txt" as plain text

#### Scenario: Other files left alone

- **WHEN** the checker is given "docs", which holds "scheduling.md", "logo.png", and "package.json"
- **THEN** it reads "scheduling.md" and neither of the others

#### Scenario: A directory whose name starts with a period

- **WHEN** the checker is given ".notes", a directory that holds "scheduling.md"
- **THEN** it checks the requirements in "scheduling.md"

### Requirement: Hidden entries in a directory

When the EARS checker finds an entry whose name starts with a period in a directory, the EARS checker SHALL skip the entry.

#### Scenario: A hidden subdirectory

- **WHEN** the checker is given "docs", which holds "scheduling.md" and a subdirectory ".github" that holds "template.md"
- **THEN** it checks "scheduling.md" and doesn't read "template.md"

#### Scenario: A hidden file

- **WHEN** the checker is given "docs", which holds "scheduling.md" and ".draft.md"
- **THEN** it checks "scheduling.md" and doesn't read ".draft.md"

### Requirement: Links in a directory

When the EARS checker finds a symbolic link in a directory, the EARS checker SHALL skip the link.

#### Scenario: A link back to its own directory

- **WHEN** the checker is given "docs", which holds "scheduling.md" and "loop", a symbolic link to "docs"
- **THEN** it checks "scheduling.md" once and finishes

#### Scenario: A link to a file outside the directory

- **WHEN** the checker is given "docs", which holds "scheduling.md" and "keys.md", a symbolic link to a file outside "docs"
- **THEN** it checks "scheduling.md" and doesn't read the file that "keys.md" names

#### Scenario: A broken link

- **WHEN** the checker is given "docs", which holds "scheduling.md" and "old.md", a symbolic link to a file that doesn't exist
- **THEN** it checks "scheduling.md" and reports nothing about "old.md"

#### Scenario: A link given by name

- **WHEN** the checker is given "current", a symbolic link to a directory that holds "scheduling.md"
- **THEN** it checks the requirements in "scheduling.md", naming the file "current/scheduling.md"

### Requirement: Other entries in a directory

When the EARS checker finds an entry in a directory that is neither a file, a directory, nor a symbolic link, the EARS checker SHALL skip the entry.

#### Scenario: A named pipe

- **WHEN** the checker is given "docs", which holds "scheduling.md" and "feed.md", a named pipe
- **THEN** it checks "scheduling.md" and doesn't read "feed.md"

### Requirement: Files in a directory in name order

When the EARS checker checks the files in a directory, the EARS checker SHALL check them in order of their names, compared character by character by code, with the files in a subdirectory in the place of the subdirectory's name.

#### Scenario: Names that sort by code

- **WHEN** the checker is given "docs", which holds "b.md", "a.md", "B.md", and a subdirectory "b" that holds "c.md", each with one requirement that lacks the modal keyword
- **THEN** it reports the findings for "docs/B.md", "docs/a.md", "docs/b/c.md", and "docs/b.md", in that order

### Requirement: Directory among the inputs

When the EARS checker is given a directory among several inputs, the EARS checker SHALL check the files found in the directory in the directory's place among the inputs.

#### Scenario: A directory between two files

- **WHEN** the checker is given "z.md", then "docs", which holds "scheduling.md", then "a.md", each file with one requirement that lacks the modal keyword
- **THEN** it reports the findings for "z.md", "docs/scheduling.md", and "a.md", in that order

### Requirement: Names of files found in a directory

When the EARS checker reports on a file found in a directory, the EARS checker SHALL name the file by the directory as given, a slash, and the file's path below the directory.

#### Scenario: A file in a subdirectory

- **WHEN** the checker is given "docs", and "docs/billing/invoices.md" has a requirement that lacks the modal keyword on line 3
- **THEN** it reports the finding at "docs/billing/invoices.md:3"

#### Scenario: A directory given with a trailing slash

- **WHEN** the checker is given "docs/", and "docs/scheduling.md" has a requirement that lacks the modal keyword on line 1
- **THEN** it reports the finding at "docs/scheduling.md:1"

### Requirement: Directory with nothing to check status

If a directory that the EARS checker is given holds no file to check, then the EARS checker SHALL exit with status 2.

#### Scenario: An empty directory

- **WHEN** the checker is given "notes", an empty directory
- **THEN** it exits with status 2

#### Scenario: A directory of other files

- **WHEN** the checker is given "notes", which holds only "package.json" and a subdirectory ".github" that holds "template.md"
- **THEN** it exits with status 2

### Requirement: Directory with nothing to check message

If a directory that the EARS checker is given holds no file to check, then the EARS checker SHALL write a message naming the directory, and saying that it holds no .md or .txt files, to standard error.

#### Scenario: An empty directory

- **WHEN** the checker is given "notes", an empty directory
- **THEN** it writes `Cannot check notes: it holds no .md or .txt files` to standard error

### Requirement: Directory with nothing to check stops the run

If a directory that the EARS checker is given holds no file to check, then the EARS checker SHALL stop without reading any later input.

#### Scenario: An empty directory before a failing file

- **WHEN** the checker is given "notes", an empty directory, and then "later.md", which has a requirement that lacks the modal keyword
- **THEN** it writes nothing to standard output and nothing naming "later.md" to standard error

### Requirement: Unlistable directory stops the run

If the EARS checker cannot list a directory or subdirectory that it is to check, then the EARS checker SHALL stop without reading any later input.

#### Scenario: An unlistable subdirectory before a failing file

- **WHEN** the checker is given "docs", which holds a subdirectory "private" that the user may not list, and then "later.md", which has a requirement that lacks the modal keyword
- **THEN** it writes nothing to standard output and nothing naming "later.md" to standard error

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

- **WHEN** the checker is given "notes", a directory that the user may not list
- **THEN** it exits with status 2

#### Scenario: A file over the size limit

- **WHEN** the checker is given a file that holds more bytes than the input size limit
- **THEN** it exits with status 2

#### Scenario: An unlistable subdirectory

- **WHEN** the checker is given "docs", which holds "scheduling.md" and a subdirectory "private" that the user may not list
- **THEN** it exits with status 2

#### Scenario: A file found in a directory that can't be read

- **WHEN** the checker is given "docs", which holds "secret.md", a file that the user may not read
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

- **WHEN** the checker is given "notes", a directory that the user may not list
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

#### Scenario: An unlistable subdirectory

- **WHEN** the checker is given "docs", which holds a subdirectory "private" that the user may not list
- **THEN** it writes `Cannot read docs/private`, and an error log entry with the platform's description of the error, to standard error
