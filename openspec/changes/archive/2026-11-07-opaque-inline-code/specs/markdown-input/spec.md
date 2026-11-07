## ADDED Requirements

### Requirement: Inline code in sentences
The Markdown reader SHALL return each inline code span in a sentence as a code span, between backticks.

#### Scenario: A quoted file name
- **WHEN** the reader reads the paragraph ``The importer SHALL skip `notes.md` files.``
- **THEN** it returns ``The importer SHALL skip `notes.md` files.``

#### Scenario: Escaped backticks
- **WHEN** the reader reads the paragraph ``The shell SHALL print \`now\` on start.``
- **THEN** it returns ``The shell SHALL print \`now\` on start.``, whose backticks quote nothing

#### Scenario: A backslash before code
- **WHEN** the reader reads the paragraph ``The exporter SHALL write to C:\\`out. Then` daily.``, an escaped backslash and then code
- **THEN** it returns ``The exporter SHALL write to C:\\`out. Then` daily.``, the backslash doubled so the code stays code, and other backslashes single

#### Scenario: Code that holds a backtick
- **WHEN** the reader reads the paragraph ```The formatter SHALL escape ``a ` b`` in output.```
- **THEN** it returns a sentence that quotes ``a ` b`` between double backticks

## MODIFIED Requirements

### Requirement: Sentence boundaries
The Markdown reader SHALL end a sentence at a period outside inline code that is followed by whitespace, with each line break inside a paragraph read as a space.

#### Scenario: Two sentences across a soft line break
- **WHEN** the reader reads the paragraph `Reminders are useful. When a reminder is due,` followed on the next line by `the scheduler SHALL send a notice.`
- **THEN** it returns `When a reminder is due, the scheduler SHALL send a notice.`

#### Scenario: A period inside inline code
- **WHEN** the reader reads the paragraph ``The checker SHALL write `Cannot read notes.txt. Check the path.` on failure.``
- **THEN** it returns the paragraph as one sentence
