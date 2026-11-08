# Proposal

## Why

The command line on Node takes 2.8 s for a 0.73 MB Markdown file and 43 s for a 7 MB one, where
the JVM takes 1.3 s and 4.2 s (measured 2026-11-07). A look at the cause found two, neither in
the Node shell itself:

- The classifier rebuilds a cats-parse character set of all 65,536 characters for every inline
  code span. The JVM hides that cost; Scala.js pays about 1 ms per requirement, on every input
  format.
- mdast's parser, micromark, takes time that grows faster than the document (0.43 s at 0.73 MB,
  24 s at 7 MB, its latest version). commonmark.js, the CommonMark reference implementation for
  JavaScript, from the same project as the JVM's and the native binary's parsers, parses the
  same files in 0.02 to 0.11 s.

0.2.0 is the first release on Node, so it should ship without either.

## What Changes

- The classifier builds its "any character but a backtick" parser once, not per code span. No
  behavior changes; every platform gets faster.
- On Node, the Markdown facade reads with commonmark.js (`commonmark` 0.31.2) instead of
  `mdast-util-from-markdown`. commonmark.js gives source positions for blocks only, so a pure
  helper finds each piece's line from the paragraph's source text. The conformance suite, which
  every platform's parser must pass unchanged, decides whether it's right.
- The CLI's npm package depends on `commonmark` instead of `mdast-util-from-markdown`.
- `scripts/time-cli` times the jar, the Node package, and the native binary on generated
  Markdown and plain text, and `docs/performance.md` records its runs.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `cli-npm-package`: the Markdown parser dependency is the one the conformance suite runs on
  Node, now `commonmark`.

## Impact

- `ears/src/io/github/tsnee/ears/Skeleton.scala`: the hoisted parser.
- `ears/markdown/js/`: `MarkdownParser.scala` on commonmark.js, `package.json` and its lockfile.
- `ears/markdown/src/`: a pure line-placement helper, with tests; `LineEndings`, which only the
  mdast parser used, and its tests go (the owner is asked before any test is removed).
- `build.mill`: the import map and the CLI package's dependency name.
- `scripts/time-cli` (new), `docs/performance.md` (new); CLAUDE.md, CONTRIBUTING.md, the README's
  speed claim, CHANGELOG, roadmap, work log.
