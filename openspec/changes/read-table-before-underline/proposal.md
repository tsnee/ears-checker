# Proposal

## Why

A line of `-` or `=` characters directly under a table (no blank line between) makes the CommonMark parsers read the whole block as a setext heading: the table, and any paragraph lines before it. Headings aren't read, so none of the block's requirements are checked. GitHub never makes a heading of a block that holds a table: it shows the table, and reads the underline as the table's next line. It's the last known difference from GitHub that loses requirements (`read-markdown-tables` left it for this change; the README lists it).

## What Changes

- **A table followed directly by a line of `-` or `=` is read as GitHub reads it.** The line is read as the table's next line, as any other line after a table is. Observed on GitHub's renderer on 2026-11-12:
  - A thematic break (`---`, `- - -`, `-- -`, with up to three spaces of indent or trailing spaces) ends the table and is shown as a rule.
  - `-` alone ends the table and starts an empty list item; `    ---` (four spaces) ends it as indented code.
  - Any other underline (`===`, `=`, `==`, `--`) is a body row, its first cell holding the line.
  - The same holds in a block quote and in a list item, and for a table with a header row and no body rows.
- **Paragraph lines before such a table are read as a paragraph**, as they are before any table.
- **A block of underlined lines with no table in it stays a heading**, as GitHub shows it, and isn't read: for example a one-column `| text |` line over `---`, which has no delimiter row.
- **Docs**: the README's known difference about `---` and `===` under a table goes, since it's fixed (it also misdescribed `===`, which GitHub shows as a row, not a rule), and the theme's changelog entry no longer needs it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `markdown-input`: two added requirements: a line of `-` or `=` directly after a table's row is read as the table's next line, and a block of underlined lines with no table is a heading, with nothing read.

## Impact

- The three platform parsers (`ears/markdown/src-jvm`, `js/src`, `native/src`): each also gives setext headings, marked as such, with their lines and pieces, as it gives paragraphs.
- `ears/markdown/src` (shared): `Tables` reads a marked heading that holds a table as it reads a paragraph, and drops one that doesn't; `Rows` learns that a thematic break ends a table.
- Tests: conformance fixtures for each case, each checked against GitHub's renderer, on the JVM, Node, and Scala Native; unit tests for the thematic break and for headings in `Tables`.
- Docs: README, CHANGELOG (the Unreleased tables entry), roadmap, work log.
- No change to the CLI, its output, or JSON output.
