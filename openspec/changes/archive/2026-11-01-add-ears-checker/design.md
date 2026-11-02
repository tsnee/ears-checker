# Design

## Context

See proposal.md for motivation and the specs for required behavior. Relevant current state:

- `build.mill` has a shared `Base` trait: Scala 3.10.0, JDK 27,
  `-Werror`, explicit nulls, strict equality, and WartRemover warts as errors. Pure modules get
  every Unsafe wart (`pureWarts`); ZIO code and zio-test specs drop the `Any` wart.
- Tests are zio-test only. Libraries are chosen on merit, with ZIO as the effect system.
- `scripts/check-openspec` runs `openspec validate --all --strict` and
  `openspec validate --archived --strict`; `scripts/claude-openspec-hook` calls it after edits
  under `openspec/` and turns a failure into a blocking hook error (exit 2).
- `openspec show <id> --type change --json --deltas-only` returns
  `{id, deltas: [{spec, operation, requirement: {name, text, …}}]}`; `operation` is ADDED,
  MODIFIED, REMOVED, or RENAMED, and only the first two carry requirement text.
  `openspec show <id> --type spec --json` returns `{id, requirements: [{name, text, …}]}`.

## Goals / Non-Goals

**Goals:**
- A classifier library whose public API is pure and total, and that builds and passes its tests
  on both the JVM and Scala.js.
- Diagnostics precise enough to fix mechanically: every diagnostic has a span, and most have an
  edit that produces the corrected sentence. The tool stays a linter: it reports suggested
  fixes and never rewrites its input. (Confirmed after the code review, which found fix bugs;
  keeping fixes was chosen over a pure linter because they make findings actionable for people,
  editors, and agents.)
- A thin CLI: all checking logic stays in the library.

**Non-Goals:**
- Natural-language understanding. The classifier works on EARS's surface structure (clause
  keywords, commas, the article, the modal keyword); it does not judge whether a response is one
  action or two joined by "and", or whether two requirements name the same system the same way.
- Publishing artifacts, a Scala.js build, or a stable public API. Package names and API shape may
  change before the module moves to its own repository.

## Decisions

### Modules

- `ears` (directory `ears/`, package `io.github.tsnee.ears`): the library. Depends only on the
  parser library. Uses `pureWarts`. Its test module uses zio-test and drops the `Any` wart.
- `ears.cli` (directory `ears/cli/`, package `io.github.tsnee.ears.cli`): depends on `ears`, ZIO,
  and the CLI libraries below. Uses `zioSafeWarts`.
- Package roots sit under `io.github.tsnee` with a project segment in between, never directly
  under it. `ears` names the tool rather than this repo, so the packages stay valid after the
  move to a standalone repository. Source directories mirror the packages
  (`ears/src/io/github/tsnee/ears/`, `ears/cli/src/io/github/tsnee/ears/cli/`).
- `ears.js`: a Scala.js 1.22.0 build of the same library sources and the same test suites, run
  on Node. It exists only to keep the library portable: any JVM-only API fails this build.
  Nothing is packaged or published from it in this change. Library tests therefore can't read
  files; tests that need files live in `ears.cli.test`.
- They depend on nothing else in the build, so they can move to their own repository unchanged.

### Parser library: cats-parse 1.1.0

Compared on Maven Central today:

| Library | Version | For | Against |
|---|---|---|---|
| cats-parse | 1.1.0 (Dec 2024) | Pure, no macros, explicit backtracking (`backtrack`), `Parser.index` for offsets, Scala.js and Native builds | Slow release cadence; brings in cats-core |
| fastparse | 3.1.1 (Jul 2024) | Fastest, good error traces, Scala.js | Macro-based; generated code may trip WartRemover and `-source:future` checks |
| scala-parser-combinators | 2.5.0 (2.5.1-M1 Oct 2026) | Standard module, actively maintained, Scala.js | Slower, weaker error reporting; `Positional` relies on mutable positions |

cats-parse wins on purity and predictable backtracking, which matter more here than speed. Its
cats-core dependency gives us `NonEmptyList` and `Validated` for accumulating diagnostics, so the
public result type is `Either[NonEmptyList[Diagnostic], Requirement]` rather than a home-made
equivalent.

### Two-stage classification

A strict grammar alone can only report "failed at offset N", which can't produce the specific,
fixable diagnostics the spec requires. So classification has two stages:

1. **Lenient skeleton parse** (cats-parse, never fails): the input becomes a sequence of leading
   clauses (each a keyword in any case, its text, and whether a comma or `then` follows), an
   optional article, the system name, the modal word with its form (keyword in either case, or
   a weak modal verb), the response text, and the trailing punctuation, each with its span.
   Anything that doesn't fit becomes an "unrecognized" skeleton.
2. **Validation** (pure functions over the skeleton): each spec diagnostic is one independent
   check that returns zero or more diagnostics; results accumulate with `Validated`. If no check
   fires, the skeleton converts to a typed `Requirement`.

Rules the skeleton parse follows, recorded because they bound what the classifier accepts:

- Clause keywords (Where, While, When, If) are case-insensitive. A clause starts at the beginning
  of the input or after a comma followed by a keyword and more words; clause text runs to the
  next such comma, so other commas stay inside clause text. A keyword directly followed by a
  comma (as in "the order Where, While, then When or If") continues the previous clause, since a
  clause with no text can't be valid. Clause text still cannot contain a comma, a keyword, and
  further words (", when ready, "); such text is read as a new clause.
- The modal position is the first word after the system name. The system name is `the` plus the
  following words up to that word, and contains no comma.
- Whitespace between tokens may be any run of spaces or tabs; spans always refer to the original
  input.
- Spans are half-open `[start, end)` in UTF-16 code units, matching `String` indexing on the JVM
  and JavaScript.
- Case conversions use `Locale.ROOT`, so results don't depend on the default locale (Turkish
  lowercases "I" to a dotless "ı").
- Missing-article fixes (all added after the code review):
  - A replaceable determiner before the system name (a, an, this, that, these, those, my, our,
    your, its, their, his, her) is replaced with "the".
  - A quantifier (each, every, all, any, no, some, either, neither, another) gets a
    diagnostic with no fix. EARS names one system, and rewriting the quantifier would change
    the meaning.
  - Otherwise "the" is inserted. At the start of a sentence the first letter is lowercased
    only for an ordinary capitalized word, so acronyms ("API") and names ("OpenSpec") keep
    their case.
- Missing-then applies to an If clause anywhere among the leading clauses, not only the last.

Precedence between overlapping checks: a weak modal suppresses missing-modal (the spec words
missing-modal to exclude it), and unrecognized-structure is reported only when no other check
fires.

### Data model

- `SourceSpan(start, end)`, `Located[A](value, span)`.
- `Requirement(feature: Option[Located[String]], precondition: Option[Located[String]],
  trigger: Option[Trigger], system: Located[String], response: Located[String])`, where
  `enum Trigger { case Event(text); case Unwanted(text) }` (each spanned). Clause order and
  "at most one When or If" are enforced by the shape of the type, not by checks on it.
- `Template.of(requirement)`, with `enum Template { Ubiquitous, EventDriven,
  StateDriven, UnwantedBehavior, OptionalFeature, Complex }`, derived from which clauses exist:
  none means ubiquitous, exactly one means its own template, two or more mean complex.
- `enum Profile { Classic, OpenSpec }`, each knowing its modal keyword form.
- `Diagnostic(kind: DiagnosticKind, span: SourceSpan, message: String, fix: Option[Fix])`, with one
  `DiagnosticKind` case per spec requirement. `Fix` is a list of non-overlapping text edits;
  applying it to the input gives the corrected sentence the scenarios name.
- `requirement.render(profile): String` produces canonical text: first keyword capitalized,
  later ones lowercase, `, then` after an If clause, `The`/`the`, the profile's keyword, and a
  final period.
- API style is data plus extensions: the types above carry no methods; operations on a single
  value (`render`, `stripSpans`) are extension methods in its companion object, and
  constructors and entry points are companion functions (`Template.of`). There are no public
  top-level functions, because a caller's wildcard import (for example `zio.test.*`, which has
  a `render` package) would shadow them.

### CLI libraries

- **ZIO 2.1.27** for effects (file and stdin reading, exit codes), with a typed error ADT.
- **decline 2.6.2** (core only, not `decline-effect`) for arguments. It parses to
  `Either[Help, A]` purely. zio-cli 0.8.2 is still pre-1.0. mainargs 0.7.8 relies on default
  parameter values, which the `DefaultArguments` wart rejects in our code.
- **zio-json 1.1.1** for reading OpenSpec JSON and writing JSON output. Decoding returns
  `Either`, so no exceptions cross our code. upickle 4.4.3 and jsoniter-scala 2.42.2 throw on
  malformed input by default.
- **commonmark-java 0.31.0** for Markdown, with source spans enabled for line numbers. 0.32.0
  was published today; we take the previous release and can move up later. flexmark 0.64.8 is
  heavier and hasn't had a release since 2023. Sentences inside paragraphs and list items are
  split with the classifier's own sentence rule (a period followed by whitespace ends a
  sentence), so "sentence" means the same thing in the CLI and the library, and no JVM-only
  splitter is needed; code blocks and inline code are skipped.
- Laika 1.3.2 was considered so that a later Node CLI could share Markdown code (it builds for
  the JVM and Scala.js, but not Scala Native). An experiment showed its AST records no source
  positions for paragraphs or list items (only for unresolved and invalid elements), so it
  can't supply the line numbers the spec requires without a custom parser extension. Markdown
  for Node is left to the npm change; the CLI keeps Markdown extraction behind one function so
  the parser can be swapped locally.
- The OpenSpec input decoder accepts either JSON shape from Context and tells them apart by the
  `deltas` or `requirements` field.
- Locations: the line (1-based) for plain text and Markdown; change id, capability, and
  requirement name for OpenSpec change JSON; spec id and requirement name for spec JSON. Line
  input reports no column (decided after the code review):
  - **Plain text:** A column would only be the indentation.
  - **Markdown:** commonmark-java always merges decoded text nodes and their source spans, so
    columns drift after escapes and entities.
  - **Alternatives considered:**
    - **JetBrains Markdown:** It keeps source offsets but adds a Kotlin dependency.
    - **flexmark-java:** It maps offsets exactly but has been unmaintained since 2023, with
      unanswered security reports.
    - **A hand-written mapping:** It would be our own code, off by one for rare entities.
  - **What's not lost:** Diagnostic spans within the requirement text remain exact.
- Plain-text lines are blank when they contain only Unicode whitespace (`isBlank`), and text is
  trimmed with `strip`, so the two always agree.
- JSON output: `{"checked": n, "findings": [{"location": …, "requirement": …, "kind": …,
  "message": …, "span": {"start": …, "end": …}, "fix": "corrected sentence" | null}]}`.
- Exit status: 0 when nothing fails, 1 on findings, 2 on unreadable input.
- Text output keeps one line per finding: in input-derived text (corrected sentences, file and
  requirement names), each whitespace run, line breaks included, is shown as one space. JSON
  output keeps the exact text. (Added after the code review.)

### OpenSpec check

`scripts/check-openspec` keeps its two validation commands, then builds the CLI once with
`./mill show ears.cli.assembly` (fast when up to date), lists change and spec ids with
`openspec list --json` and `openspec list --specs --json` (read with jq), and pipes each
`openspec show … --json` into `java -jar <assembly> --format openspec`. Any nonzero status fails
the script. The Claude Code hook is unchanged.

### Testing

TDD from the specs: each scenario becomes a zio-test case written before the code that makes it
pass, and each diagnostic gets a witness sentence. Properties, in zio-test `check`:

- Totality: classification of any generated string returns without throwing and yields a tree
  or at least one diagnostic.
- Round trip: for generated requirements, `classify(render(r))` equals `r` ignoring spans.
- Fix soundness: applying a diagnostic's fix removes that diagnostic of that kind from the
  result.
- SourceSpan sanity: every span lies within the input, and a tree element's span covers exactly its
  text.

The CLI is tested through its pure core (inputs to findings and exit status) plus one
end-to-end test per input format that runs the ZIO program on a temporary file.

## Risks / Trade-offs

- [Clause text containing ", when " or similar is misread as a new clause] → documented above;
  the resulting diagnostic points at the spot, and the text can be reworded.
- [Requiring the article `the` rejects names like "OpenSpec SHALL …"] → strict by design for
  now; a later profile option can relax it.
- [cats-parse releases are infrequent] → the API we use is small and stable, and the skeleton
  parser is isolated behind the classifier's API, so swapping libraries stays local.
- [The hook gets slower: building the assembly and starting a JVM per change] → the assembly is
  cached by Mill and checking is fast; if latency becomes a problem, the CLI can accept several
  JSON documents in one run.
- [Our own specs must pass the checker once it's wired in] → the specs in this change were
  written to the templates, and the first task after wiring is to run the check over them.

## Migration Plan

None needed: new modules and one script step. To roll back, remove the EARS step from
`scripts/check-openspec`; the modules are unused elsewhere.

## Open Questions

- Resolved after implementation, for the standalone repository: the project is `ears-checker`
  (Maven `io.github.tsnee:ears-checker_3`, npm `ears-checker`), licensed CC0, first released
  as 0.1.0 with an unstable API. The packaging changes will carry this out.
