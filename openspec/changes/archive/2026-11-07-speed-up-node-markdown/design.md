# Design

## Context

Observations (2026-11-07; macOS 27.2 on Apple silicon, Node 25.2.1, JDK 27.0.1; the Node package packed from `ears.cli.js.npmPackage` at `56113a5` and installed into an empty project, the jar from `ears.cli.assembly`). Inputs: `unit.md`, every `openspec/specs/*/spec.md` concatenated (91,770 bytes, 2,016 requirement candidates per 8 copies), repeated 8, 16, 32, and 76 times; `unit.txt`, the specs' requirement texts one per line (199 lines), repeated likewise; and the Markdown candidates' own texts as plain text (`c8`, `c32`). Wall-clock times, one run each.

- **The symptom:**

  | Input | Node | JVM |
  | --- | --- | --- |
  | Markdown 0.73 MB | 2.81 s | 1.25 s |
  | Markdown 1.47 MB | 5.79 s | 1.47 s |
  | Markdown 2.94 MB | 13.18 s | 2.22 s |
  | Markdown 6.97 MB | 43.43 s | 4.16 s |
  | Plain text, 1,592 / 3,184 / 6,368 lines | 1.85 / 3.57 / 6.87 s |  |
  | The Markdown candidates as plain text, 2,000 / 8,000 lines | 2.18 / 7.94 s |  |

  Plain text is linear but slow (about 1.1 ms per requirement); Markdown grows faster than its input. Candidates grow linearly with the file (2,016 at 0.73 MB, 8,064 at 2.94 MB) and none is long (282 characters at most), so no single requirement gets slower to read.

- **Cause 1, the classifier on Scala.js.** A CPU profile of the unoptimized link (`fastLinkJS`, which keeps names) puts 83% of samples inside `Checker.findingsFor`, nearly all in `IterableOnceOps.toArray` and Char-array copying (`$isArrayOf_C`, `slowcopy`, Char boxing). Node's source-map reader traces them to `Skeleton.scala:93`, ``Parser.charWhere(_ != '`')`` inside `backtickRun.flatMap { open => … }`: cats-parse's `charWhere` builds its set by testing every `Char`, and the `flatMap` builds it again for every code span. It doesn't depend on `open`. A probe that built it once (then reverted) gave:

  | Input | Before | Built once |
  | --- | --- | --- |
  | Candidates as plain text, 2,000 / 8,000 lines | 2.18 / 7.94 s | 0.26 / 0.49 s |
  | Markdown 0.73 / 1.47 / 2.94 / 6.97 MB | 2.81 / 5.79 / 13.18 / 43.43 s | 0.91 / 1.89 / 5.72 / 25.83 s |

- **Cause 2, micromark.** Profiling the probe build on 2.94 MB puts 56% of samples in micromark's `EditMap.consume` (`micromark-util-edit-map`). Parsing alone, in the same process:

  | Input   | commonmark.js 0.31.2 | `mdast-util-from-markdown` 2.1.0 |
  | ------- | -------------------- | -------------------------------- |
  | 0.73 MB | 0.02 s               | 0.43 s                           |
  | 1.47 MB | 0.02 s               | 1.30 s                           |
  | 2.94 MB | 0.05 s               | 4.71 s                           |
  | 6.97 MB | 0.11 s               | 24.03 s                          |

  For comparison, commonmark-java 0.31.0 (the JVM's parser, with inline source spans, as `ears.markdown` uses it) parsing the same 6.97 MB five times in one JVM (scala-cli, Scala 3.10.0, JDK 27): 0.25, 0.19, 0.10, 0.11, 0.12 s. The two reference parsers are equally fast once warm; most of the JVM CLI's 4.16 s is the JVM starting, classifying about 19,000 sentences, and writing the findings.

  `mdast-util-from-markdown` 2.1.0, `micromark` 4.0.3, `micromark-util-subtokenize` 2.1.0, and `micromark-util-edit-map` 1.0.0 are each the latest on npm, so there's no upgrade to take. Garbage collection isn't it: on 2.94 MB, mark-compacts total 29 ms and scavenges 669 ms of the 13.2 s, with a live heap of 347 MB.

- **commonmark.js** (`commonmark` 0.31.2, 2024-09-19, BSD-2-Clause, the CommonMark project's JavaScript reference; dependencies `entities` 3.0.1 BSD-2-Clause, `mdurl` 1.0.1 MIT, `minimist` 1.2.8 MIT, the last for its own command line). ESM entry `lib/index.js` exports `Parser`; `new Parser().parse(text)` gives a tree walked with `walker()`. Probed on the conformance suite's line-sensitive inputs:
  - only blocks have `sourcepos` (`[[startLine, startCol], [endLine, endCol]]`, 1-based); inline nodes have none;
  - a paragraph's start line is right after a reference definition (`[r]: /u⏎The…` starts on 2; in a block quote, on 2), where cmark gets it wrong; its end line is right;
  - soft and hard breaks are nodes of their own (`softbreak`, `linebreak`), including inside image alt text across lines;
  - line endings it hides: inside a code span (`code "scheduler service"`, the line ending made a space, as CommonMark says), in a link's destination or title (`[link](u⏎"t")`), and in inline HTML (`html_inline "<span⏎class=\"x\">"`);
  - `&#10;` gives a `text "\n"` with no source line ending; a byte-order mark isn't text; `\r\n` is a `softbreak`.

## Goals / Non-Goals

**Goals:**

- Node's time linear in its input and close to the JVM's.
- The same sentences and lines on every platform, as the conformance suite already requires.

**Non-Goals:**

- Changing the JVM's or the native binary's parser.
- A CI timing gate: wall-clock times on shared runners vary too much to fail a build on; the timing script and `docs/performance.md` record them instead.

## Decisions

### 1. Build the code-span content parser once

- ``private val notBacktick = Parser.charWhere(_ != '`').void`` beside the other parsers, used inside the `flatMap`. The tokens are the same, so the classifier's existing tests and properties are the check; there's no behavior to test, and timing tests aren't kept (decision 5 records it).

### 2. commonmark.js on Node

- `ears/markdown/js/package.json` and its lockfile pin `commonmark` 0.31.2 instead of `mdast-util-from-markdown` 2.1.0, installed as now (`npm ci --ignore-scripts`); the tests' import map points `commonmark` at its `lib/index.js`.
- `MarkdownParser` (Node) imports the `Parser` class (`@JSImport("commonmark", "Parser")`), walks the tree, and for each paragraph (list items and block quotes included) collects pieces: `text` and `code` literals (a code literal already has spaces for line endings), image alt text (an image's children), a break as `Piece(" ", false, None)`; `html_inline` is dropped, as now. Lines come from decision 3.
- The npm CLI package's `dependencies` names the parser from that lockfile, as now (the name in `build.mill` changes).
- `LineEndings`, which only undid mdast's line endings inside `text` values, is unused and goes, with its tests in `HelpersSuite`; the owner is asked first, with the requirement they cover.

### 3. Lines from the paragraph's source

- A pure helper (`SourceLines`, shared, so it's measured on the JVM) takes the paragraph's source (the document's lines from its start line to its end line, whole), the start line, and the pieces without lines, and gives each piece the line it starts on.
- It reads the source once, left to right, with a cursor: for each piece with visible text, it finds the piece's text from the cursor, letting any run of whitespace in the piece match any run of whitespace in the source (a code span's space matches a line ending, and a container's `> ` or indentation sits only at line starts), and the piece's line is the start line plus the line endings before the match; the cursor moves past the match. A piece it can't find (text an escape or entity changed, say) takes the line at the cursor, and the cursor stays. Breaks take no line, as now.
- So hidden line endings (a code span's, a link title's, inline HTML's) are passed when the next piece is found after them, and no piece is ever placed before an earlier one.
- Properties: on text with no escapes, every piece is found and its line is the line its text starts on in the source; lines never decrease; pieces with no text keep `None`.
- Found while applying (2026-11-07), and added:
  - commonmark.js splits text at every escape (`\\`, then `` ` ``), where commonmark-java joins sibling text nodes; the facade's escaping needs them joined (the conformance fixture "a backslash before a backtick in text" failed). `SourceLines.joined` joins adjacent text of the same parent node; joining across a link's boundary would hide a line ending in its title.
  - A paragraph full of escapes (the README's table, `\<system\>`) left the cursor further behind on every line, putting sentences two or three lines early; `check-npm-cli` caught it on this repository's README, which no fixture covered. Two rules fix it, both from CommonMark: a backslash before ASCII punctuation in the source matches the punctuation alone, and each break, being a real line ending, moves the search past the next line ending.
- Found in review (2026-11-07), and fixed:
  - Unbounded search was quadratic: a piece that couldn't fully match (an entity on every line) scanned the rest of the paragraph, so 8,000 such lines took 78 s on Node. The search now looks only from the cursor to the end of the next line (text between two breaks is on one line, moved only by line endings the parser hides), which keeps a paragraph linear and stops a piece from matching its plainly written twin lines later. A break never moves the search past the paragraph's last line.
  - Text was joined across dropped inline HTML, putting a later sentence a line early. Joining now needs the later node to be the earlier one's next sibling (`later.prev eq earlier`).
  - In text an escaped backslash (`\\`) is tried as an escape first; in code, backslashes are literal.
  - Conformance fixtures pin the HTML and twin-text cases on every platform.
- The conformance suite decides: every fixture must pass on Node unchanged. If one can't, apply stops and reports it to the owner rather than changing the fixture.

### 4. The CLI's npm package

- `check-npm-cli` already derives the expected installed packages from the lockfile, and compares the Node CLI with the jar on this repository's Markdown, so it checks the swap end to end. The package installs `commonmark`, `entities`, `mdurl`, and `minimist`, where mdast brought more than a dozen.

### 5. Timing: `scripts/time-cli` and `docs/performance.md`

- `scripts/time-cli` (POSIX sh, developer-only like the other scripts) builds the jar, the native binary, and the Node package (installed into a temporary project), generates the inputs above (`--copies 8,16,32,76` by default) in a temporary directory, times each build on each input, and prints a Markdown table with the date, the machine (`uname -sm`), and the Node and Java versions. `docs/performance.md` keeps each run's table, newest first, with the commit it ran on.
- The first entries are this design's measurements and the run after the change.

## Risks / Trade-offs

- **[Lines in rare constructs]** Decision 3 is a heuristic where commonmark.js gives no inline positions. A piece whose text also appears earlier, between the cursor and its real place (inside a link's destination or title, say), would be placed too early. The conformance suite covers the known cases; a new disagreement becomes a fixture.
- **[A different parser on Node]** commonmark.js and micromark could read some input differently; commonmark.js is from the same project as the JVM's and native parsers, so it's likely closer to them. The conformance suite and `check-npm-cli` compare the platforms.
- **[Text repeated in a link's title]** A link title holding the same words as the text after the link can match first, putting that sentence a line early (`See [x](/u "The c SHALL d.⏎y") The c SHALL d.`). Contrived; `SourceLines` doesn't know where titles are. Accepted.
- **[`minimist` installed but unused]** It's commonmark.js's own command line's dependency; it runs nothing at install (`--ignore-scripts` in our installs; users' installs run no scripts of ours) and nothing at run time.

## Open Questions

- None.
