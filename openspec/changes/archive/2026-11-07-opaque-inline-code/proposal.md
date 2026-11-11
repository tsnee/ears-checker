# Proposal

## Why

Requirements often quote code: a message, a file name, a keyword. The classifier has no notion of inline code, so what's quoted is judged as if it were the requirement's own words: `` `Cannot read notes.txt. Check the path.` `` ends the sentence inside the quote, `` `The calendar shall sync` `` counts as a second modal, and `` `Done.` `` passes only by accident (a period before a backtick isn't punctuation). The Markdown reader makes it worse: it drops the backticks and splits sentences inside code before the classifier sees them. Separately, a leading UTF-8 byte-order mark breaks plain-text input (a fix carries the invisible mark) and OpenSpec JSON input (the file doesn't parse), and UTF-16 input gets the unhelpful `not valid UTF-8 at line 1, column 1`. This is the third change in the `batch-node-cli` batch.

## What Changes

- **Inline code is opaque to the classifier.** A code span, written as in CommonMark (a run of backticks, up to the next run of the same length), is one quoted word: its periods, commas, spaces, keywords, and modal words take no part in classification. A backtick with no matching run is an ordinary character, as now, and so is a backslash-escaped one (`` \` ``), as in Markdown.
  - Visible change: some sentences that quoted code were wrongly reported (multiple sentences, multiple responses) and now pass; a sentence whose only `SHALL` is quoted still reports a missing modal.
- **The Markdown reader keeps inline code** in the sentences it returns, as a code span with backticks, writes a backtick that isn't code as `` \` ``, and no longer ends a sentence at a period inside code. Plain text and OpenSpec JSON already reach the classifier with their backticks, so all three formats agree.
  - Visible change: findings for Markdown input show the code spans with their backticks.
- **A leading UTF-8 byte-order mark is ignored** in every input format.
- **UTF-16 and UTF-32 input**, recognized by its byte-order mark, is reported by name: `Cannot read notes.txt: UTF-16, not UTF-8`.
- Library and npm package: the classifier change reaches both, as the same behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ears-classifier`: inline code is one quoted word; a period inside code doesn't end a sentence.
- `markdown-input`: sentences keep their inline code as code spans, and don't end inside code.
- `ears-cli`: a leading byte-order mark is ignored, and UTF-16 and UTF-32 input is reported by name.

## Impact

- **`ears` (library):** the tokenizer in `Skeleton.scala` reads a code span as one word. Released in the next library version, on Maven Central and npm.
- **`ears.markdown`:** `Extraction` wraps code pieces in backticks and skips periods inside code; the conformance suite's three fixtures with inline code change their expected sentences (asked of the owner during apply), and new fixtures cover backticks inside code.
- **`ears.cli`:** `Utf8` drops a leading byte-order mark and names UTF-16 and UTF-32 input.
- **Docs:** README (how inline code is read), roadmap (the item comes off), worklog.
