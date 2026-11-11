# Proposal

## Why

The whole-repository security review before 0.2.0 (2026-11-11) found that text output prints
control characters from the input as they are. A requirement in a cloned repository can carry
terminal escape sequences into a `Fix:` sentence, and file names and OpenSpec change,
capability, and requirement names can carry them too: they can erase or forge earlier findings,
retitle the window, plant a link that points elsewhere, or, in some terminals, write the
clipboard. NEL (U+0085) and U+2028 break text output's one line per finding. The plain messages
on standard error (`Cannot read <input>`) print file names the same way. SECURITY.md puts such
escaping problems in scope, and the log lines on standard error are already escaped against
them.

## What Changes

- Text output writes each ISO control character, U+2028, and U+2029 that a finding takes from
  the input as a visible `\uXXXX` (lowercase hex, four digits), as the log lines already do. A
  whitespace run of space, tab, line feed, vertical tab, form feed, and carriage return still
  shows as one space, the same on every build (the native binary used to keep a vertical tab).
- The plain messages on standard error escape the same characters, line feeds included, so a file
  name can't add a line; argument errors keep their usage text's own lines.
- JSON output and log lines don't change: both already escape control characters.
- Backslashes stay as they are in text output, so a `Fix:` sentence can still be copied (design).

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `ears-cli`: control characters from the input are escaped in text output and in messages on
  standard error.

## Impact

- `ears/cli/src` (the text report and the messages), `ears/cli/test/src`.
- SECURITY.md, CHANGELOG.md (Fixed), the roadmap, the work log.
- Every build (JVM, Node, native) shares the code; `scripts/cli-cases` gains a case.
