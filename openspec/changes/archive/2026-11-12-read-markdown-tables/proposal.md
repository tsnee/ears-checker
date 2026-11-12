# Proposal

## Why

None of the parsers the Markdown reader uses recognizes GitHub-style tables, so a table is read as one paragraph: its rows run together into one long "sentence" that gives a misleading finding, the second and later requirements in it are never checked, and a table right after a paragraph merges into that paragraph, so its requirements go unchecked (only sentences with "shall" are read from Markdown, and the merged text's sentence boundaries hide them). Requirements are often written in tables (ID, requirement, rationale), so these are exactly the sentences users want checked. Seen on all three builds of 0.2.0 on 2026-11-11.

## What Changes

- **Tables are recognized by GitHub's rules**, the same on the JVM, Node, and Scala Native: a header row followed by a delimiter row (`|---|---|`) with the same number of cells starts a table, which may interrupt a paragraph, and ends where GitHub ends it (the rest of the paragraph is read as the Markdown it is). Lines with pipes but no matching delimiter row stay ordinary text.
- **Each cell is read as text of its own**, on its row's line: a sentence ends at the cell's end, so every requirement in a table is checked separately.
- **Cells split as GitHub splits them**: at every pipe, inside inline code too, except a backslash-escaped pipe (`\|`) or a pipe written as a character reference (`&#124;`), which stay in their cell. A body row with fewer cells than its header is fine.
- **A body row with more cells than its header is a warning.** GitHub doesn't show the extra cells, so their text is silently hidden from readers; the reader leaves them unread, as GitHub does, and says so.
- **Warnings appear beside the findings**: a `file:line: warning: table-extra-cells: …` line in text output, and a new `warnings` array in JSON output. They're also logged, and they don't change the exit status. Adding the field keeps JSON output at format version 1 (`docs/json-output.md`'s stability rules allow new fields).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `markdown-input`: sentences are read from table cells as well as paragraphs (the first requirement, modified); new requirements recognize tables, split cells, and return a warning for a row with extra cells.
- `ears-cli`: new requirements write warnings in text and JSON output and in the log, and keep them out of the exit status.

## Impact

- `ears/markdown/src` (shared): table recognition and cell splitting, from the paragraph's raw lines, with each cell re-parsed as inline text; the facade returns sentences and warnings. Each of the three platform parsers also reports a paragraph's last line.
- `ears/cli/src` (shared): warnings carried from the Markdown input to the report, written in both output formats, and logged.
- Tests: conformance fixtures for each table case, run on all three platforms; CLI tests for the warning in both formats and the exit status; a `scripts/cli-cases` case with a table.
- Docs: `docs/json-output.md` (the `warnings` field), README (what the Markdown reader reads), CHANGELOG.
