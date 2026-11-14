# Design

## Context

See proposal.md for why. Observations (2026-11-14, the jar built from 1c1dbbf):

- `OpenSpecInput.candidates` writes the whole message, `Not OpenSpec change or spec JSON: <zio-json's detail>`, without the input's name, which it isn't given. For `{not json` the detail is `(expected '"' got 'n')`, from a file and from standard input alike.
- `Checker.read(settings, content)` chooses the reader by format, and gives the plain-text and Markdown readers `settings.sourceName`: the file, or `<stdin>`, the name findings show (`<stdin>:3: …`).
- The read errors name standard input `standard input`: `Cli.read` spells it out (`settings.file.getOrElse("standard input")`) and `ReadFailure.describe` writes `Cannot read <source>…`.
- `Cli.load` already logs a parse failure as `Cannot parse input`, with the message as its `reason` and the input's `file` among the entry's details; the message is written to standard error by `Checker.outcome`, escaped by `Visible.escape`.
- Tests: `CheckerSuite`'s "exit status 2 and a message on standard error for malformed JSON" and `MainSuite`'s "an unparsable file before a missing one" check that the message starts with `Not OpenSpec change or spec JSON:`; `ExtractSuite` checks only that `Candidate.fromOpenSpec` fails (`isLeft`). `scripts/cli-cases` has error cases with exit status 2 (`invalid-utf-8`, `utf-16`), compared across the jar, Node, and the native binary.

## Goals / Non-Goals

**Goals:**

- Every message about an input names it the same way: the file as given, or `standard input`.

**Non-Goals:**

- Rewording zio-json's detail, which says what's wrong with the JSON.
- Plain text and Markdown, which can't fail to parse: every UTF-8 text is both.

## Decisions

### 1. The reader gives the detail; a parse failure words the message

- `OpenSpecInput.candidates` returns zio-json's detail alone as its failure. `Checker.parse` gives a `ParseFailure` (`NotOpenSpec(detail)`, or `UnreadMarkdown(detail)` when cmark-gfm itself fails), which, like `ReadFailure`, has `describe(source)`, the message for standard error (`Cannot parse <input>: not OpenSpec change or spec JSON: <detail>`, or `Cannot parse <input>: <detail>`), and `reason`, the parser's account alone, which the log entry carries beside its `file` detail. `Checker.read` is `parse` described with the input's name. The reader stays about JSON; the message's form sits beside the read errors'. (Revised after the code review, 2026-11-14: the first version wrote the message inline in `Checker.read` and logged the whole message as the reason. A Markdown parse failure, which only an out-of-memory cmark-gfm gives, also didn't name its input, which the same requirement asks; it now does.)
- _Alternative: pass the name into the reader_: it would then know about messages and names, which the plain-text and Markdown readers use only for locations.

### 3. The shape is chosen by `deltas`

- The reader tried the change's shape, then the spec's, and reported the spec parser's account when both failed, so a broken change (a delta without its `operation`, say) was reported as `.requirements(missing)`, a field changes never have. Now an object with `deltas` is parsed as a change, and anything else as a spec, so the account is about the shape the input was meant to have (`.deltas[0].operation(missing)`). The text is parsed as that shape directly: parsed through zio-json's generic tree, malformed JSON's account is `(expected string)`, where the direct parse says `(expected '"' got 'n')`. (Found by the code review, 2026-11-14; the behavior predates this change, but the message now presents the account as the reason.)

### 2. One name for an input in messages

- A `Settings` extension, `messageName` (the file, or `standard input`), used by `Cli.read` for the read errors and by `Checker.read` for the parse error, so both say the same; `sourceName` (`<stdin>`) stays the name in findings' locations, which is unchanged.

## Risks / Trade-offs

- [Anything matching the old message's start breaks] → It's an error message for people, not a documented format; the CHANGELOG says what changed.

## Assumptions checked and unchecked

Checked on 2026-11-14: every observation above. Not checked: that Node and the native binary write zio-json's detail the same way as the jar; the new `scripts/cli-cases` case compares them, and a difference would show there first.
