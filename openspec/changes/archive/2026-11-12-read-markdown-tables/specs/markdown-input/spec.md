## MODIFIED Requirements

### Requirement: Sentences in paragraphs and list items

When the Markdown reader reads a document, the Markdown reader SHALL return each sentence of each paragraph and of each table cell, list items included, that contains the modal keyword outside inline code.

#### Scenario: List item

- **WHEN** the reader reads `# Reminders` followed by a list item `- The scheduler SHALL list upcoming events.` and a fenced code block containing `SHALL inside code`
- **THEN** it returns only `The scheduler SHALL list upcoming events.`

#### Scenario: Keyword only in inline code

- **WHEN** the reader reads the paragraph ``Write `SHALL` in uppercase.``
- **THEN** it returns no sentence

## ADDED Requirements

### Requirement: Tables

When a line of a paragraph is followed by a delimiter row with as many cells, the Markdown reader SHALL read that line, the delimiter row, and every later line of the paragraph up to the table's end as a table.

#### Scenario: A table with outer pipes

- **WHEN** the reader reads `| Event | Response |`, `|---|---|`, `| Payment fails | The billing service shall retry the charge. |`, and `| Refund | The billing service shall notify the customer |` on lines 5 to 8
- **THEN** it returns `The billing service shall retry the charge.` on line 7 and `The billing service shall notify the customer` on line 8, and nothing with a pipe in it

#### Scenario: A table without outer pipes

- **WHEN** the reader reads `Event | Response`, `--- | ---`, and `Payment fails | The billing service shall retry the charge.`
- **THEN** it returns `The billing service shall retry the charge.` on line 3

### Requirement: Pipes without a delimiter row

If no delimiter row with as many cells follows a line with pipes, then the Markdown reader SHALL read the line as paragraph text.

#### Scenario: No delimiter row

- **WHEN** the reader reads `The router shall forward | packets to the uplink.` and `The router shall drop | malformed frames.` as one paragraph
- **THEN** it returns both lines' sentences with their pipes, as it would without the pipes

#### Scenario: A delimiter row with a different number of cells

- **WHEN** the reader reads `| Event | Response |` followed by `|---|---|---|` and a row
- **THEN** it reads the three lines as one paragraph, not a table

### Requirement: A table after paragraph lines

When a table starts after the first line of a paragraph, the Markdown reader SHALL read the paragraph's lines before the table as a paragraph of their own.

#### Scenario: No blank line before the table

- **WHEN** the reader reads `The billing service shall retry the charge.`, `| Event | Response |`, `|---|---|`, and `| Refund | When a refund is issued the billing service shall notify the customer. |` with no blank line between them
- **THEN** it returns `The billing service shall retry the charge.` on line 1 and `When a refund is issued the billing service shall notify the customer.` on line 4

### Requirement: Cells as text

The Markdown reader SHALL read each cell of a table's header row and body rows as text of its own, with any sentence in it ending at the cell's end.

#### Scenario: Two requirements in one row

- **WHEN** a table row is `| The scheduler shall list events. | The scheduler shall notify users |`
- **THEN** the reader returns `The scheduler shall list events.` and `The scheduler shall notify users` as two sentences

#### Scenario: A short row

- **WHEN** a table's header has three cells and a body row `| A | The proxy shall reject the request. |` has two
- **THEN** the reader returns `The proxy shall reject the request.` and no warning

### Requirement: Cell lines

The Markdown reader SHALL give each sentence in a table cell the line of its row.

#### Scenario: Row line

- **WHEN** a table's third body row, on line 6, holds `The billing service shall retry the charge.`
- **THEN** the reader returns the sentence with line 6

### Requirement: Cell boundaries

The Markdown reader SHALL split a table row into cells at every pipe that is neither backslash-escaped nor written as a character reference, inside inline code as well as outside it.

#### Scenario: An escaped pipe

- **WHEN** a table row is `| B | The proxy shall log \| escaped pipes. |`
- **THEN** the reader returns `The proxy shall log | escaped pipes.` as one sentence

#### Scenario: An escaped pipe in inline code

- **WHEN** a table row is ``| A | The proxy shall reject `a \| b` headers. |``
- **THEN** the reader returns ``The proxy shall reject `a | b` headers.``

#### Scenario: A character reference

- **WHEN** a table row is `| B | The proxy shall log &#124; entities. |`
- **THEN** the reader returns `The proxy shall log | entities.` as one sentence

#### Scenario: A bare pipe in inline code

- **WHEN** a two-column table's body row is ``| A | The proxy shall reject `a|b` headers. |``
- **THEN** the reader splits the row at that pipe, as GitHub does, so the row has 3 cells and the reader returns a warning for it

### Requirement: Cells as inline text

The Markdown reader SHALL read a table cell's text as inline text, never as a list, heading, quote, or other block.

#### Scenario: A cell that looks like a list item

- **WHEN** a table row is `| 1) The scheduler shall retry the job. | x |`
- **THEN** the reader returns `1) The scheduler shall retry the job.` as the cell's sentence

### Requirement: References in cells

The Markdown reader SHALL read a reference link in a table cell by the document's definition of its label.

#### Scenario: A reference link around the system's name

- **WHEN** a table cell is `[The scheduler][s] shall list events.` and the document defines `[s]: https://example.com/scheduler`
- **THEN** the reader returns `The scheduler shall list events.`

### Requirement: Rows before inline text

The Markdown reader SHALL split a table's rows from each other and from the lines before the table before it reads their inline formatting.

#### Scenario: Inline code that would cross a line break

- **WHEN** the reader reads ``Intro `a``, ``b` | c``, `|---|---|`, and `| x | The proxy shall reject y. |` as one paragraph
- **THEN** it returns `The proxy shall reject y.` on line 4, as GitHub shows it in a table

### Requirement: Extra cells unread

If a table's body row has more cells than its header row, then the Markdown reader SHALL leave the cells beyond the header's count unread.

#### Scenario: Four cells under a two-cell header

- **WHEN** a two-column table has the body row `| C | one | The billing service shall refund the order. | two |`
- **THEN** the reader returns no sentence from that row's third and fourth cells

### Requirement: Extra cells warning

If a table's body row has a cell with text beyond its header row's count, then the Markdown reader SHALL return a warning with the row's line, its number of cells, and the header's number of cells.

#### Scenario: Warning for a row with extra cells

- **WHEN** the two-column table's body row on line 9 is `| C | one | two | three |`
- **THEN** the reader returns a warning for line 9 with 4 cells and a header of 2

#### Scenario: An empty extra cell

- **WHEN** a two-column table's body row is `| A | The proxy shall log the request. | |`
- **THEN** the reader returns `The proxy shall log the request.` and no warning

### Requirement: Table end

When a line after a table's delimiter row would end the table on GitHub, the Markdown reader SHALL read that line and the rest of the paragraph as the Markdown they are, outside the table.

#### Scenario: A lazy line after a table in a block quote

- **WHEN** the reader reads `> | ID | Requirement |`, `> |---|---|`, `> | A | The scheduler shall list events. |`, and `The calendar shall sync. | note`
- **THEN** it returns `The scheduler shall list events.` on line 3 and `The calendar shall sync.` on line 4, read as paragraph text rather than a row, with no warning

#### Scenario: An indented code line after a table

- **WHEN** a table's last row is followed in the same paragraph by `    The proxy shall log | requests.`
- **THEN** the reader returns nothing from that line, which GitHub shows as code
