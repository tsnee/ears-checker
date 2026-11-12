# Design

## Context

`read-markdown-tables` finds tables in the paragraphs each platform's parser gives (`ParsedParagraph`: a `Paragraph` and its last line), from the paragraph's raw lines (`Rows`, `Tables`). A setext heading isn't a paragraph, so a table whose block ends in an underline never reaches `Tables`, and headings aren't read.

Observations, 2026-11-12:

- **GitHub's renderer** (`gh api markdown -f mode=gfm`): see the proposal's list. In short, a block with a table in it is never a heading: the underline is the table's next line. With no table in the block, an underline makes a heading, as in CommonMark (`| The scheduler shall list events. |` over `---` is an `<h2>`).
- **The parsers' heading lines**: each reports a setext heading's lines from its first line through its underline. cmark 0.31.2 (`cmark --sourcepos -t xml`): `heading sourcepos="1:1-5:3"` for a four-line table under `---`, and `1:3-4:5` inside a block quote. commonmark.js (the version in `ears/markdown/js/package-lock.json`): the same `sourcepos`, `[[1,1],[5,3]]` and `[[1,3],[4,5]]`. commonmark-java 0.31.0 (a scala-cli probe): source spans on lines 0 to 4, and 0 to 3 in the block quote, one span per line.
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

`ParsedParagraph` gains a field, `underlined: Boolean`: true for a heading that spans more than one line. Each parser gives such a heading exactly as it gives a paragraph (its block's first line, its pieces, its last line, which is the underline's), so everything `Tables` does with a paragraph's raw lines works on it unchanged: the container prefixes, the first line after any link definitions (commonmark-java's `blockStart` walks back over the definitions before a heading as before a paragraph), and the table's end. On Scala Native, `CmarkLines.paragraph` is given the line before the underline.

Alternative: an enum of blocks (`Paragraph`, `SetextHeading`). It says the same thing with more code at every use, and every use treats the two the same except one.

### 2. `Tables` reads an underlined paragraph only for its table

An underlined paragraph that holds a table is laid out and read exactly as a paragraph that does (the underline is its last line, so it's a body row, or it ends the table and is read again as Markdown of its own). One that holds no table is dropped, so headings stay unread. That includes the shortcut for a document with no pipe, which today keeps every paragraph as it is: it drops the underlined ones.

### 3. A thematic break ends a table

`Rows.endsTable` also ends a table at a thematic break: after the container prefix and at most three spaces of indent, three or more of the same `-`, `*`, or `_`, with nothing else but spaces and tabs (CommonMark's rule, which GitHub follows). Read again on its own, the line is a rule, with nothing read. `-` alone is already a list item; `===`, `=`, `==`, and `--` aren't breaks, so they're body rows, as on GitHub.

### 4. Tests

- Conformance fixtures (`MarkdownConformanceSuite`, run on the JVM, Node, and Scala Native), each checked against GitHub's renderer, for each spec scenario and the proposal's other cases: `---` and `===` under a table; paragraph lines before it; a header row with no body rows; a block quote and a list item; `-` alone, `--`, `- - -`, `-- -`, `   ---`, and `    ---`; and the one-column line over `---`, which stays a heading. Each new fixture fails today (none of its sentences is read) except the one-column heading, which describes today's behavior and guards it.
- `RowsSuite`: thematic breaks end a table, and the near misses (`--`, `===`, `- -`, a break indented four) don't, as a property over generated lines where it can be stated.
- `TablesSuite`: an underlined paragraph with a table is laid out as a paragraph; one without is dropped, with or without a pipe in the document.
- A comment in the fixture "an empty list item ends a table" says `-` alone "would make the lines above it a setext heading, which isn't read yet"; once this change is in, that's wrong. Editing it touches an existing test file, so it's asked about first.

## Risks / Trade-offs

- **[A parser gives a setext heading's lines differently in some case]** → The probes covered a top-level block and a block quote on all three; the list-item fixture covers that container. A difference would show as a fixture failing on one platform.
- **[cmark's piece lines in a heading]** → `CmarkLines` placing from the line before the underline is read from its code, not run; the fixtures' line numbers check it on Scala Native.
- **[Cost]** → An underlined block now goes through `Tables`' pipe-and-dash check, which most headings fail at once; no measurable change expected, and none is measured.
