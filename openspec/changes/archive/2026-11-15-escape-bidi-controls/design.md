# Design

## Context

See proposal.md for why. Observations (2026-11-14, the jar, the Node package, and the native binary built from 610334c, and the source):

- A plain-text requirement with a missing comma holding `[RLO] [LRI] [RLM]` (U+202E, U+2066, U+200F) gives a fix with the bytes `[e2 80 ae] [e2 81 a6] [e2 80 8f]` from all three builds in text output, and the same bytes in the jar's JSON output, in both `requirement` and `fix`.
- `Visible.escape` escapes ISO controls, U+2028, U+2029, and lone surrogates as `\uXXXX`. Text output's findings (`Checker.singleLine`, a finding's message, a warning's message), the messages on standard error (`Checker.scala:47`, and decline's errors in `Settings`), and the whole JSON document (`Checker.report`) go through it.
- None of the bidirectional formatting characters is an ISO control or whitespace, so none is changed today on any build.
- Log lines escape through `Logging.escape`, a separate function (roadmap, Later: "One predicate for which characters are escaped"). It writes a backslash as `\\`, a line feed and a carriage return as `\n` and `\r`, and every other ISO control, U+2028, and U+2029 as `\uXXXX`, one character at a time; a bidirectional character or a lone surrogate passes through (checked 2026-11-15 in the source). A log entry holds no requirement text: its details are the input's name, the arguments (as JSON) when they're invalid, the platform's error (which often repeats the file name), and the parser's reason, so a bidirectional character reaches one through a file name or an argument.
- No `ears-cli` requirement says which characters a log entry escapes: "Log line format" covers line breaks only, and "Control characters in messages" excludes log entries. `LoggingSuite`'s properties use `TerminalText`, which holds no bidirectional character or surrogate, so they stay true when log lines escape these as well.
- Tests: `TerminalText.acted` and its generators (`controls`, `characters`, `texts`) hold no bidirectional character, so the existing properties of `VisibleSuite` and `CheckerSuite` stay true when `Visible` escapes them as well (none asserts that a character outside `acted` reaches the output).

## Goals / Non-Goals

**Goals:**

- None of the nine explicit bidirectional formatting characters raw in text output, messages, JSON output, or log lines, on any build.
- One predicate for which characters are escaped, shared by output and log lines.

**Non-Goals:**

- The implicit marks: LRM (U+200E), RLM (U+200F), and ALM (U+061C).
- The other invisible format characters (zero-width characters, U+FEFF, U+206A to U+206F): roadmap item 8, escaping by Unicode category.

## Decisions

### 1. The nine explicit formatting characters

- `Visible.escape` also writes U+202A to U+202E (LRE, RLE, PDF, LRO, RLO) and U+2066 to U+2069 (LRI, RLI, FSI, PDI) as `\uXXXX`. These are the characters "Trojan Source" uses: each opens or closes an embedding, override, or isolate, which reorders the text after it until it's closed.
- _Alternative: also the implicit marks_: they appear in ordinary right-to-left text, and a mark changes only how neutral characters next to it are ordered, not a whole run; escaping them would garble legitimate Arabic or Hebrew requirements in every fix. Rejected.

### 2. JSON output too

- JSON output already goes through `Visible.escape` whole (escape-json-output), so it escapes them with no further code. In JSON `\u202e` is the same character, so a parser reads the same text and the format version stays 1, and a viewer of a saved document (an editor, a web page) shows the escape rather than reordering the line.
- _Alternative: text output and messages only_: it would mean a second escape function for JSON, two rules where escape-json-output made one, for no gain to a program reading the document. Rejected.

### 3. Tests write the characters by code point

- Test sources build each bidirectional character from its code point (`0x202e.toChar`) and `scripts/cli-cases` writes it with `printf` octal bytes, so no source file holds the raw character (a Trojan Source hazard of its own, and invisible in review). Expected escapes are written with a doubled backslash in Scala (`"\\u202e"`).

### 4. Log lines share `Visible`'s predicate

- `Visible.escaped(text, i)`, which says whether the code unit at `i` is escaped (an ISO control, U+2028, U+2029, one of the nine, or a lone surrogate), becomes `private[cli]`, and `Logging.escape` asks it for each position after its own cases: a backslash is `\\`, a line feed `\n`, a carriage return `\r`, an escaped character `\uXXXX`, and anything else stays. `Logging.escape` walks positions rather than characters so a surrogate pair stays whole. Its `separatesLines` goes. Folded in on the owner's word after the code review (2026-11-15), which found log lines still writing the nine raw.
- _Alternative: a separate change in the theme_: the same code, with this change's own stderr scenario ("standard error contains no U+202E") untrue until then. Rejected by the owner.
- _Alternative: a new shared object_: one predicate has one home already, the object whose job is what a terminal sees; a second object would hold one function. Rejected.

## Risks / Trade-offs

- [Log lines change for any input name or argument holding one of the nine or a lone surrogate] → They were raw, or `?` and U+FFFD by platform; now `\uXXXX`, the form log lines already use. No requirement or test depended on the old bytes.

- [A right-to-left requirement that uses an embedding or isolate on purpose shows `\u2067` in its fix] → The escape shows where the character is; copied back into a plain-text or Markdown file it's six characters, not the character, so the author puts the character back by hand (an OpenSpec JSON file decodes it, since it's a JSON escape). The implicit marks, which ordinary right-to-left text uses most, are left alone.

## Assumptions checked and unchecked

Checked on 2026-11-14 (the log-line ones on 2026-11-15): every observation above. From memory, not rechecked: that the Trojan Source paper (Boucher and Anderson, 2021) lists these nine characters; the plan doesn't depend on it, since the set is Unicode's explicit formatting characters (UAX #9).
