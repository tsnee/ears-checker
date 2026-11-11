## ADDED Requirements

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
