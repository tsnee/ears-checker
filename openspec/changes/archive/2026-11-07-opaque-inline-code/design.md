# Design

## Context

See proposal.md for why. What's in place, checked while planning (2026-11-07):

- **The classifier's tokenizer** (`SkeletonParser` in `ears/src/.../Skeleton.scala`) makes words, commas, and periods. A comma or period is punctuation only before whitespace or the end; a backtick is an ordinary word character. Keywords, modal words, and weak modals are found by comparing whole words. So `` `Done.` `` is one word by accident, while `` `notes.txt. Check` `` holds a sentence end and `` `The calendar shall sync` `` holds a modal word.
- **Fixes:** each edits a keyword, the modal, a comma, the final period, the clause order (which copies clause text verbatim), or the start of the system name. Only the missing-article fix reads the system name's text: it takes the first word by splitting on whitespace, so for `` `job scheduler` `` it sees `` `job ``. It happens to produce the right fix today (it lowercases the backtick, which changes nothing), but by luck.
- **The Markdown reader** (`Extraction` in `ears/markdown/src`) joins a paragraph's pieces, each marked code or not, into one text. A code piece holds the code's literal, without backticks, on every platform's parser. Sentences end at any period followed by whitespace, code or not; only the keyword search skips code. Three conformance fixtures have inline code and expect it without backticks.
- **Byte-order marks** (the jar on this branch):
  - plain text: `Scheduler shall list events.` after a UTF-8 mark gives the fix `"The ﻿Scheduler …"`;
  - OpenSpec JSON after a mark fails: `Not OpenSpec change or spec JSON: (expected '{' got '﻿')`;
  - Markdown is fine: `Markdown.sentences` already drops a leading mark;
  - UTF-16 with its mark: `not valid UTF-8 at line 1, column 1`.
- **The three parsers agree on backticks.** commonmark-java 0.31.0, mdast-util-from-markdown 2.1.0, and cmark 0.31.2 (CI has 0.30.2) gave the same result on nine edge cases: double backticks around a backtick, padding stripped (one space each side, not from a lone space), unmatched runs as text, an escaped backtick (`` \` ``) as text, inline HTML taking precedence over a code span, and no escapes inside code.
- **What reaches the classifier:** plain-text lines and OpenSpec JSON keep their backticks; Markdown doesn't (above). `OwnSpecsSuite` wraps inline code in single backticks, as OpenSpec's JSON has it.

## Goals / Non-Goals

**Goals:**

- One rule for inline code in the classifier, reached the same way from all three input formats.
- Byte-order marks handled the same way in all three formats.

**Non-Goals:**

- Markdown inside plain text or OpenSpec JSON: only code spans are recognized there, because quoting with backticks is the common convention in all three.
- Other inline Markdown (emphasis, links) in the classifier.
- Encodings other than UTF-8; detecting UTF-16 without a byte-order mark.

## Decisions

### 1. The code-span rule: CommonMark's

- A run of _n_ backticks opens a code span, which closes at the next run of exactly _n_ backticks; runs of other lengths inside are content. That lets ``` ``a ` b`` ``` quote a backtick, as in Markdown, so one rule serves all three formats.
- A run with no matching run is ordinary characters, as every backtick is today.
- **A backslash before a backtick escapes it, as in Markdown:** `` \` `` is an ordinary character and opens nothing (inside a code span, a backslash is just content, as in Markdown). A backslash also escapes a backslash, so in `` \\` `` the backtick does open code; without that pair, a literal backslash just before code hid the span (found in review). Markdown turns `` \`not code\` `` into text with plain backticks; without this rule the reader's output would make it code. OpenSpec JSON keeps the backslashes, being raw Markdown, so it agrees for free; plain text gets Markdown's meaning too.
- **Alternatives:** single backticks only (simpler, but code holding a backtick would break the sentence); treating an unmatched backtick as opening code to the end (it would hide the rest of the sentence from the checker).

### 2. A code span is part of a word

- In the tokenizer, a word is a run of code spans and word characters, so a span is one word, or part of one when it touches other characters (`` `ears`-style ``). Its text keeps its backticks, so it never equals a keyword or modal word, and the periods, commas, and spaces in it are never punctuation or word breaks.
- In cats-parse: the opening run, then any mix of non-backtick characters and backtick runs of other lengths, then a run of the same length; if that fails, the backtick is an ordinary word character.
- Spans in diagnostics are unaffected: tokens keep their source positions.

### 3. The missing-article fix reads tokens

Planned: the skeleton would record the system name's first word as a token, for the fix to use instead of splitting on whitespace. **Not done, after checking in apply:** a word that starts with a backtick never matches a determiner or quantifier, and the fix's lowercasing of the first character leaves a backtick as it is, so the whitespace split gives the right fix for every code-span case (`` `job-runner` `` and `` `job runner` `` both become `` The `…` ``, tested in `InlineCodeSuite`). The change would add code with no observable effect.

### 4. The Markdown reader returns code spans

- `Extraction` writes each code piece between backticks: a fence one longer than the longest run of backticks in its literal, padded with a space on each side when the literal starts or ends with a backtick, as CommonMark itself writes code. The classifier, following the same rule, reads it back as one span.
- A backtick in a text piece (escaped in the source, or one that matched nothing) is written as `` \` ``, so the classifier never pairs it into a span Markdown didn't make. Each backslash that comes right before a backtick (a text one, or the fence of the code after it) is doubled, so it can't escape that backtick; other backslashes stay single, as Markdown shows them. (No regex: Scala Native's has no lookahead.)
- Code pieces with nothing between them (code, dropped inline HTML, code) are joined and written as one span, since adjacent fences would otherwise be read back as different spans.
- `InlineCodeRoundTripSuite`, in the CLI's tests (the one module with both the reader and the classifier, on the JVM and Node), writes random text and code as Markdown, reads it back, and classifies it: any disagreement about where code is shows as a sentence that doesn't classify.
- Sentence bounds skip a period inside a code piece; the keyword search already skips code.
- Lines are found as now, by offset into the joined text, which now includes the fences.
- The facade is shared, so the JVM, Node, and Scala Native agree; the conformance suite gains the nine edge cases above (so a parser upgrade that diverges fails on its platform, and CI's cmark 0.30.2 is checked too), a period inside code, and escaped backticks; its three fixtures with inline code expect backticks (with the owner's approval, since they change existing tests).

### 5. Byte-order marks, in `Utf8.decode`

- A leading UTF-8 mark (`EF BB BF`) is dropped before decoding, so every format reads as if it weren't there, and positions count from after it, as an editor shows them. (The Markdown facade's own dropping stays, for its own callers; it finds no mark to drop.)
- A UTF-32 mark (`FF FE 00 00`, `00 00 FE FF`, checked first) or a UTF-16 mark (`FF FE`, `FE FF`) gives a new `ReadFailure`, `OtherEncoding(name)`: `Cannot read <input>: UTF-16, not UTF-8`.
- Both platforms decode there, so they agree; `check-npm-cli` gains a case for UTF-16 input and one for a byte-order mark.

## Risks / Trade-offs

- **[The library's behavior changes for its users]** Sentences that quoted code with periods or modal words were reported and now pass, and a sentence whose only modal is quoted is reported. That's the fix, but it reaches Maven Central and npm users in the next release; the README says how inline code is read.
- **[A pair of backticks that wasn't meant as code]** It's read as code anyway; plain text and OpenSpec JSON use backticks for code by convention, and the rule is Markdown's.
- **[Parsing cost]** cats-parse's `flatMap` per backtick run; negligible next to the rest.
- **[`OwnSpecsSuite`]** It wraps code in single backticks, not the facade's computed fence; it differs only for code holding a backtick, which this repository's specs don't have.
