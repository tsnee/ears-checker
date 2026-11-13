# markdown-input Specification

## Purpose

Reads requirement sentences from Markdown documents for the EARS checker, with the same sentences and lines on every platform the checker builds for.

## Requirements

### Requirement: Sentences in paragraphs and list items

When the Markdown reader reads a document, the Markdown reader SHALL return each sentence of each paragraph and of each table cell, list items included, that contains the modal keyword outside inline code.

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

### Requirement: cmark found at run time

When the Markdown reader on Scala Native first reads a document, the Markdown reader SHALL load an installed cmark library of release 0.30 or later, whatever its exact release.

#### Scenario: Homebrew's cmark

- **WHEN** the reader runs natively on macOS with Homebrew's cmark installed in `/opt/homebrew/lib`
- **THEN** it loads that cmark and reads the document

#### Scenario: Ubuntu's runtime package only

- **WHEN** the reader runs natively on Ubuntu with only cmark's runtime package `libcmark0.30.2` installed, which has `libcmark.so.0.30.2` and no `libcmark.so`
- **THEN** it loads `libcmark.so.0.30.2` and reads the document

#### Scenario: A newer release

- **WHEN** the reader runs natively where the installed cmark is a release newer than any the conformance suite has run on
- **THEN** it loads that cmark and reads the document

#### Scenario: An older release earlier in the search

- **WHEN** the reader runs natively on Linux where `/usr/local/lib/libcmark.so` is cmark 0.29 and `/usr/lib/x86_64-linux-gnu/libcmark.so.0.30.2` is installed
- **THEN** it loads `libcmark.so.0.30.2` and reads the document

### Requirement: No library from the current directory

The Markdown reader on Scala Native SHALL load cmark only by an absolute path, apart from the bare name `libcmark.so` on Linux while `LD_LIBRARY_PATH` names only absolute directories.

#### Scenario: A library planted in a repository

- **WHEN** the reader runs natively on macOS in a directory that holds a file named `libcmark.dylib`, with Homebrew's cmark installed
- **THEN** it loads Homebrew's cmark from `/opt/homebrew/lib` and never opens the file in the current directory

#### Scenario: An empty entry in LD_LIBRARY_PATH

- **WHEN** the reader runs natively on Linux with `LD_LIBRARY_PATH` set to `:/opt/lib`, in a directory that holds a file named `libcmark.so`, with cmark installed in `/usr/lib/x86_64-linux-gnu`
- **THEN** it loads cmark by its absolute path in `/usr/lib/x86_64-linux-gnu` and never opens the file in the current directory

### Requirement: Relative library path on macOS

If `DYLD_LIBRARY_PATH` has a relative directory entry, then the Markdown reader on Scala Native on macOS SHALL decline to load cmark, with a message naming `DYLD_LIBRARY_PATH`.

#### Scenario: The current directory in DYLD_LIBRARY_PATH

- **WHEN** the native binary reads a Markdown file on macOS with `DYLD_LIBRARY_PATH` set to `.`, in a directory that holds a file named `libcmark.dylib`
- **THEN** it exits with status 2 and a message naming `DYLD_LIBRARY_PATH`, and never opens the file in the current directory

#### Scenario: An empty entry in DYLD_LIBRARY_PATH

- **WHEN** the native binary reads a Markdown file on macOS with `DYLD_LIBRARY_PATH` set to `:/opt/homebrew/lib`, in a directory that holds a file named `libcmark.dylib`
- **THEN** it loads cmark from `/opt/homebrew/lib`, since macOS ignores the empty entry

#### Scenario: Plain text with the current directory in DYLD_LIBRARY_PATH

- **WHEN** the native binary reads a plain-text file on macOS with `DYLD_LIBRARY_PATH` set to `.`
- **THEN** it checks the file as usual

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
