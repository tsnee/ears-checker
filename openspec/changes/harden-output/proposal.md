# Proposal

## Why

A crafted requirement, file name, or OpenSpec id can still do two kinds of harm through the checker's output (observed 2026-11-15):

- **Hide or disguise text.** `Visible.escaped` escapes a hand-picked set (controls, U+2028, U+2029, the nine explicit bidirectional characters, lone surrogates), so zero-width characters, the soft hyphen, tag characters (which can carry hidden ASCII), and look-alike letters from other scripts (a Cyrillic small letter a, U+0430, for a Latin `a`) pass through raw. Two findings can look identical while differing, and a fix can hide text.
- **Inject CI commands.** In a GitHub Actions log, the runner reads a line that starts with `::` (after leading whitespace) as a workflow command, and `##[command]` anywhere in a line as one too (`actions/runner`, read 2026-11-15). Every text-output line starts with its location, taken from the input (a file name, or an OpenSpec change or spec id), and a fix, a message, or a log line repeats input text. A pull request could forge annotations, mask log text, switch off command processing, or set the step's outputs (`set-output`), which a later step that used them in a script would run.

The checker reads English only (README), and the owner chose on 2026-11-15 to escape everything outside the characters English text uses, rather than a rule by Unicode category, which needed exceptions and a table tied to a Unicode version.

Roadmap item 8, the last change of the "CLI messages and output" theme.

## What Changes

- Text output, messages, JSON output, and log lines keep only the English character set as it is: printable ASCII, the Latin-1 letters and symbols (U+00A0 to U+00FF, without the soft hyphen U+00AD), the combining accents (U+0300 to U+036F, which decomposed text spells accented letters with), and the typographic punctuation word processors insert (dashes, curly quotes, daggers and the bullet, the ellipsis, per mille, primes, single angle quotes, the euro sign, and the trade mark sign). Every other UTF-16 code unit is written as `\uXXXX`: a character above U+FFFF (an emoji) as its two units, which is also its JSON escape. Emoji, other scripts, and the marks escape-bidi-controls kept now show as escapes. **BREAKING** for anyone reading such characters from text output; JSON output's content is unchanged (each escape is the same character), so its format version stays 1.
- The first of two adjacent number signs is written as `\u0023`, so no output holds `##[` (GitHub Actions' older command form, found anywhere in a line), `##vso[` (Azure Pipelines), or `##teamcity[`.
- In text output, a line's first character is written as `\uXXXX` when it's a space, a no-break space, or a colon, so no line starts a GitHub Actions workflow command. A location (a file name, an OpenSpec id) is escaped as it is, its spaces kept, where text output turned each whitespace run into one space and dropped the ends, so a file named with a leading space was reported under another name.
- The README and `docs/json-output.md` say that output repeats input text, escaped for terminals and CI logs but not for HTML or Markdown, which a program rendering it must escape.
- Roadmap: fuzzing cmark-gfm (the C parser compiled into the native binary) goes on the Later list.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: the four "Control characters in …" requirements are renamed "Escaped characters in …" (text output, messages, JSON output, log lines) and name the English character set and the number-sign rule, each with new scenarios; a new requirement, "Line starts in text output", covers the first character of a line.

## Impact

- `ears/cli`: `Visible` (the predicate, shared with `Logging`) and `Checker` (a line's first character in text output). `Visible`'s lone-surrogate and bidirectional code goes: escaping every unit outside the set covers both.
- Tests: new tests for `Visible`, `Checker`, and `Logging`; five existing tests contradict the new behavior (a surrogate pair or a mark staying, and two properties whose plain text can hold `##`) and change only with the owner's approval; `scripts/cli-cases` cases compare the builds' bytes. README, `docs/json-output.md`, CHANGELOG, roadmap.
