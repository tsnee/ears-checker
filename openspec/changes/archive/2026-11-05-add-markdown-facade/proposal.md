# Proposal

## Why

The CLI reads Markdown with commonmark-java, which exists only on the JVM. That one dependency
blocks two roadmap items: the CLI on Node (`npx ears-check`) and Scala Native. Everything else
the CLI uses already builds for both. Only the parse itself depends on the parser: turning a
document into paragraphs of text runs, each marked as code or not, with their lines. Splitting
sentences, finding the modal keyword, and placing each sentence on a line are already plain
Scala that would run anywhere.

## What Changes

- **A Markdown facade:** a small interface for what the CLI reads from a parsed document
  (paragraphs, list items included, as runs of text and inline code with their lines; headings,
  code blocks, and the rest left out), with one implementation per platform:
  - **JVM:** commonmark-java, as today.
  - **Node:** `mdast-util-from-markdown` (the unified/remark parser), through Scala.js.
  - **Scala Native:** cmark, CommonMark's reference C implementation, through Scala Native's C
    interop.
- **Shared extraction:** the sentence splitting, keyword search, and line placement move out
  of `MarkdownInput` into code all three platforms compile.
- **One conformance suite:** the same Markdown fixtures and expected sentences and lines, run
  on all three platforms, so the parsers can't disagree unseen. It starts from the CLI's
  existing Markdown tests and adds the cases where parsers are likeliest to differ.
- **Builds:** new modules for the facade on each platform; the CLI keeps compiling the JVM
  parser into itself, so no new Maven artifact. The CLI's behavior and output don't change.
- **CI** runs the conformance suite on the JVM, on Node, and natively, installing cmark and
  the npm parser for those runs.
- **Not in this change:** the CLI on Node or Scala Native (each its own change, next on the
  roadmap), and any change to what the CLI checks in Markdown.

## Capabilities

### New Capabilities
- `markdown-input`: reading requirement sentences from Markdown, and getting the same
  sentences and lines on every platform.

### Modified Capabilities
- `continuous-integration`: a test that fails on Scala Native fails the run, as one on the JVM
  or Node does.
- `test-coverage`: the facade's JVM module, `ears.markdown`, is measured like the others.

## Impact

- **New sources:** `ears/markdown/` (shared extraction, the facade, and one parser per
  platform), and the conformance suite.
- **`build.mill`:** `ears.markdown` (JVM, measured), `ears.markdown.js` (Scala.js, tests on
  Node), and `ears.markdown.native` (Scala Native 0.5, tests run natively, linked with cmark).
  None is published; `ears.cli` compiles the shared and JVM sources into itself.
- **Moved:** `ears/cli/src/…/MarkdownInput.scala` becomes the JVM parser plus the shared
  extraction; the CLI's Markdown tests move into the conformance suite.
- **Dependencies:** `mdast-util-from-markdown` (npm, pinned with a lockfile, for the Node
  tests only); cmark (system library: `brew install cmark` locally, `libcmark-dev` in CI);
  Scala Native's `nativelib` 0.5 and clang.
- **`.github/workflows/ci.yml`:** installing cmark, and the native and Node test runs, which
  `./mill __.test` already includes.
- **Docs:** `README.md` and `CLAUDE.md` (the new modules, cmark), `docs/roadmap.md` (the item
  done), and `docs/worklog.md`.
