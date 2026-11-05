# Design

## Context

See proposal.md for why. What's in place:

- `ears/cli/src/…/MarkdownInput.scala` (87 lines) is the only commonmark-java code.
  - Only `paragraphs` and `pieces` touch the parser. They walk the syntax tree for `Paragraph`
    nodes, list items included, and flatten each into `Piece(text, code, line)` runs: `Text`
    and `Code` literals, a space for each soft or hard line break, and the 0-based line of each
    `Text` or `Code` node from commonmark-java's inline source spans.
  - The rest is plain Scala that would run anywhere: `sentences` splits at a period followed by
    whitespace, a sentence is kept if `(?i)\bshall\b` matches outside a code piece, and its line
    is the line of the piece it starts in, else the paragraph's.
- `Candidate.fromMarkdown(file, content)` calls it; `ExtractSuite` has 5 Markdown tests
  (list item vs heading and fenced code, a sentence starting with inline code, escapes and
  entities, keyword only in inline code, soft line breaks).
- The `ears-cli` spec's Markdown requirement says what's checked, not how, so moving the parse
  behind a facade changes no requirement.
- **Node:** `mdast-util-from-markdown` 2.1.0 is ESM-only (`"type": "module"`), with 12
  dependencies. Probed on 2026-11-05 with the paragraph ``Some `code⏎span` here and *more*
  text.⏎The scheduler shall `list`⏎events.``:
  - every node has a `position` with 1-based lines;
  - soft line breaks stay inside `text` values as `\n`, so one `text` node can span lines
    (`" text.\nThe scheduler shall "` starts on line 2, though "The scheduler" is on line 3);
  - an `inlineCode` value keeps a line ending as `\n` (`"code\nspan"`), where CommonMark (and
    commonmark-java and cmark) turn it into a space.
- **Scala Native:** ZIO 2.1.27, decline 2.6.2, zio-json 1.1.1, and cats-parse 1.1.0 all publish
  Scala Native 0.5 builds; Scala Native is 0.5.12. Locally there's Apple clang 21,
  `pkg-config`, and cmark 0.31.2 (Homebrew; `pkg-config --libs libcmark` gives
  `-L/opt/homebrew/Cellar/cmark/0.31.2/lib -lcmark`). Ubuntu 24.04, which `ubuntu-latest`
  runs, has `libcmark-dev` 0.30.2.
- cmark's C API can be used with opaque pointers only: `cmark_parse_document`,
  `cmark_node_first_child`, `cmark_node_next`, `cmark_node_get_type_string`,
  `cmark_node_get_literal`, `cmark_node_get_start_line`, and `cmark_node_free`.
  cmark gives inline nodes their own source positions since 0.29.0 ("Add sourcepos info for
  inlines", its changelog), so both versions do. `cmark --sourcepos -t xml` on the paragraph
  above shows each `text` and `code` node with its own start line, splits text at soft breaks
  (as commonmark-java does), and gives the code span across lines as `code span`.

## Goals / Non-Goals

**Goals:**
- One small interface between the parser and the extraction, implemented on the JVM, on Node,
  and on Scala Native.
- One conformance suite, the same fixtures and expected results, run on all three, so any
  disagreement between parsers fails a test.
- The CLI's behavior and output unchanged; no new published artifact.

**Non-Goals:**
- The CLI on Node or Scala Native (the next roadmap items).
- Columns: the CLI reports lines only, for the reason `MarkdownInput`'s comment gives.
- Markdown features the CLI doesn't use today (tables, GitHub extensions).

## Decisions

### 1. The facade: parsed paragraphs as pieces

Package `io.github.tsnee.ears.markdown`:

```scala
/** A run of a paragraph's text: inline code or not, and the 1-based line it starts on, when the
  * parser knows it. A line break inside a paragraph is a piece of one space. */
final case class Piece(text: String, code: Boolean, line: Option[Int])

/** A paragraph, list items included, and the 1-based line it starts on. */
final case class Paragraph(line: Int, pieces: List[Piece])

/** A requirement sentence and the 1-based line it starts on. */
final case class Sentence(line: Int, text: String)

object Markdown:
  /** The sentences of `content` that contain the modal keyword outside inline code. */
  def sentences(content: String): List[Sentence] =
    MarkdownParser.paragraphs(content).flatMap(Extraction.sentences)
```

- `Extraction` (shared, pure) is today's `sentences`/`fromPieces` logic, unchanged except that
  lines are 1-based throughout.
- `MarkdownParser.paragraphs(content): List[Paragraph]` has one implementation per platform,
  each in its platform's source directory, the same pattern as `LetterCase`.
- Parsing is pure on every platform (no I/O), so there's no effect type; the native parser
  frees cmark's memory before it returns (Decision 4).

### 2. Modules and sources

| Module | Sources | Tests |
|---|---|---|
| `ears.markdown` (JVM, `Covered`) | `ears/markdown/src` + `src-jvm` (commonmark-java) | `ears/markdown/test/src` + `test/src-jvm` |
| `ears.markdown.js` (Scala.js) | `ears/markdown/src` + `js/src` (mdast) | `ears/markdown/test/src` on Node |
| `ears.markdown.native` (Scala Native 0.5) | `ears/markdown/src` + `native/src` (cmark) | `ears/markdown/test/src`, run natively |

- None is `Published`. `ears.cli` adds `ears.markdown`'s shared and JVM sources to its own and
  depends on commonmark-java as now, so its jar and POM are unchanged. (A module dependency
  would make the published CLI's POM name an unpublished artifact.) `MarkdownInput` becomes a
  one-line call to `Markdown.sentences`, mapping each `Sentence` to a `Candidate`.
- The conformance suite (`MarkdownConformanceSuite`, in `ears/markdown/test/src`) is shared by
  all three test modules, so every fixture runs on every platform.
- The CLI's coverage excludes the facade's sources (`-coverage-exclude-files:.*/ears/markdown/.*`,
  Scala 3's own coverage option, which Mill's scoverage module uses for Scala 3). Found in apply:
  without it the CLI measured them again as its own code, and fell to 92.74% / 91.18%, below its
  minimums, because its tests needn't reach every branch of a parser the conformance suite
  covers.
- Coverage is measured on `ears.markdown` (shared plus JVM parser) with minimums; the Node and
  native parsers are thin bindings that only Scala.js and Scala Native compile, the same limit
  as the npm facade, and the conformance suite covers each of their branches.

### 3. Every parser gives the facade the same pieces

All three parsers give inline nodes their start line (Context), so `Extraction` keeps today's
rule: a sentence's line is the line of the piece it starts in. `Piece.line` stays optional only
for line-break pieces, which have no text of their own to start a sentence. That rule needs
each parser to hand over the same pieces, which commonmark-java and cmark do as they are, and
the Node parser does after two adjustments (Decision 4): it splits a `text` value at each
`\n` into pieces on successive lines, with a one-space break piece between them, and it turns
each line ending inside an `inlineCode` value into a space. Conformance fixtures cover both: a
sentence that starts after a soft break inside one mdast `text` node, and a code span across
lines.

**Revised after the review**, which ran about 75 more inputs on all three platforms and found
four disagreements, each now a conformance fixture (38 in all) and fixed in shared, JVM-tested
code where the fix is pure:

- **cmark's inline lines are wrong in places, not just after code spans.** It doesn't advance
  after a backslash hard break or after a link whose title is on the next line, and a paragraph
  right after a reference definition starts on the definition's line, along with everything in
  it. C probes showed what cmark does get right: the paragraph's end line, that each break ends a
  line, and inline lines relative to each other between two breaks. `CmarkLines` (shared) places
  the last stretch at the end line, each earlier stretch one line before the next (less the lines
  its own nodes span), and each piece relative to its stretch. Still wrong: a sentence that
  starts inside a link's text when the link's title or destination crosses a line, since cmark
  reports no span for the link.
- **mdast's `imageReference`** had no case, and alt text across lines kept its line ending: both
  are read like `image` now, alt text split per line.
- **mdast decodes `&#10;` and `&#13;`** into line endings in `text` values, where
  commonmark-java and cmark keep them as text. `LineEndings` (shared) splits a value only at the
  line endings that are in the node's source, from mdast's offsets.
- **commonmark-java keeps a leading byte-order mark** in the first sentence, which mdast and cmark
  drop. `Markdown.sentences` drops it on every platform (the CLI kept it before this change).
- **Deep nesting** (thousands of levels of emphasis or block quotes) overflowed the stack of the
  recursive walks on Node and Scala Native (natively, an uncatchable signal). `Walk` (shared)
  walks with an explicit stack on all three. The parsers' own nesting limits still differ, so the
  results on such input may too; the `markdown-input` delta says so, and the suite checks only
  that nothing fails.

### 4. The Node and native bindings

- **Node:** `@JSImport("mdast-util-from-markdown", "fromMarkdown")`, reading the tree through
  `js.Dynamic` (`type`, `children`, `value`, `position.start.line`). Node types `paragraph`,
  `text`, `inlineCode`, and `break` map to the facade, with Decision 3's two adjustments: a
  `text` value is split at each `\n` into pieces on successive lines with a space piece
  between them, and a line ending inside `inlineCode` becomes a space. Tests
  need the package installed: a Mill task runs `npm ci` from a lockfile in `ears/markdown/js`
  into its `Task.dest` (with `--cache` in the task's directory, as in the npm package check), and
  the tests link as ES modules with Mill's `scalaJSImportMap` rewriting the bare import to that
  install's entry file (`ESModuleImportMapping.Prefix("mdast-util-from-markdown",
  "file://<dest>/node_modules/mdast-util-from-markdown/index.js")`). The package's own imports
  then resolve from its `node_modules` as usual. Checked on 2026-11-05 with a throwaway Mill
  1.3.1 project: a zio-test suite linked this way imported the package on Node and read a list
  item's line. Without the map, Node can't find the package: it resolves bare imports only from
  `node_modules` directories above the importing file, and Mill links tests under `out/`.
- **Scala Native:** `@extern object cmark` over `Ptr[Byte]` for nodes, so no struct layouts
  are declared. `paragraphs` parses inside `Zone` for the input string and walks the tree with
  `cmark_node_first_child` and `cmark_node_next`, mirroring the JVM parser (simpler than the
  iterator first planned), then frees the document. It parses with `CMARK_OPT_SOURCEPOS`
  (`1 << 1` in both versions), not `CMARK_OPT_DEFAULT`. Found in apply: under the default,
  inline start lines are wrong after a code span that crosses a line. A C probe of 0.31.2 gave
  the text after it line 1, and the conformance fixture for that case failed natively. A `NULL`
  child or sibling is `None` through `Option(pointer)` and `toLong != 0`, so the binding needs no
  `null` literal whichever way Scala Native represents it. Node
  types are read with `cmark_node_get_type_string` (`"paragraph"`, `"text"`, `"code"`,
  `"softbreak"`, `"linebreak"`), not as numbers: the `cmark_node_type` enum's values are
  implicit, the same in 0.30.2 and 0.31.2 but not promised, and both versions declare every
  function this binding calls (checked in both `cmark.h` files on 2026-11-05).
  Linking takes `pkg-config --libs libcmark` (a `Task.Input`, so a change of system library is
  noticed); a missing cmark fails the build with a message saying to install it.

### 5. CI

- `check` installs cmark with `sudo apt-get install -y libcmark-dev` before the build, the same
  kind of run step as the pinned `npm install -g`. Scala Native's clang comes with
  `ubuntu-latest`.
- `./mill __.test` runs the three test modules with the rest, so no new step. The npm parser
  is installed by the Mill task, not globally. The task needs the network the first time, and
  after a `clean` or a lockfile change, since its npm cache lives in its own directory; npm's
  errors show (`--loglevel=error`), where `--silent` hid them.
- Linking puts `pkg-config`'s flags before Mill's defaults, so its `-L` decides which cmark is
  linked; a missing `pkg-config` fails with the same message as a missing cmark.
- `check-jdk17` changes nothing: it doesn't run the tests.

### 6. Docs

- **README:** Building names cmark as a requirement for the native tests.
- **CLAUDE.md:** Build and test (cmark, the npm task), Layout (`ears/markdown/`), and the
  rule that Markdown parsing goes through the facade.
- **`docs/roadmap.md`:** the facade item comes off; the CLI on Node becomes first.

## Risks / Trade-offs

- **[Parsers disagree on a case the suite lacks]** The suite covers today's tests and the
  likely differences (code spans across lines, escapes, entities, nested lists, lazy
  continuation lines, HTML blocks, headings, setext headings, block quotes, tabs). A
  disagreement found later becomes a fixture.
- **[The native build costs CI time]** Linking a Scala Native test binary takes tens of
  seconds to minutes; apply reports it. The cmark install is a few seconds.
- **[The native and Node parsers aren't measured]** They're thin bindings; the conformance
  suite reaches every branch, and the shared extraction is measured on the JVM.
- **[A system library in the build]** cmark comes from the operating system, not from Maven:
  the build can't pin its version. The conformance suite is what guards against a change in
  its behavior.
- **[cmark and npm versions drift apart]** commonmark-java, cmark, and micromark all follow
  CommonMark, but each at its own pace; the lockfile pins the npm side, and the suite catches
  drift on the others.
