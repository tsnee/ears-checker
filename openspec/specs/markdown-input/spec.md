# markdown-input Specification

## Purpose
Reads requirement sentences from Markdown documents for the EARS checker, with the same
sentences and lines on every platform the checker builds for.

## Requirements

### Requirement: Sentences in paragraphs and list items
When the Markdown reader reads a document, the Markdown reader SHALL return each sentence of each paragraph, list items included, that contains the modal keyword outside inline code.

#### Scenario: List item
- **WHEN** the reader reads `# Reminders` followed by a list item `- The scheduler SHALL list upcoming events.` and a fenced code block containing `SHALL inside code`
- **THEN** it returns only `The scheduler SHALL list upcoming events.`

#### Scenario: Keyword only in inline code
- **WHEN** the reader reads the paragraph ``Write `SHALL` in uppercase.``
- **THEN** it returns no sentence

### Requirement: Sentence boundaries
The Markdown reader SHALL end a sentence at a period outside inline code that is followed by whitespace, with each line break inside a paragraph read as a space.

#### Scenario: Two sentences across a soft line break
- **WHEN** the reader reads the paragraph `Reminders are useful. When a reminder is due,` followed on the next line by `the scheduler SHALL send a notice.`
- **THEN** it returns `When a reminder is due, the scheduler SHALL send a notice.`

#### Scenario: A period inside inline code
- **WHEN** the reader reads the paragraph ``The checker SHALL write `Cannot read notes.txt. Check the path.` on failure.``
- **THEN** it returns the paragraph as one sentence

### Requirement: Sentence lines
The Markdown reader SHALL give each sentence the 1-based line of the source on which the sentence starts.

#### Scenario: Escapes and entities earlier in the paragraph
- **WHEN** the reader reads `Some \*escaped\* text &amp;amp; more.` followed on line 2 by `The scheduler shall list events.`
- **THEN** it returns that sentence on line 2

#### Scenario: Sentence starting with inline code
- **WHEN** the reader reads a document whose line 7 is the list item ``- `ears-checker` shall check every line.``
- **THEN** it returns `ears-checker shall check every line.` on line 7

### Requirement: Same results on every platform
While a document nests no deeper than every platform's parser follows, the Markdown reader SHALL return the same sentences, with the same lines, on the JVM, on Node, and on Scala Native.

#### Scenario: Conformance suite
- **WHEN** the conformance suite runs its fixtures on the JVM, on Node, and on Scala Native
- **THEN** each fixture gives the same sentences and lines on all three

#### Scenario: Constructs one parser misplaces
- **WHEN** the reader reads `[r]: /u` followed on line 2 by `The scheduler shall list.`, on each platform
- **THEN** it returns that sentence on line 2 on all three, though cmark itself places the paragraph on line 1

### Requirement: Deep nesting
If a document nests constructs thousands of levels deep, then the Markdown reader SHALL return its sentences without failing.

#### Scenario: Ten thousand nested block quotes
- **WHEN** the reader reads 10,000 nested block quotes around `The scheduler shall list.`, on each platform
- **THEN** it returns at most one sentence and doesn't fail, though the platforms may differ on whether they return it

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
