## ADDED Requirements

### Requirement: Underline after a table

When a line of only `-` or `=` characters, spaces, and tabs directly follows a table's row, the Markdown reader SHALL read that line as the table's next line, never as the underline of a heading.

#### Scenario: A rule under a table

- **WHEN** the reader reads `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, `| B | The calendar shall sync. |`, and `---`
- **THEN** it returns `The scheduler shall list events.` on line 3 and `The calendar shall sync.` on line 4, and the `---` line is a rule, with nothing read from it

#### Scenario: An equals line under a table

- **WHEN** the reader reads `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, and `===`
- **THEN** it returns `The scheduler shall list events.` on line 3, the `===` line is a body row with no sentence, and there is no warning

#### Scenario: Paragraph lines before an underlined table

- **WHEN** the reader reads `The report shall print.`, `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, and `===`
- **THEN** it returns `The report shall print.` on line 1 and `The scheduler shall list events.` on line 4

#### Scenario: A rule under a table in a block quote

- **WHEN** the reader reads `> | ID | Requirement |`, `> |---|---|`, `> | A | The scheduler shall list events. |`, and `> ---`
- **THEN** it returns `The scheduler shall list events.` on line 3

#### Scenario: A single dash under a table

- **WHEN** the reader reads `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, and `-`
- **THEN** it returns `The scheduler shall list events.` on line 3, and the `-` line is an empty list item

#### Scenario: A definition and an equals line over a table

- **WHEN** the reader reads `[a]: /url`, `===`, `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, and `---`
- **THEN** it returns `The scheduler shall list events.` on line 5, since the `===` line is paragraph text on GitHub, not an underline

### Requirement: Rows after an underline row

When a table's last row is a line of only `-` or `=` characters, spaces, and tabs that doesn't end the table, the Markdown reader SHALL read the lines directly after it as the table's next lines.

#### Scenario: Rows after an equals line

- **WHEN** the reader reads `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, `===`, `| B | The calendar shall sync. |`, and `| C | The report shall print. | extra |`
- **THEN** it returns `The scheduler shall list events.` on line 3, `The calendar shall sync.` on line 5, and `The report shall print.` on line 6, with a warning for line 6, which has 3 cells under a header of 2

#### Scenario: A block quote after an equals line

- **WHEN** the reader reads `| ID | Requirement |`, `|---|---|`, `| A | The scheduler shall list events. |`, `===`, and `> The calendar shall sync.`
- **THEN** it returns `The scheduler shall list events.` on line 3 and `The calendar shall sync.` on line 5, read from the block quote rather than a row

### Requirement: Underline without a table

If a block of lines underlined by a line of `-` or `=` characters holds no table, then the Markdown reader SHALL read the block as a heading, with no sentence read from it.

#### Scenario: A one-column line over a rule

- **WHEN** the reader reads `| The scheduler shall list events. |` and `---`
- **THEN** it returns no sentence, since the lines are a heading on GitHub too, with no delimiter row
