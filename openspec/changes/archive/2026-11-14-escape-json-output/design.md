# Design

## Context

See proposal.md for why. Observations (2026-11-14, the jar, the Node package, and the native binary built from d227713, and the source):

- An OpenSpec spec whose requirement holds `[\ud83d] [\u009b] [\u007f] [\u2028]` gives, with `--output json`, the bytes `[3f] [c2 9b] [7f] [e2 80 a8]` from the jar and the native binary and `[ef bf bd] [c2 9b] [7f] [e2 80 a8]` from Node. With text output and a finding whose fix quotes the requirement (`wrong-case` under the OpenSpec profile), the fix shows `[?] [\u009b] [\u2028]` from the jar and `[ef bf bd] [\u009b] [\u2028]` from Node.
- `Checker.report` writes JSON with zio-json's `Json.toJson`, which escapes the C0 controls (as `\n`, `\u0000`, and so on) and nothing else, then appends one line feed. Every character the document has outside its strings is printable ASCII.
- `Visible.escape` writes each ISO control (C0, DEL, C1), U+2028, and U+2029 as `\uXXXX` in lowercase hex, and is used for text output's findings and for the messages on standard error. It treats a lone surrogate as an ordinary character, which each platform's UTF-8 encoder then replaces on output (the JVM and Scala Native with `?`, Node with U+FFFD).
- A lone surrogate reaches the checker only through a JSON escape in OpenSpec input: plain text and Markdown are decoded strictly as UTF-8, and a file name on macOS or Linux is bytes the platforms decode without producing one.
- Log lines escape the same controls through `Logging.escape`, a separate function (roadmap, Later: "One predicate for which characters are escaped").
- Tests: `VisibleSuite`, `CheckerSuite`'s "every document is one line…" property and its "characters a terminal acts on" suite, and `TerminalText`'s generators, none expecting a raw character in JSON output or a lone surrogate anywhere.

## Goals / Non-Goals

**Goals:**

- No ISO control, line or paragraph separator, or lone surrogate raw in JSON or text output, and the same bytes from every build.

**Non-Goals:**

- Log lines (a separate function and a separate roadmap item).
- Bidirectional formatting characters: roadmap item 7, the theme's next change.

## Decisions

### 1. One escape for both outputs

- `Visible.escape` also writes a lone surrogate (a high surrogate not followed by a low one, or a low one not after a high one) as `\uXXXX`; a valid pair stays as it is, so emoji are untouched.
- `Checker.report` passes the JSON document's text through `Visible.escape`. Only characters inside strings can be escaped (everything outside is printable ASCII, and zio-json leaves no raw C0), and in a JSON string `\uXXXX` is the same character, so the document means exactly what it did; its format version stays 1. For text output, `\uXXXX` is what the controls already show.
- _Alternative: a JSON encoder that escapes these itself_: zio-json has no option for it, and a second escaping rule would differ from text output's.

## Risks / Trade-offs

- [A tool comparing JSON output byte for byte with an older run] → The content is the same string; only its spelling changes, for input that holds these characters.

## Assumptions checked and unchecked

Checked on 2026-11-14: every observation above. Not checked: that each JSON parser a user might run decodes `\ud83d` alone to a lone surrogate rather than rejecting it; JSON (RFC 8259) allows it, and the case in `scripts/cli-cases` checks the three builds agree.
