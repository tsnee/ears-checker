# Proposal

## Why

When an input given as OpenSpec JSON isn't OpenSpec change or spec JSON, the checker writes `Not OpenSpec change or spec JSON: (expected '"' got 'n')` (the jar, 2026-11-14), which doesn't say which input it's about: a run over several files leaves the user guessing. That breaks `ears-cli`'s existing requirement "Unreadable input message", which already says an input that "cannot be read or parsed" gets a message naming it; its scenarios cover only read errors, which do name their input (`Cannot read notes.txt: not valid UTF-8 at line 2, column 2`). Found by the test review on 2026-11-11 (roadmap item 4); the first change of the "CLI messages and output" theme, items 4 to 7.

## What Changes

- The message names the input, in the read errors' form, with the wording the owner chose on 2026-11-14: `Cannot parse notes.json: not OpenSpec change or spec JSON: (expected '"' got 'n')`. Standard input is named `standard input`, as the read errors name it.
- The same on the JVM, Node, and Scala Native, checked by a new case in `scripts/cli-cases`.
- **BREAKING** only for anything that matched the old message's start, `Not OpenSpec change or spec JSON:`. The exit status (2) and the log entry are unchanged.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: "Unreadable input message" keeps its statement, which already requires this, and gains scenarios for a parse error: a file, standard input, and the second of two files.

## Impact

- `ears/cli`: `OpenSpecInput` gives the parser's detail, and `Checker.read` writes the message with the input's name; the name for standard input, `standard input`, comes from one place for read and parse errors alike.
- Tests: a new test per platform-independent case; the two tests that check the old message's start (`CheckerSuite`, `MainSuite`) change with the owner's approval.
- `scripts/cli-cases` gains a case; CHANGELOG's Unreleased gains a line.
