# Design

## Context

The Markdown facade (`ears/markdown`) gives shared code each paragraph as `Paragraph(line, pieces)`: decoded text and inline code, each `Piece` with its 1-based line, and a line break inside the paragraph as `Piece(" ", false, None)`. Each platform's parser builds those pieces (commonmark-java, commonmark.js through `SourceLines`, cmark through `CmarkLines`); `Extraction.sentences` (shared) finds the sentences. `Markdown.sentences(content)` is the facade's one entry point, used by the CLI's `MarkdownInput` and by the conformance suite, which runs every fixture on all three platforms.

Observations, 2026-11-11 and -12:

- All three builds of 0.2.0 read a table as one paragraph (one garbled finding for a table alone; nothing at all for a table right after a paragraph, whose requirements go unchecked).
- Of the libraries, only commonmark-java has a table extension (`commonmark-ext-gfm-tables`, at the facade's 0.31.0); commonmark.js has none and no extension mechanism; natively only GitHub's cmark-gfm fork does, a different library on an older CommonMark (0.29). Using them would mean three table implementations and a changed native dependency, so tables are recognized in shared code.
- GitHub's spec (github.github.com/gfm, "Tables (extension)"): the header row must match the delimiter row in cells, or there's no table; a body row with fewer cells gets empty ones, one with more has the excess ignored; `\|` includes a pipe in a cell, inside other inline spans too; a table ends at a blank line or another block.
- GitHub's renderer (`gh api markdown`, mode gfm): a table directly after a paragraph line interrupts it (the paragraph ends, the table follows); a bare pipe inside inline code splits the cell (`` `a|b` `` leaves "…reject `a" and an excess cell); `&#124;` stays in its cell; a body row's extra cells aren't shown; lines with pipes and no delimiter row are a paragraph.
- Every parser's break piece is `Piece(" ", false, None)`. On Node and Scala Native every other piece has a line (`SourceLines` and `CmarkLines` give one even to text they can't place); on the JVM a piece's line comes from commonmark-java's inline source spans.
- `docs/json-output.md`, while the version is 1: new fields can appear without a new version, and consumers ignore fields they don't know.
- Container markers (`- `, `* `, `+ `, `1. `, `> `) contain no pipe, so a paragraph line's pipes in the original source are exactly its row's pipes, wherever the paragraph sits.

## Goals / Non-Goals

**Goals:**

- Each requirement in a table is checked on its own, on its row's line, the same on every platform.
- Cells split, and their text read, as GitHub does it, by following GitHub's own order: rows and cells from the raw text first, inline formatting per cell after.
- A row whose extra cells GitHub hides is reported where the user looks: beside the findings, in both output formats, and in the log.

**Non-Goals:**

- Alignment (`:---:`) and any rendering: the reader only reads text.
- A switch to fail on warnings: easy to add if someone asks.
- A table that a `---` or `===` line directly under it turns into a setext heading: the parsers don't hand headings over, so it isn't read yet. It's the theme's next change (the owner chose to read it as GitHub does), using this change's row logic on the heading's lines.

## Decisions

### 1. Raw rows, re-parsed cells (GitHub's own order)

GitHub finds a table's rows and cells in the raw text, then reads each cell's inline formatting on its own. The reader does the same, in shared code:

1. **Paragraph lines.** The parsers still say which lines are paragraph text. Each `Paragraph` also carries its last line, which every parser knows (commonmark-java's block source spans, commonmark.js's `sourcepos`, cmark's end line): a small addition to each.
2. **Container markers.** For a paragraph, the reader takes its raw lines from the document, from the paragraph's real first line (decision 2's details: after any link reference definitions, even when that line has no text piece, such as an image alone), and strips each one's container prefix: leading spaces and tabs and block-quote markers (`>`, with one optional space after it), repeated; on the paragraph's first line, also list markers (`-`, `+`, `*`, or one to nine ASCII digits and `.` or `)`, followed by a space or tab), repeated. It notes each line's container: how many `>` it had and its indentation past the container's content. Applied only to find and read tables.
3. **Rows and cells.** On the stripped lines, the first line followed by a delimiter row with as many cells starts a table (a delimiter row indented four or more spaces past its container's content isn't one), and later lines are body rows until the first that GitHub would end the table with: one with fewer `>` than the header (a lazy line), one indented four or more spaces past the container's content (a code block), or one whose stripped text starts a block (any list marker, an empty item included, or an HTML block start of CommonMark's seven kinds). The lines from there to the paragraph's end are read again as ordinary Markdown in a small parse of their own (with the table's container prefix removed), so a code block or HTML is skipped, a list item is read as one, and a lazy line is a paragraph, as GitHub shows them; their pieces take their original lines. A row splits into cells at each `|` with no backslash right before it (any run of backslashes escapes it, as GitHub does); in each cell, one backslash before each escaped pipe is removed, and the cell is trimmed. An empty first or last cell from an outer pipe is dropped. A delimiter row is cells each `:?-+:?` with optional spaces, at least one pipe.
4. **Inline text, re-parsed.** The lines before the table (joined as they were) and each nonempty cell are read again by the platform's parser, all in one document per source document: each as its own paragraph, prefixed by `x ` and separated by blank lines, followed, when any of them holds a `[`, by a blank line and the whole original document. The `x ` prefix makes each a plain paragraph, never a list, heading, quote, fence, HTML block, or link definition (a cell is inline text on GitHub); it's removed from the first piece afterward. The original document after them gives its link reference definitions (`[s]: …`) to the cells, since CommonMark resolves references anywhere in a document, and coming after, it can't change how they're read; with no `[` in any segment there's no reference to resolve, and the parse costs only the cells. The parse reuses the platform's already-loaded parser (on Scala Native, the cmark library loaded once). The first paragraphs of that parse are the cells and the earlier lines, in order; their pieces take the original lines (the row's line for a cell).
5. **Sentences.** Each such paragraph goes through `Extraction.sentences`, which already ends a sentence at the paragraph's end.

A document with no `|` skips all of this; one without a table costs a scan of its paragraphs' lines; a document with tables costs one more parse (plus a small one for any lines after a table ends inside a paragraph). An extra cell warns only when it has text: an empty one hides nothing.

_Alternatives considered:_ (a) the parsed pieces, with the raw line used only to tell escaped pipes from separators by counting them (the first version of this change): it kept every parser untouched but differed from GitHub wherever a pipe hid in a link destination or HTML, a reference sat inside inline code, or inline code crossed a line break, each needing its own patch. The owner chose to match GitHub (2026-11-12). (b) Each cell parsed as its own document: simpler, but a cell like `1. Retry` would be read as a list and `[s]` references wouldn't resolve, which changes results (a reference link around the system's name gives a false `missing-article` finding, checked with the 0.2.0 jar).

### 2. Details checked against GitHub

Settled during apply (2026-11-12), each checked on GitHub's renderer:

- A character reference is never a separator (`&#124;` and the named forms stay in the cell), and after a backslash (`\&#124;`) it's literal text.
- A pipe with any run of backslashes right before it never separates cells, and GitHub removes one backslash before reading the cell: `x \\| y` shows `x | y` in one cell; in inline code, `` `x \\| y` `` shows code `x \| y`. (An earlier detail, "`\\|` separates, as cmark-gfm does", was written from memory and wrong on GitHub.)
- A pipe in a link's destination, or anywhere in the raw row, splits the cell on GitHub; with raw rows the reader does the same.
- A bare pipe inside inline code splits the cell, and GitHub then shows the cell's lone backtick as text; re-parsing the cell gives the same.
- Inline code can't cross from one row (or a line before the table) into the next, since rows are split before inline formatting is read: ``Intro `a`` over ``b` | c`` over a delimiter row is a paragraph then a table, on GitHub and here.
- **A known difference, accepted by the owner:** a link reference definition directly above a table, with no blank line, is paragraph text on GitHub (the table interrupts it before it becomes a definition), so a cell's `[The scheduler][s]` stays unresolved there; all three parsers take the line as a definition, so the reader resolves the link and checks the requirement as its author meant. Matching GitHub would mean overriding all three parsers on what a definition is. A fixture records it.

Settled while implementing the rework (2026-11-12):

- A paragraph's last line travels in a wrapper (`ParsedParagraph(paragraph, lastLine)`), not a new field of `Paragraph`, which would have changed an existing test's expected value.
- A paragraph's raw lines start at its real first line: commonmark.js counts a reference definition above a paragraph as part of the paragraph's block, and a first line with no text piece (an image alone, inline HTML) can't be found from the pieces. So the reader starts from the block's first line and, when that differs from the first piece's line, skips the leading lines that are definitions: the longest run of leading lines that, parsed on their own, yield no paragraph. (Changed after the second review: the first version started at the first piece's line, which misses an image-only header and can strip a list marker from what is really a continuation line.)
- (After the second review, checked on GitHub's renderer 2026-11-12.) A table ends where GitHub ends it, not at the paragraph's end: a lazy line out of a block quote or list item, a list marker (`2. …` can't interrupt a paragraph but does end a table, and an empty `- ` item does too), a line indented four or more past the container's content (a code block), or an HTML block start; and a delimiter row indented four or more isn't one. The first version made every later paragraph line a row, so code-block text was checked as requirements, lazy text split into cells, and lines that aren't rows drew extra-cells warnings.
- In the extra parse, the lines before a table are re-parsed with every line after the first indented by four spaces, so a continuation line that looks like a list item (an indented `- …`) stays paragraph text, as on GitHub, instead of starting a list.
- A paragraph is only examined for a table when one of its later raw lines holds both `|` and `-`; documents with no such paragraph are never parsed again (a test with a parser that fails if called holds it).

Settled while applying the second review's fixes (2026-11-12):

- **Indentation in containers is approximate.** A paragraph's own lines don't always show its list item's content column. When the block's first line has no marker and one to three leading spaces, those are the paragraph's own indentation; four or more count as an unknown item's content. In the cases that stay uncertain the reader keeps reading rows rather than dropping a table.
- **Lines after a table's end.** They're parsed as their own document, so a second table among them is read too. Lines before the first lazy one lose the table's container prefix; from the lazy line on they're parsed as written, since GitHub has closed those containers. The original document follows them only when they hold a `[`.
- **Trimming** is over ASCII whitespace only (cmark-gfm's `cmark_isspace`: space, tab, LF, VT, FF, CR), not `String.strip`, which would also trim Unicode whitespace that GitHub keeps in a cell.
- **The parser is loaded once per read** (`MarkdownParser.loaded()`), and every extra parse reuses it; on Scala Native that's one `CmarkLoader.load()`. A paragraph's `line` is now its block's first line on every platform, the link reference definitions opening it included, as commonmark.js always reported it.
- A header line with no pipe over a delimiter row with one (`| --- |`) is a one-column table; checked on GitHub's renderer, 2026-11-12. A bare `---` under a line is a setext heading, which no parser hands over as a paragraph, so it never reaches the table rule.
- The Markdown reader returns only sentences with "shall" (`Extraction.keyword`), in table cells as elsewhere, so a fixture's requirement sentences use "shall"; a sentence with only "should" is prose to the reader, table or not (the owner kept that rule, 2026-11-12: reading "should" requirements in Markdown would be a change of its own).

The row splitter and the prefix stripper are pure and shared, so they're measured by scoverage and tested directly, with a property that a row built from cells (each pipe in them escaped) splits back into those cells, besides the fixtures.

### 3. The facade returns warnings beside sentences

`Markdown.read(content): Either[String, MarkdownText]`, with `final case class MarkdownText(sentences: List[Sentence], warnings: List[MarkdownWarning])` and `enum MarkdownWarning { case ExtraCells(line: Int, cells: Int, headerCells: Int) }`, plain data in the facade (no messages: wording belongs to the CLI). `Markdown.sentences` stays, as `read(...).map(_.sentences)`, so existing callers and tests don't change. Original lines are split as the Node parser already does (`\r\n`, `\r`, `\n`); that helper moves to shared code for both uses.

### 4. Warnings in the CLI

`MarkdownInput` returns the candidates and the warnings; `Checker`'s outcome carries warnings beside findings, with their `Location.Line`; the exit status is computed from findings as now. Text output merges an input's findings and warnings by line (a finding first on a tie) and writes a warning as `<file>:<line>: warning: table-extra-cells: This table row has <n> cells and its header <m>, so GitHub doesn't show the last <n-m>.`; the location goes through the same escaping as findings'. JSON output adds `"warnings": [...]` after `"findings"`, each `{"location": …, "kind": "table-extra-cells", "message": …}`, empty when there are none. The effect code logs each warning at WARN with the input, line, kind, and message (a logging test asserts that, under the global rule's exception for tests of the logging itself).

### 5. Tests and fixtures

- **Conformance fixtures** (all three platforms), one per spec scenario: a table with and without outer pipes, after a paragraph line, pipes with no delimiter row, a delimiter row with a different count, two requirements in a row, a short row, an escaped pipe in text and in inline code, `&#124;`, a bare pipe in inline code, and a row with extra cells (its sentences and its warning). Each fixture's expected result is written by hand and agrees with GitHub's renderer, checked when the fixture is written.
- **Unit tests** for the row splitter and the container-prefix stripper, with the round-trip property of decision 2; fixtures for a reference link in a cell, a cell that would be a list or heading as a document, inline code crossing a line break before a table, and tables in nested containers (a list item in a block quote).
- **CLI tests**: the warning line in text output, the `warnings` array (and its empty form), line order with findings, exit status 0 for a warning alone, and the log entry.
- **`scripts/cli-cases`**: a Markdown table case in text and JSON, with a finding and an extra-cells row, compared across the builds.

## Risks / Trade-offs

- **The container-prefix rule** (decision 1, step 2) is the one place shared code reads Markdown structure itself. It covers block quotes, list items, and their nesting, with fixtures for each on all three platforms; a container it doesn't know would leave its marker in the first cell.
- **One more parse** for a document with tables, on every platform; a document without tables pays nothing.
- **Platform code**: each parser reports a paragraph's last line. That's the only parser change, and the conformance suite holds the three to the same results.
- **Output contract**: text output gains a line kind, and JSON a field. Both are documented; consumers of JSON ignore unknown fields by the document's rules, and the text format's findings keep their form.
- **Unverified assumptions:** that commonmark-java gives every text and code piece a line (above); that commonmark.js and cmark decode `&#124;` and the named references to `|` in text, as CommonMark requires; that every parser keeps `\|` literally in a code span. All three are checked by the fixtures in task 1, before anything is built on them. Checked today: the GitHub behaviors, the libraries' table support, the JSON stability rules, the break pieces' form.
- **Checked in task 1.1 (2026-11-12), on the JVM and Node:** a throwaway probe printed each parser's pieces for a table with `\|` in text and in inline code and `&#124;`, `&vert;`, and `&#x7C;` in text. Both gave identical pieces: every text and code piece had its line and only the breaks had none; inline code kept `a \| b` literally; the escaped `\|` in text and all three references arrived as `|`. Scala Native is held to the same by the conformance fixtures in the gate.
