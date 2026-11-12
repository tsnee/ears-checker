# Design

## Context

The Markdown facade (`ears/markdown`) gives shared code each paragraph as `Paragraph(line, pieces)`: decoded text and inline code, each `Piece` with its 1-based line, and a line break inside the paragraph as `Piece(" ", false, None)`. Each platform's parser builds those pieces (commonmark-java, commonmark.js through `SourceLines`, cmark through `CmarkLines`); `Extraction.sentences` (shared) finds the sentences. `Markdown.sentences(content)` is the facade's one entry point, used by the CLI's `MarkdownInput` and by the conformance suite, which runs every fixture on all three platforms.

Observations, 2026-11-11 and -12:

- All three builds of 0.2.0 read a table as one paragraph (one garbled finding for a table alone; nothing at all for a table right after a paragraph, whose weak modal goes unreported).
- Of the libraries, only commonmark-java has a table extension (`commonmark-ext-gfm-tables`, at the facade's 0.31.0); commonmark.js has none and no extension mechanism; natively only GitHub's cmark-gfm fork does, a different library on an older CommonMark (0.29). Using them would mean three table implementations and a changed native dependency, so tables are recognized in shared code.
- GitHub's spec (github.github.com/gfm, "Tables (extension)"): the header row must match the delimiter row in cells, or there's no table; a body row with fewer cells gets empty ones, one with more has the excess ignored; `\|` includes a pipe in a cell, inside other inline spans too; a table ends at a blank line or another block.
- GitHub's renderer (`gh api markdown`, mode gfm): a table directly after a paragraph line interrupts it (the paragraph ends, the table follows); a bare pipe inside inline code splits the cell (`` `a|b` `` leaves "…reject `a" and an excess cell); `&#124;` stays in its cell; a body row's extra cells aren't shown; lines with pipes and no delimiter row are a paragraph.
- Every parser's break piece is `Piece(" ", false, None)`. On Node and Scala Native every other piece has a line (`SourceLines` and `CmarkLines` give one even to text they can't place); on the JVM a piece's line comes from commonmark-java's inline source spans.
- `docs/json-output.md`, while the version is 1: new fields can appear without a new version, and consumers ignore fields they don't know.
- Container markers (`- `, `* `, `+ `, `1. `, `> `) contain no pipe, so a paragraph line's pipes in the original source are exactly its row's pipes, wherever the paragraph sits.

## Goals / Non-Goals

**Goals:**

- Each requirement in a table is checked on its own, on its row's line, the same on every platform.
- Cells split where GitHub splits them, escaped pipes and character references included, in the common case.
- A row whose extra cells GitHub hides is reported where the user looks: beside the findings, in both output formats, and in the log.

**Non-Goals:**

- GitHub's exact result in the rare cases the pieces can't tell (decision 2's fallback): a pipe in a link destination or an HTML tag, or a character reference inside inline code, on a table row. The extra-cells warning flags most of them.
- A broken inline code span's look: GitHub shows a stray backtick after a bare pipe splits `` `a|b` ``; the reader keeps the first part as code. Only the cell boundaries matter for checking.
- Alignment (`:---:`) and any rendering: the reader only reads text.
- A switch to fail on warnings: easy to add if someone asks.

## Decisions

### 1. Tables in shared code, from the pieces

A new `private[markdown] object Tables` (shared) turns a paragraph into blocks: the paragraph as it is, or the lines before a table (a paragraph) and the table (header, body rows). It splits the paragraph's pieces into lines at break pieces, and looks for the first line followed by a delimiter row with as many cells; every later line of the paragraph is a body row (GitHub reads a line without pipes after a table as a one-cell row too). A delimiter row is a line of cells each `:?-+:?` with optional spaces, separated by pipes, with optional outer pipes, and at least one pipe. Each cell becomes its own `Paragraph(rowLine, cellPieces)` for `Extraction.sentences`, which already ends a sentence at the paragraph's end. Cells are trimmed of surrounding whitespace, and an empty first or last cell from an outer pipe is dropped, as GitHub does.

_Alternative considered:_ the paragraph's original source lines (option B). They show escapes directly, but inside list items and block quotes Node and cmark don't separate container markers from continuation lines, so shared code would need its own marker rules, three ways to disagree. The owner chose the pieces (2026-11-12).

### 2. Which pipes separate cells

The parsed text has lost what separates `\|` and `&#124;` from a real separator: all become `|`. But the original line is available (`Markdown.sentences` has the content), and its markers hold no pipe. So for each row, the reader lists the original line's pipe-producing tokens in order: `|` (escaped when an odd number of backslashes precedes it) and the character references that decode to a pipe (`&#124;`, `&#x7c;` in either case and with leading zeros, `&vert;`, `&verbar;`, `&VerticalLine;`). When their count equals the parsed row's pipes (in text pieces, plus each `|` or `\|` in code pieces), the n-th of one is the n-th of the other, and only the unescaped `|`s separate cells. Inside a code piece, `\|` reads as `|` in the cell, as GitHub shows it.

When the counts differ (a pipe in a link destination or HTML tag, which the parsers drop, or a reference inside inline code, which they don't decode), every parsed `|` separates except a `\|` in a code piece. That can split a cell GitHub keeps whole; the extra-cells warning then usually says so.

The tokenizer is pure and shared, so it's measured by scoverage and tested directly, with properties for the alignment (a row's pipes round-trip) besides the fixtures.

### 3. The facade returns warnings beside sentences

`Markdown.read(content): Either[String, MarkdownText]`, with `final case class MarkdownText(sentences: List[Sentence], warnings: List[MarkdownWarning])` and `enum MarkdownWarning { case ExtraCells(line: Int, cells: Int, headerCells: Int) }`, plain data in the facade (no messages: wording belongs to the CLI). `Markdown.sentences` stays, as `read(...).map(_.sentences)`, so existing callers and tests don't change. Original lines are split as the Node parser already does (`\r\n`, `\r`, `\n`); that helper moves to shared code for both uses.

### 4. Warnings in the CLI

`MarkdownInput` returns the candidates and the warnings; `Checker`'s outcome carries warnings beside findings, with their `Location.Line`; the exit status is computed from findings as now. Text output merges an input's findings and warnings by line (a finding first on a tie) and writes a warning as `<file>:<line>: warning: table-extra-cells: This table row has <n> cells and its header <m>, so GitHub doesn't show the last <n-m>.`; the location goes through the same escaping as findings'. JSON output adds `"warnings": [...]` after `"findings"`, each `{"location": …, "kind": "table-extra-cells", "message": …}`, empty when there are none. The effect code logs each warning at WARN with the input, line, kind, and message (a logging test asserts that, under the global rule's exception for tests of the logging itself).

### 5. Tests and fixtures

- **Conformance fixtures** (all three platforms), one per spec scenario: a table with and without outer pipes, after a paragraph line, pipes with no delimiter row, a delimiter row with a different count, two requirements in a row, a short row, an escaped pipe in text and in inline code, `&#124;`, a bare pipe in inline code, and a row with extra cells (its sentences and its warning). Each fixture's expected result is written by hand and agrees with GitHub's renderer, checked when the fixture is written.
- **Unit tests** for the pipe tokenizer and the alignment fallback, with a property that a row built from cells (escaping each pipe in them) splits back into those cells.
- **CLI tests**: the warning line in text output, the `warnings` array (and its empty form), line order with findings, exit status 0 for a warning alone, and the log entry.
- **`scripts/cli-cases`**: a Markdown table case in text and JSON, with a finding and an extra-cells row, compared across the builds.

## Risks / Trade-offs

- **The fallback can split differently from GitHub** in the rare cases of decision 2; the warning flags most, and a fixture records one.
- **A line with no line of its own on the JVM** would break the line split (decision 1 relies on `None` meaning a break). commonmark-java gives inline spans with `BLOCKS_AND_INLINES`; a fixture with escapes and references on every row checks it on the JVM, and if a piece ever lacks a line, the break test also requires the piece to be the single space.
- **Output contract**: text output gains a line kind, and JSON a field. Both are documented; consumers of JSON ignore unknown fields by the document's rules, and the text format's findings keep their form.
- **Unverified assumptions:** that commonmark-java gives every text and code piece a line (above); that commonmark.js and cmark decode `&#124;` and the named references to `|` in text, as CommonMark requires; that every parser keeps `\|` literally in a code span. All three are checked by the fixtures in task 1, before anything is built on them. Checked today: the GitHub behaviors, the libraries' table support, the JSON stability rules, the break pieces' form.
