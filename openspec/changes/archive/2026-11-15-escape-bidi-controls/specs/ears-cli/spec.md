## ADDED Requirements

### Requirement: Control characters in log lines

The EARS checker SHALL write each ISO control character, U+2028, U+2029, explicit bidirectional formatting character (U+202A to U+202E and U+2066 to U+2069), and lone surrogate in a log entry, other than a line feed or carriage return, as a backslash, `u`, and its code in four lowercase hexadecimal digits.

#### Scenario: A file name with a right-to-left override in a log entry

- **WHEN** a user checks a missing file whose name contains the right-to-left override (U+202E)
- **THEN** the error log entry shows `\u202e` in the file name, the same on the JVM, on Node, and natively

#### Scenario: An escape sequence in a log entry

- **WHEN** a log entry's message or detail contains the escape character (U+001B)
- **THEN** the entry shows `\u001b` there

## MODIFIED Requirements

### Requirement: Control characters in text output

Where text output is selected, the EARS checker SHALL write each ISO control character, U+2028, U+2029, explicit bidirectional formatting character (U+202A to U+202E and U+2066 to U+2069), and lone surrogate that a finding takes from the input, other than a run of spaces, tabs, line feeds, vertical tabs, form feeds, and carriage returns shown as one space, as a backslash, `u`, and its code in four lowercase hexadecimal digits.

#### Scenario: An escape sequence in a requirement

- **WHEN** a plain-text requirement with a missing comma contains the escape character (U+001B) followed by `[2K`
- **THEN** its finding's `Fix:` sentence shows `\u001b[2K` and standard output contains no U+001B

#### Scenario: A line separator in a requirement name

- **WHEN** an OpenSpec requirement's name contains U+2028 and its requirement has a finding
- **THEN** the finding is one line, with the name showing `\u2028`

#### Scenario: A vertical tab in a requirement

- **WHEN** a plain-text requirement with a missing comma contains a vertical tab between two words
- **THEN** its finding's `Fix:` sentence shows one space there, on the JVM, on Node, and natively

#### Scenario: A lone surrogate in a fix

- **WHEN** an OpenSpec requirement written in classic case contains the lone surrogate `\ud83d`, checked with the OpenSpec profile
- **THEN** its finding's `Fix:` sentence shows `\ud83d`, the same bytes on the JVM, on Node, and natively

#### Scenario: A right-to-left override in a fix

- **WHEN** a plain-text requirement with a missing comma contains the right-to-left override (U+202E)
- **THEN** its finding's `Fix:` sentence shows `\u202e`, the same bytes on the JVM, on Node, and natively

### Requirement: Control characters in messages

The EARS checker SHALL write each ISO control character, U+2028, U+2029, explicit bidirectional formatting character (U+202A to U+202E and U+2066 to U+2069), and lone surrogate in a message on standard error that isn't a log entry, other than the line breaks of its own usage text, as a backslash, `u`, and its code in four lowercase hexadecimal digits.

#### Scenario: A file name with an escape sequence

- **WHEN** a user checks a missing file whose name contains U+001B
- **THEN** the `Cannot read` message shows `\u001b` in the name and standard error contains no U+001B

#### Scenario: A file name with a line feed

- **WHEN** a user checks a missing file whose name contains a line feed
- **THEN** the `Cannot read` message is one line, showing `\u000a` in the name

#### Scenario: A file name with a right-to-left override

- **WHEN** a user checks a missing file whose name contains the right-to-left override (U+202E)
- **THEN** the `Cannot read` message shows `\u202e` in the name, and standard error contains no U+202E

### Requirement: Control characters in JSON output

Where JSON output is selected, the EARS checker SHALL write each ISO control character, U+2028, U+2029, explicit bidirectional formatting character (U+202A to U+202E and U+2066 to U+2069), and lone surrogate in the document's strings as a JSON escape.

#### Scenario: A C1 control in a requirement

- **WHEN** an OpenSpec requirement with a finding contains U+009B, with JSON output selected
- **THEN** the finding's `requirement` shows `\u009b`, and standard output contains no U+009B

#### Scenario: A line separator in a requirement

- **WHEN** an OpenSpec requirement with a finding contains U+2028, with JSON output selected
- **THEN** the finding's `requirement` shows `\u2028`, and the document is one line

#### Scenario: A lone surrogate in a requirement

- **WHEN** an OpenSpec requirement with a finding contains the lone surrogate `\ud83d`, with JSON output selected
- **THEN** the finding's `requirement` shows `\ud83d`, the same bytes on the JVM, on Node, and natively

#### Scenario: A right-to-left isolate in a requirement

- **WHEN** a plain-text requirement with a finding contains the right-to-left isolate (U+2067), with JSON output selected
- **THEN** the finding's `requirement` shows `\u2067`, and standard output contains no U+2067
