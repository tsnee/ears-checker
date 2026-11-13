# Design

## Context

`read-markdown-tables` finds tables in the paragraphs each platform's parser gives (`ParsedParagraph`: a `Paragraph` and its last line), from the paragraph's raw lines (`Rows`, `Tables`). A setext heading isn't a paragraph, so a table whose block ends in an underline never reaches `Tables`, and headings aren't read.

Observations, 2026-11-12:

- **GitHub's renderer** (`gh api markdown -f mode=gfm`): see the proposal's list. In short, a block with a table in it is never a heading: the underline is the table's next line. With no table in the block, an underline makes a heading, as in CommonMark (`| The scheduler shall list events. |` over `---` is an `<h2>`).
- **The parsers' heading lines**: each reports a setext heading's lines from its first line through its underline. cmark 0.31.2 (`cmark --sourcepos -t xml`): `heading sourcepos="1:1-5:3"` for a four-line table under `---`, and `1:3-4:5` inside a block quote. commonmark.js (the version in `ears/markdown/js/package-lock.json`): the same `sourcepos`, `[[1,1],[5,3]]` and `[[1,3],[4,5]]`. commonmark-java 0.31.0 (a scala-cli probe): source spans on lines 0 to 4, and 0 to 3 in the block quote, one span per line.
- **cmark's heading end** (found at the start of implementation): cmark 0.31.2 ends a setext heading that isn't at the document's end on the line after its underline: `1:1-3:23` for `The calendar shall heading`, `---`, `The report shall print.` (the paragraph's line), and `1:1-3:0` when a blank line follows; `1:1-2:3` at the document's end. commonmark.js and commonmark-java give the underline's line in all three. No cmark issue reports it (searched 2026-11-12; #141 and #296 are about other positions); the owner may file one.
- **Rows after an underline row** (GitHub, the same day): after a table's `===` row, `| B | The calendar shall sync. |` and `| C | The report shall print. | extra |` are body rows B and C, C's third cell hidden. The parsers end the heading at `===` and give the later lines as a paragraph of their own.
- **ATX headings** (`# …`) are one line, so a heading that spans more than one line is a setext heading on every parser. None of the parsers' node types says which kind a heading is, so the span is how each tells.
- **`CmarkLines.paragraph`** places a paragraph's pieces backward from its end line (`ears/markdown/src/.../CmarkLines.scala:23`), so for a heading it must be given the line before the underline, where the heading's text ends.
- **`Rows.endsTable`** ends a table at a lazy line, an indent of four or more, a list item, or an HTML block start (`Rows.scala:208`). A thematic break can't appear inside a paragraph (every parser ends the paragraph there), so it wasn't needed until now; a heading's underline is the first place one reaches `Rows`.

## Goals / Non-Goals

**Goals:**

- Read a table whose block ends in a `-` or `=` underline as GitHub does, on every platform, with the lines before it as a paragraph.
- Keep headings unread when they hold no table.

**Non-Goals:**

- Reading headings. ATX and setext headings stay unread, as `markdown-input` says.
- The other known difference, a link definition directly above a table, which stays as `read-markdown-tables` accepted it.

## Decisions

### 1. The parsers give setext headings as underlined paragraphs

`ParsedParagraph` gains a field, `underlined: Boolean`: true for a heading that spans more than one line. Each parser gives such a heading exactly as it gives a paragraph (its block's first line, its pieces, its last line, which is the underline's), so everything `Tables` does with a paragraph's raw lines works on it unchanged: the container prefixes, the first line after any link definitions (commonmark-java's `blockStart` walks back over the definitions before a heading as before a paragraph), and the table's end. Each parser reports the last line its library gives; `Tables` finds the underline itself (decision 2), so cmark's late end line does no harm. On Scala Native, `CmarkLines.paragraph` is given the line before the one cmark reports: the line before the underline, or the underline itself when cmark ends the heading a line late. That places a heading's pieces one line late at most, which is harmless: a heading's pieces are never read as sentences, and only bound `Tables.firstLine`, which backs down from that bound by parsing. (The owner chose this on 2026-11-13 over the native parser finding the underline itself, which would need the first line of text, and so `firstLine`, before the pieces are placed; it also leaves the native parser without a pass over the document's lines.)

Alternative: an enum of blocks (`Paragraph`, `SetextHeading`). It says the same thing with more code at every use, and every use treats the two the same except one.

### 2. `Tables` reads an underlined paragraph only for its table

- **The underline.** An underlined paragraph's last line is its underline: the first line after its first line of text (past any link definitions that open the block, as `Tables.firstLine` finds it), no later than the line the parser reports, whose text (past the container prefixes, as `Rows` strips them) is only `-` or only `=` characters, with at most three spaces before and any spaces after. No later line of a heading's text can look like that, since it would have been the underline, and the delimiter row has pipes; the first line of text can (`[a]: /url` then `===` makes `===` the heading's text, found by the code review), hence the search starts after it. This one rule serves all three parsers, cmark's late end included, and it's pure, shared code.
- **The table.** An underlined paragraph that holds a table is laid out and read exactly as a paragraph that does: the underline is its last line, so it's a body row, or it ends the table and is read again as Markdown of its own. One that holds no table is dropped, so headings stay unread. That includes the shortcut for a document with no pipe, which today keeps every paragraph as it is: it drops the underlined ones.
- **Rows after an underline row.** When the underline is a body row (the table hasn't ended), the paragraph the parser gives next, if it starts on the line right after the underline, holds the table's next rows. `Tables` lays the two out as one block, from the heading's first line to that paragraph's last line, so `Rows` decides where the table ends as everywhere else, and what follows its end is read again as Markdown. If that paragraph is itself underlined and its table goes on too, the next one joins as well. The parsers give a paragraph inside a following block too (a block quote's, on the line after the underline), so the join relies on `Rows.endsTable`: a line that starts a block quote, an ATX heading, or a code fence ends the table, as GitHub ends it there, and the join then reads that line and the rest again as Markdown. Inside an ordinary paragraph those lines can't appear (each interrupts the paragraph), so they reach `endsTable` only through a join. (Found by the code review: a quote's line after `===` was read as a row.)

### 3. A thematic break ends a table

`Rows.endsTable` also ends a table at a thematic break: after the container prefix and at most three spaces of indent, three or more of the same `-`, `*`, or `_`, with nothing else but spaces and tabs (CommonMark's rule, which GitHub follows). Read again on its own, the line is a rule, with nothing read. `-` alone is already a list item; `===`, `=`, `==`, and `--` aren't breaks, so they're body rows, as on GitHub.

### 4. Tests

- Conformance fixtures (`MarkdownConformanceSuite`, run on the JVM, Node, and Scala Native), each checked against GitHub's renderer, for each spec scenario and the proposal's other cases: `---` and `===` under a table; paragraph lines before it; a header row with no body rows; a block quote and a list item; `-` alone, `--`, `- - -`, `-- -`, `   ---`, and `    ---`; the one-column line over `---`, which stays a heading; rows after an `===` row (the spec's scenario), and a following heading over a table's `=` row; and a paragraph after a rule under a table. The fixtures whose last line the parsers already read as a setext underline fail today (none of their sentences is read). Those whose last line already ends a paragraph in every parser (`- - -`, `-- -`, `***`, `    ---`) pass today and guard that, as does the one-column heading.
- `RowsSuite`: thematic breaks end a table, and the near misses (`--`, `===`, `- -`, a break indented four) don't, as a property over generated lines where it can be stated.
- `TablesSuite`: an underlined paragraph with a table is laid out as a paragraph; one without is dropped, with or without a pipe in the document; the underline is found before a reported end one line too late, after a blank line or a paragraph, and in a block quote; and the next paragraph joins only after an underline row, only when it starts on the next line.
- A comment in the fixture "an empty list item ends a table" says `-` alone "would make the lines above it a setext heading, which isn't read yet"; once this change is in, that's wrong. Editing it touches an existing test file, so it's asked about first.

## Risks / Trade-offs

- **[A parser gives a setext heading's lines differently in some case]** → The probes covered a top-level block and a block quote on all three; the list-item fixture covers that container. A difference would show as a fixture failing on one platform.
- **[cmark's piece lines in a heading]** → `CmarkLines` placing from the line before the underline is read from its code, not run; the fixtures' line numbers check it on Scala Native.
- **[cmark fixes its end line]** → The underline search needs no change: the reported line would then be the underline itself.
- **[Cost]** → An underlined block now goes through `Tables`' pipe-and-dash check, which most headings fail at once; no measurable change expected, and none is measured.
