# Proposal

## Why

JSON output writes some characters from the input as they are, and one of them differently on each platform (all three builds on 2026-11-14, from an OpenSpec requirement holding `\ud83d`, `\u009b`, `\u007f`, and `\u2028`):

- The C1 control U+009B (CSI), DEL, and U+2028 reach the document raw (bytes `c2 9b`, `7f`, `e2 80 a8`): zio-json escapes only the C0 controls. Few terminals act on a UTF-8 C1 control, and the document is for tools, but a raw line separator splits it for a line-based tool, and escaping costs a parser nothing (`\u2028` and the raw character are the same JSON string). Found by the security review on 2026-11-11.
- A lone surrogate (an unpaired UTF-16 code unit, which JSON's `\ud83d` decodes to) is written as `?` by the jar and the native binary and as U+FFFD (`ef bf bd`) by Node, so the builds' JSON differs.
- Text output has the same lone-surrogate difference: a finding's fix quoting `[\ud83d]` shows `[?]` from the jar and `[�]` from Node. It already escapes the controls and separators.

Roadmap item 6, the third change of the "CLI messages and output" theme.

## What Changes

- JSON output writes each ISO control character, U+2028, U+2029, and lone surrogate in its strings as a JSON escape (`\u009b`, `\u2028`, `\ud83d`), the same on every build. The document's content is unchanged (each escape decodes to the same character), so its format version stays 1.
- Text output, and the messages on standard error, write a lone surrogate as `\uXXXX` too, as they already write the controls and separators.
- Log lines are unchanged: they escape the same controls through their own function, and unifying the two is a separate roadmap item.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: a requirement for these characters in JSON output, and lone surrogates added to "Control characters in text output".

## Impact

- `ears/cli`: `Visible.escape` also escapes lone surrogates, and `Checker.report` passes the JSON document through it.
- Tests: new tests for `Visible` and for both outputs; a `scripts/cli-cases` case compares the three builds' bytes. `docs/json-output.md` and CHANGELOG.
