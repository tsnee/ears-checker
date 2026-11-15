# Proposal

## Why

Text output, messages, and JSON output write Unicode's bidirectional formatting characters from the input as they are (all three builds on 2026-11-14, from a plain-text requirement holding RLO, LRI, and RLM: the bytes `e2 80 ae`, `e2 81 a6`, and `e2 80 8f` in the fix and in JSON output's `requirement` and `fix`). They aren't control characters, so `Visible.escape` leaves them alone. A requirement or file name carrying the explicit embeddings, overrides, and isolates (U+202A to U+202E, U+2066 to U+2069) can make a finding display in a different order than it's written ("Trojan Source", CVE-2021-42574). Few terminals act on them; editors and web views of saved output do.

Roadmap item 7, in the "CLI messages and output" theme, followed by item 8, escaping by Unicode category (a non-goal of escape-text-output, 2026-11-11).

## What Changes

- Text output, the messages on standard error, and JSON output write each of the nine explicit bidirectional formatting characters (U+202A to U+202E, U+2066 to U+2069) as `\uXXXX`, as they already write control characters, separators, and lone surrogates. In JSON that's an escape for the same character, so the document's content and its format version (1) are unchanged.
- The implicit marks (U+200E, U+200F, U+061C) stay as they are: they're ordinary in right-to-left text and can't reorder a run by themselves.
- Log lines escape the same characters as output: `Visible` and `Logging` share one predicate for which characters are escaped (roadmap, Later: "One predicate for which characters are escaped", folded in on the owner's word after this change's code review, 2026-11-15). A log entry's file name, arguments, or platform error then can't reorder the line in a CI log viewer, and a lone surrogate in one is `\uXXXX` from every build. Each function keeps its own form for everything else: a log line still doubles backslashes and writes line feeds and carriage returns as `\n` and `\r`, and output leaves backslashes alone.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: "Control characters in text output", "Control characters in messages", and "Control characters in JSON output" add the bidirectional formatting characters, each with a scenario; a new requirement, "Control characters in log lines", says which characters a log entry escapes, which no requirement said before ("Log line format" covers line breaks only).

## Impact

- `ears/cli`: `Visible.escape` escapes the nine characters too; JSON output already goes through it. `Logging.escape` uses `Visible`'s predicate, so it also escapes them and lone surrogates.
- Tests: new tests for `Visible`, for both outputs, and for log lines; `scripts/cli-cases` cases compare the builds' bytes. `docs/json-output.md`, CHANGELOG, and the roadmap ("One predicate" done).
