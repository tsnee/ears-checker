# Design

## Context

See proposal.md for why. Observations (2026-11-17, the source on `batch-check-other-projects` at 406dbd7, the OpenSpec CLI 2.0.0 installed at `/opt/homebrew/Cellar/openspec/2.0.0`, which CI pins too, and scratchpad probes):

- The Markdown facade (`ears/markdown`) gives the CLI sentences with lines, nothing about the headings they're under. Its records (`ears_cmark.c`) carry each node's type, position, and literal, so a heading's text is there but not its level, which would need a change to the C entry point (and the WebAssembly checksum) or a look at the source line.
- OpenSpec doesn't parse CommonMark: its readers (`dist/core/parsers/`) are line-based. A header is a line matching `^(#{1,6})\s+(.+)$` outside a fenced code block (`code-fence.js`: a run of three or more backticks or tildes, closed by a run as long, a backtick opener whose info string has a backtick being inline code), its title with a closing run of `#`s stripped.
  - A spec's requirements (`markdown-parser.js`): the first section whose title is `Requirements` (any case, any level); each header one level below it, up to the next header at its level or above, is a requirement.
  - A delta's requirements (`requirement-blocks.js`): the `## ` sections titled `ADDED Requirements`, `MODIFIED Requirements`, `REMOVED Requirements`, and `RENAMED Requirements` (any case); in them, lines matching `^###\s*Requirement:\s*(.+)$`. `openspec show --json --deltas-only` gives each block's requirement with its operation.
  - A requirement's text (`requirement-text.js`, `extractRequirementBody`): the lines after its header up to the next header, skipping fenced lines and blank lines, each trimmed, joined with `\n`; lines matching `^\*\*[^*]+\*\*:` (`**ID**: …`) are dropped unless nothing else is left. With no text at all, the header's title is the text, `Requirement:` prefix included: an `### Requirement: The scheduler SHALL list events` with only a scenario under it comes back as `"text": "Requirement: The scheduler SHALL list events"` from `openspec show --json --deltas-only` (a scratch change, 2026-11-17).
- A probe in Scala (scratchpad `openspec-md-probe/Probe.scala`) applying those rules agrees with `openspec show --json` exactly, name and text, on every requirement of this repository: all 15 specs (284 requirements), and all 37 archived changes copied into a scratch OpenSpec root (344 added and modified requirements).
- The CLI checks OpenSpec JSON's `text` as one candidate (`OpenSpecInput`), located by change or spec and requirement name; Markdown as one candidate per sentence with a modal keyword (`MarkdownInput`), located by line.
- `Settings.inputFormat` infers Markdown from `.md`, OpenSpec JSON from `.json`; `--format` takes `text`, `markdown`, `openspec`. The default profile is OpenSpec for OpenSpec JSON, classic otherwise.
- `DirectoryWalk` (from check-directories) checks `.md` and `.txt` files; with it, `ears-check .` in an OpenSpec project reads `openspec/` as ordinary Markdown: proposals, designs, tasks, and specs with their scenarios.
- `OwnSpecsSuite` (JVM test) checks this repository's spec statements with commonmark-java, deliberately apart from the CLI's readers; `scripts/cli-cases` writes each spec's JSON with `openspec show` and passes every tracked `.md` file (specs included) to the builds in its `markdown-*` cases, which accept status 0 or 1.

## Goals / Non-Goals

**Goals:**

- A spec's or delta spec's Markdown is checked exactly as its JSON from `openspec show` is: the same requirements, the same text, located by line.
- `ears-check .` or `ears-check openspec` in an OpenSpec project checks its specs and active changes' delta specs, and nothing else under `openspec/`.

**Non-Goals:**

- Checking proposals, designs, or task lists, whose requirement-like sentences are summaries.
- Recognizing an OpenSpec layout from inside it (`cd openspec && ears-check specs`): the path has no `openspec` in it; `--format openspec-markdown` covers a single file.
- OpenSpec stores (`--store`) and project configuration (`openspec/config.yaml`'s `context`): only the files are read.
- Changing `scripts/check-openspec`, which keeps checking through OpenSpec's JSON (decision 6).

## Decisions

### 1. Read OpenSpec's Markdown by OpenSpec's own line rules (the owner's decision)

- A shared, pure `OpenSpecMarkdown` reader follows the rules in Context: header lines outside fences, the `Requirements` section's child headers in a spec, `### Requirement:` blocks in the ADDED and MODIFIED sections of a delta (both kinds read from any file, in document order, since a spec has no delta sections and a delta no `Requirements` section), and each requirement's text as `extractRequirementBody` takes it, with the header's title when it has none. Each text is one candidate, as OpenSpec JSON's is.
- So the native route checks exactly what the JSON route does (the probe: 628 requirements, no difference), and a classifier finding reads the same either way. It needs no change to the Markdown facade, its C entry point, or its checksum.
- It isn't CommonMark: a `### Requirement:` line in an indented code block or an HTML block counts, and text keeps its Markdown (`**`, backticks), as OpenSpec's does. That's the point: OpenSpec reads it that way. CLAUDE.md's "reads Markdown only through the facade" gets this exception in words.
- OpenSpec's patterns are JavaScript's, so the reader's whitespace is JavaScript's (its `\s` and `trim()` share one set: the ASCII spaces, U+00A0, U+1680, U+2000 to U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF), not Java's `\s` or `Character.isWhitespace`; and where JavaScript's `.` stops at a line or paragraph separator, so does the reader. Every pattern is a scan of characters rather than a regular expression, so it means the same on the JVM, Node, and Scala Native. Section titles are compared with `equalsIgnoreCase`, which no locale changes (the code review, 2026-11-17: under a Turkish default locale, `toLowerCase` turned `MODIFIED` into `modıfıed`).
- A requirement's text ends at OpenSpec's `^#{1,6}\s` line, which needs no title and so is looser than a header; it's found by a binary search of those lines, so a spec of thousands of requirements reads in time proportional to its lines.
- _Alternative: the facade, extended with each sentence's heading_ (the roadmap's first idea): CommonMark's view of the same file, so it would disagree with OpenSpec wherever their rules differ (a list item's text, a statement split by a table, a fence CommonMark doesn't see), and it splits statements into sentences where OpenSpec checks the whole text. It also needs the heading level, which the records don't carry. Not chosen.

### 2. Recognized by the path, or by `--format openspec-markdown` (the owner's decision)

- With no format selected, a file is OpenSpec Markdown when its path, split at `/` (and `\`, for Windows), has a component `openspec` followed by `specs`, one or more directories, and `spec.md`; or by `changes`, a change's directory, `specs`, one or more directories, and `spec.md`; or by `changes`, `archive`, an archived change's directory, and the same. Anything else ending in `.md` is Markdown, as now. A pure function in shared code (`OpenSpecPaths`).
- `--format openspec-markdown` reads any file so; `--format markdown` reads a spec as ordinary Markdown.
- _Alternatives_: `openspec/config.yaml` beside the directory (a file read for each walk, and OpenSpec finds its root by the directory alone); sniffing a file's content for `## Requirements` or `## ADDED Requirements` (a README could have either). Not chosen.

### 3. The walk inside `openspec`: specs only, the archive skipped (the owner's decision)

- `DirectoryWalk` decides from a found path (its directory as given and its name): a file in an OpenSpec directory (an `openspec` directory itself, or a directory below its `specs` or `changes`) is checked only when it's an OpenSpec spec file (decision 2), and otherwise skipped as not requirements. Another directory below one named `openspec` (`src/openspec/docs`, in a clone of the OpenSpec tool's own repository) is walked as any other (the code review, 2026-11-17); a directory named `archive` directly in `openspec/changes` is skipped (a new reason, `Archive`, logged like a link). The archive given itself, or a directory in it, is walked, and its delta specs are checked: the user named it.
- The directory given counts too: `ears-check openspec/changes/add-reminders` checks its delta spec and not its proposal.
- _Alternative: check the archive_: archived deltas are history; checking them fails a project for wording its owner already accepted. Not chosen.

### 4. Profile, locations, and the format's name

- The OpenSpec profile by default, as for OpenSpec JSON; `--profile` overrides it.
- A finding is located at the line its requirement's text starts on (the first line taken, after blank or label lines), or the header's line when the title is the text: `Location.Line`, so text output is `file:line: …` and JSON output's location is `line`, as for Markdown, with no new location type and no change to `docs/json-output.md`'s version.
- The log's format is `openspec-markdown`.

### 5. The message for a directory with nothing to check

- Given an OpenSpec directory, `Cannot check <dir>: it holds no OpenSpec spec files`; given another directory whose walk went into an `openspec` directory, `Cannot check <dir>: it holds no .md or .txt files outside openspec, and no OpenSpec spec files` (the walk reports whether it did, so a project whose only Markdown is a proposal isn't told it has none; the code review, 2026-11-17); elsewhere, as now, `Cannot check <dir>: it holds no .md or .txt files`. A project with no active change and `ears-check openspec/changes` fails with the first, as `docs/` with nothing does with the last.

### 6. Agreement with OpenSpec in CI, not in `check-openspec` (the owner's decision)

- `scripts/check-openspec` is the OpenSpec gate and stays on the JSON route, unchanged: its "Single EARS checker run" requirement would otherwise need a second run, and duplicated findings would muddle the hook's report.
- `scripts/cli-cases` already writes every spec's JSON with `openspec show`. It also runs the jar with `--verbose` on each spec's Markdown and checks that the `Read input` entry's `requirements` count equals the JSON's, so a change in OpenSpec's rules (an upgrade of the pinned CLI) fails CI. Since `cli-cases` is sourced by three scripts, the check runs on the jar once per script; it takes seconds.

### 7. Tests

- `OpenSpecMarkdown` (every platform): each rule in Context with a witness (sections in any case, a closing `#` run, fences of both kinds and the inline-code exception, label lines alone and with text, the title fallback with its prefix, REMOVED and RENAMED left out, a spec's non-`Requirement:` child header, CRLF and a byte-order mark), and the line of each text.
- `OpenSpecPaths` and `DirectoryWalk` (every platform): the path patterns, the archive, the walk's choices inside `openspec`, the message.
- `Settings`: the inference and the `--format` value; `CliSuite`: a spec and a delta end to end with fakes.
- `scripts/cli-cases`: an OpenSpec tree (a spec, an active change, an archived one, a proposal) compared across the builds, text and JSON; and decision 6's counts.

## Risks / Trade-offs

- [OpenSpec changes its rules in a later release] → CI's pinned version moves only with Dependabot's pull requests, and decision 6's counts fail on such a pull request; the reader is then updated to match.
- [A project keeps a directory named `openspec` that isn't OpenSpec's] → Only its own files and what's below a `specs` or `changes` in it follow OpenSpec's rules; other directories in it are walked as any other. Rare.
- [A spec file in which no requirement section is recognized (`## ADDED Requirement`, singular) gives no requirement and passes] → Known limitation, kept on the owner's word (the code review, 2026-11-17): the file's structure is OpenSpec's to check (`openspec validate --strict`), and a warning would need a new kind in the JSON output.
- [Walking from inside `openspec/` (`cd openspec && ears-check .`) reads proposals and specs as ordinary Markdown, with many false findings] → Known limitation, kept (the code review, 2026-11-17; a non-goal above): the path as given doesn't show the layout, and each platform's real path would be needed through `Reading`. The README says to check from the project's root.
- [`--format openspec-markdown` on a directory reads every file found so, and ordinary Markdown has no requirement section, so it's checked for nothing] → Known limitation, kept (the code review, 2026-11-17): `--format` applies to every file found, as for every format.
- [The help text gains `openspec-markdown`] → No test quotes the format option's help (checked 2026-11-17); the README's option list changes with it.

## Assumptions checked and unchecked

- Checked (2026-11-17): OpenSpec 2.0.0's rules, read from its source; the probe's agreement on 15 specs and 37 archived changes; the title fallback with its prefix for an empty delta requirement.
- Unchecked: how OpenSpec reads a spec whose `Requirements` section is nested below another heading (the probe follows its code: the first such section in document order); Windows paths with `\` (decision 2 splits on both; Windows CI runs only the smoke cases).
