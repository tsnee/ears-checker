## RENAMED Requirements

- FROM: `### Requirement: Control characters in text output`
- TO: `### Requirement: Escaped characters in text output`

- FROM: `### Requirement: Control characters in messages`
- TO: `### Requirement: Escaped characters in messages`

- FROM: `### Requirement: Control characters in JSON output`
- TO: `### Requirement: Escaped characters in JSON output`

- FROM: `### Requirement: Control characters in log lines`
- TO: `### Requirement: Escaped characters in log lines`

## MODIFIED Requirements

### Requirement: Escaped characters in text output

Where text output is selected, the EARS checker SHALL write each UTF-16 code unit that a finding takes from the input and that is outside the English character set (printable ASCII, U+00A0 to U+00FF other than U+00AD, U+0300 to U+036F, U+2010 to U+2022, U+2026, U+2030, U+2032, U+2033, U+2039, U+203A, U+20AC, and U+2122) or is a number sign followed by another, other than in a run of spaces, tabs, line feeds, vertical tabs, form feeds, and carriage returns in a corrected sentence, shown as one space, as a backslash, `u`, and its four lowercase hexadecimal digits.

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

#### Scenario: Accented letters and typographic punctuation in a fix

- **WHEN** a plain-text requirement with a missing comma contains `café`, curly double quotes (U+201C, U+201D), and an em dash (U+2014)
- **THEN** its finding's `Fix:` sentence shows them as they were written

#### Scenario: An emoji in a fix

- **WHEN** a plain-text requirement with a missing comma contains the emoji U+1F600
- **THEN** its finding's `Fix:` sentence shows `\ud83d\ude00`, the same bytes on the JVM, on Node, and natively

#### Scenario: A zero-width space in a fix

- **WHEN** a plain-text requirement with a missing comma contains the zero-width space (U+200B) inside a word
- **THEN** its finding's `Fix:` sentence shows `\u200b` there

#### Scenario: A Cyrillic letter in a fix

- **WHEN** a plain-text requirement with a missing comma contains the Cyrillic small letter a (U+0430) in place of a Latin `a`
- **THEN** its finding's `Fix:` sentence shows `\u0430` there

#### Scenario: A decomposed accent in a file name

- **WHEN** a file whose name is `cafe`, a combining acute accent (U+0301), and `.txt` has a requirement with a finding, with text output selected
- **THEN** the finding's line shows the name as it was written

#### Scenario: A tab in a file name

- **WHEN** a file whose name is `notes`, a tab, and `.txt` has a requirement with a finding, with text output selected
- **THEN** the finding's line starts with `notes\u0009.txt:1:`

#### Scenario: A workflow command in a fix

- **WHEN** a plain-text requirement with a missing comma contains `##[error]`
- **THEN** its finding's `Fix:` sentence shows `\u0023#[error]`, and standard output contains no `##`

### Requirement: Escaped characters in messages

The EARS checker SHALL write each UTF-16 code unit in a message on standard error that isn't a log entry and that is outside the English character set (printable ASCII, U+00A0 to U+00FF other than U+00AD, U+0300 to U+036F, U+2010 to U+2022, U+2026, U+2030, U+2032, U+2033, U+2039, U+203A, U+20AC, and U+2122) or is a number sign followed by another, other than the line breaks of its own usage text, as a backslash, `u`, and its four lowercase hexadecimal digits.

#### Scenario: A file name with an escape sequence

- **WHEN** a user checks a missing file whose name contains U+001B
- **THEN** the `Cannot read` message shows `\u001b` in the name and standard error contains no U+001B

#### Scenario: A file name with a line feed

- **WHEN** a user checks a missing file whose name contains a line feed
- **THEN** the `Cannot read` message is one line, showing `\u000a` in the name

#### Scenario: A file name with a right-to-left override

- **WHEN** a user checks a missing file whose name contains the right-to-left override (U+202E)
- **THEN** the `Cannot read` message shows `\u202e` in the name, and standard error contains no U+202E

#### Scenario: A file name with a zero-width space

- **WHEN** a user checks a missing file whose name contains the zero-width space (U+200B)
- **THEN** the `Cannot read` message shows `\u200b` in the name

### Requirement: Escaped characters in JSON output

Where JSON output is selected, the EARS checker SHALL write each UTF-16 code unit in the document's strings that is outside the English character set (printable ASCII, U+00A0 to U+00FF other than U+00AD, U+0300 to U+036F, U+2010 to U+2022, U+2026, U+2030, U+2032, U+2033, U+2039, U+203A, U+20AC, and U+2122) or is a number sign followed by another as a JSON escape.

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

#### Scenario: An emoji in a requirement

- **WHEN** a plain-text requirement with a finding contains the emoji U+1F600, with JSON output selected
- **THEN** the finding's `requirement` shows `\ud83d\ude00`, the same bytes on the JVM, on Node, and natively

#### Scenario: A workflow command in a requirement

- **WHEN** a plain-text requirement with a finding contains `##[set-output name=ok]true`, with JSON output selected
- **THEN** the finding's `requirement` shows `\u0023#[set-output name=ok]true`, and standard output contains no `##`

### Requirement: Escaped characters in log lines

The EARS checker SHALL write each UTF-16 code unit in a log entry that is outside the English character set (printable ASCII, U+00A0 to U+00FF other than U+00AD, U+0300 to U+036F, U+2010 to U+2022, U+2026, U+2030, U+2032, U+2033, U+2039, U+203A, U+20AC, and U+2122) or is a number sign followed by another, other than a line feed or carriage return, as a backslash, `u`, and its four lowercase hexadecimal digits.

#### Scenario: A file name with a right-to-left override in a log entry

- **WHEN** a user checks a missing file whose name contains the right-to-left override (U+202E)
- **THEN** the error log entry shows `\u202e` in the file name, the same on the JVM, on Node, and natively

#### Scenario: An escape sequence in a log entry

- **WHEN** a log entry's message or detail contains the escape character (U+001B)
- **THEN** the entry shows `\u001b` there

#### Scenario: A zero-width space in a log entry

- **WHEN** a log entry's message or detail contains the zero-width space (U+200B)
- **THEN** the entry shows `\u200b` there

#### Scenario: A workflow command in a log entry

- **WHEN** a user checks a missing file whose name is `##[add-mask]notes.txt`
- **THEN** the error log entry shows `\u0023#[add-mask]notes.txt`, and standard error contains no `##`

## ADDED Requirements

### Requirement: Line starts in text output

Where text output is selected, the EARS checker SHALL write the first character of the line of each finding and warning as a backslash, `u`, and its four lowercase hexadecimal digits when that character is a space, a no-break space, or a colon.

#### Scenario: A spec id that is a workflow command

- **WHEN** an OpenSpec spec whose id is `::error::forged` has a requirement with a finding, with text output selected
- **THEN** the finding's line starts with `\u003a:error::forged/`

#### Scenario: A file name that starts with a no-break space

- **WHEN** a file whose name is `notes.txt` after a no-break space (U+00A0) has a requirement with a finding, with text output selected
- **THEN** the finding's line starts with `\u00a0notes.txt:1:`

#### Scenario: A file name that starts with a space

- **WHEN** a file named ` notes.txt` (with a leading space) has a requirement with a finding, with text output selected
- **THEN** the finding's line starts with `\u0020notes.txt:1:`
