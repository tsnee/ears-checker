## ADDED Requirements

### Requirement: Inline code
The EARS classifier SHALL read a code span, from a run of backticks not escaped by a backslash to the next run of the same length, as one word whose content takes no part in classification.

#### Scenario: A quoted message with periods
- **WHEN** the classifier receives "The checker SHALL write `Cannot read notes.txt. Check the path.` to standard error." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree whose response contains the whole code span

#### Scenario: A quoted modal word
- **WHEN** the classifier receives "The scheduler SHALL reject an event titled `The calendar shall sync` with a warning." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree, with no multiple-responses diagnostic

#### Scenario: The only modal is quoted
- **WHEN** the classifier receives "The linter writes `SHALL` in uppercase." under the OpenSpec profile
- **THEN** it reports a missing-modal diagnostic

#### Scenario: Double backticks around a backtick
- **WHEN** the classifier receives "The formatter SHALL escape ``a ` b`` in output." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree whose response contains ``a ` b``

#### Scenario: An unmatched backtick
- **WHEN** the classifier receives "The shell SHALL reject a lone ` in a name. Then it SHALL stop." under the OpenSpec profile
- **THEN** it reports a multiple-sentences diagnostic, since the backtick quotes nothing

#### Scenario: Escaped backticks
- **WHEN** the classifier receives "The shell SHALL print \`now. Then\` on start." under the OpenSpec profile
- **THEN** it reports a multiple-sentences diagnostic, since escaped backticks quote nothing, as in Markdown

#### Scenario: An escaped backslash before code
- **WHEN** the classifier receives "The exporter SHALL write to C:\\`out. Then` daily." under the OpenSpec profile
- **THEN** it returns a ubiquitous syntax tree whose response contains the code span `` `out. Then` ``, since the backslash escapes the backslash before it, as in Markdown

#### Scenario: A system name in code
- **WHEN** the classifier receives "`job-runner` SHALL retry failed jobs." under the OpenSpec profile
- **THEN** it reports a missing-article diagnostic whose fix gives "The `job-runner` SHALL retry failed jobs."

## MODIFIED Requirements

### Requirement: Multiple sentences
If the input contains, outside inline code, a period followed by whitespace and further text, then the EARS classifier SHALL report a multiple-sentences diagnostic.

#### Scenario: Two sentences
- **WHEN** the classifier receives "The scheduler SHALL list upcoming events. It SHALL hide past events."
- **THEN** it reports a multiple-sentences diagnostic
