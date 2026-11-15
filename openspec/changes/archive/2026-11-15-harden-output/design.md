# Design

## Context

See proposal.md for why. Observations (2026-11-15):

- `Visible.escaped(text, i)` decides what text output, messages, JSON output (the whole document goes through `Visible.escape`), and log lines (`Logging.escape`, after its own `\\`, `\n`, and `\r`) escape: ISO controls, U+2028, U+2029, U+202A to U+202E and U+2066 to U+2069, and lone surrogates (`lone`). Each escaped code unit becomes `\uXXXX`.
- Every text-output line starts with its location (`Checker.scala:156` and `:160`): a file name, `<stdin>`, or an OpenSpec change or spec id from the JSON. Messages on standard error start with the checker's own words (`Cannot read`, `Cannot parse`, the usage text), log lines with a timestamp, and the JSON document with `{`.
- GitHub's runner (`actions/runner`, `src/Runner.Common/ActionCommand.cs` and `src/Runner.Worker/ActionCommandManager.cs` on `main`, read through `gh api`): `TryParseV2` trims a line's leading white space (`TrimStart`, .NET's Unicode white space) and takes a line starting with `::` as a command; `TryParse` looks for `##[` anywhere in a line (`IndexOf`). The registered commands include `add-mask`, `stop-commands`, `set-output`, `save-state`, `add-matcher`, `warning`, `error`, `notice`, `group`, `debug`, and `echo`; `set-env` and `add-path` run only when unsecure commands are allowed.
- The platforms' Unicode tables differ (a probe of `Character.getType`): JDK 27 follows Unicode 17.0, Scala.js 1.22.0 Unicode 15, and Scala Native 0.5.12 Unicode 6.0 to 6.2, without even the isolates. An allow-list of characters assigned decades ago needs no table from any of them.
- Existing tests that contradict the change (all in `ears/cli/test/src`):
  - `VisibleSuite`:
    - "text without such characters is unchanged": its text comes from `TerminalText.characters`, whose ASCII range can put two `#` side by side.
    - "a surrogate pair stays as it is".
    - "text of surrogate pairs and characters left alone is unchanged": its pairs, and its plain text can hold `##`.
    - "the implicit marks LRM, RLM, and ALM stay as they are".
  - `LoggingSuite`: "a lone surrogate is written as `\ud83d`, and a surrogate pair stays", the pair half.

  `CheckerSuite`'s "a span counts UTF-16 code units: an emoji counts as two" checks the span only, which doesn't change. Every other property stays true: `TerminalText`'s characters are controls, the separators, ASCII, `é`, and U+00A0.

## Goals / Non-Goals

**Goals:**

- No output a crafted input can make show other text than it holds: nothing outside the English character set reaches text output, messages, JSON output, or log lines unescaped, with the same bytes from every build.
- No output a CI system reads as a command: no `##` in any output, and no text-output line starting with white space or a colon.

**Non-Goals:**

- Rejecting input that holds other characters: the checker still checks it, and only its output escapes them.
- Escaping for HTML or Markdown: a program rendering the output does that (the docs say so).
- A size limit per input (roadmap item 9) and fuzzing cmark-gfm (roadmap, Later).

## Decisions

### 1. An allow-list: the English character set

- Kept as they are: printable ASCII (U+0020 to U+007E); U+00A0 to U+00FF other than the soft hyphen U+00AD (the no-break space, accented letters, `°`, `£`, `×`, `«`); U+0300 to U+036F (the combining accents, which spell `é` as `e` and an accent in decomposed text, as some macOS tools write file names; the code review of 2026-11-15 found them, and the owner added them: stacked accents can blur a letter but can't hide or reorder text); U+2010 to U+2022 (hyphens and dashes, curly quotes, daggers, the bullet); U+2026 (ellipsis); U+2030 (per mille); U+2032 and U+2033 (primes); U+2039 and U+203A (single angle quotes); U+20AC (euro); U+2122 (trade mark). Every other UTF-16 code unit is escaped. That's what English prose holds once a word processor has had it, and nothing in it is invisible but the no-break space, which shows as a space.
- _Alternative: by Unicode category_ (escape-by-category, planned and dropped on 2026-11-15): a table of 21 ranges tied to Unicode 17.0, five exceptions, a JDK tripwire test, and look-alike letters from other scripts still raw. Rejected by the owner as too complex.
- _Alternative: printable ASCII only_: one comparison, but `café`, curly quotes, and dashes in every fix would come back as escapes. Rejected.

### 2. Every unit on its own

- `escaped(text, i)` looks at the code unit at `i`; a character above U+FFFF is two units, both outside the set, so an emoji is `\ud83d\ude00`, its JSON escape. A lone surrogate is escaped the same way, so `lone` and the bidirectional predicate go: the set covers both.

### 3. Two number signs

- The first `#` of two adjacent ones is escaped (`\u0023#`), wherever it is, in all four outputs (`escaped` looks one unit ahead). That breaks GitHub's `##[`, which the runner finds anywhere in a line, and, from memory, Azure Pipelines' `##vso[` and TeamCity's `##teamcity[`. A lone `#` stays, so Markdown-ish text and issue numbers read as before.
- _Alternative: escape `##[` only_: just GitHub's form. Rejected.

### 4. A text-output line's first character

- `Checker` escapes a text-output line's first character when it's a space, a no-break space, or a colon: the only white space the set keeps is those two (`TrimStart` would skip them), and a colon is how a workflow command starts. A location is escaped as it is, its spaces kept: it no longer goes through `singleLine`, which turned each whitespace run into one space and dropped the ends, so a file named with a leading space was reported under another file's name (the code review of 2026-11-15; the owner chose to fix it here). A tab or line break in a location is escaped like any other control character, so the line stays one line. After escaping, a line starts with a backslash or another printable character, never `::`. Only text output needs it: every other output starts with the checker's own text.
- _Alternative: start every line with a fixed word_: it would break the `file:line:` form editors and CI problem matchers read. Rejected.

### 5. Tests write characters by code point

- As before: test sources build every character outside ASCII and Latin-1 from its code point, `scripts/cli-cases` writes them as `printf` octal bytes, and expected escapes are written with a doubled backslash in Scala.

## Risks / Trade-offs

- [Emoji, Greek letters, math symbols such as `≤`, and every non-Latin script show as escapes in text output, messages, and log lines; a fix copied back into a plain-text or Markdown file holds the escapes] → The checker reads English only; JSON output decodes to the same text.
- [The marks escape-bidi-controls kept (LRM, RLM, ALM) are now escaped] → Right-to-left text is outside what the checker reads.
- [A file name or id that really starts with a space or colon shows `\u0020` or `\u003a` at the start of its line] → Rare, and the alternative is a forged command.
- [Other CI systems' command forms beyond these three] → `##` and a leading colon or white space cover the ones known here; the docs tell consumers that output repeats input.

## Assumptions checked and unchecked

Checked on 2026-11-15: every observation above, including the runner's two parsers in its source. From memory, not checked: Azure Pipelines' `##vso[` and TeamCity's `##teamcity[` forms, and whether either is found anywhere in a line. The rule covers them either way, since it removes every `##`.
