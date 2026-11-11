# Design

## Context

Observations (2026-11-07, from the jar built at `d3bffcc`):

- **The document today:** one line, newline-terminated, on standard output: `{"checked":<n>,"findings":[…]}`. Each finding is `{"location":…,"requirement":…,"kind":…,"message":…,"span":{"start":…,"end":…},"fix":…}`, in that order (`Checker.report`, built as a zio-json AST, which keeps insertion order). Non-ASCII text is written as UTF-8, not escaped.
- **Locations:** `{"type":"line","file","line"}` for plain text, Markdown, and standard input (whose `file` is `<stdin>`, from `Settings.sourceName`); `{"type":"change","change", "capability","requirement"}`; `{"type":"spec","spec","requirement"}`.
- **Spans:** `SourceSpan` is a half-open range of UTF-16 code units in the classified text, the finding's `requirement` (`ears/src/.../Model.scala`). "The scheduler should list upcoming events." gives `weak-modal` at `{"start":14,"end":20}`; with "📅" after "The", `{17,23}`. JavaScript and Java index strings this way; Python and Rust don't.
- **Fixes:** `null` when the diagnostic has none (`missing-modal` gives `"fix":null`).
- **Kinds:** 14, from `DiagnosticKind.kebabName`, spelled out so a renamed case can't change output: missing-modal, wrong-case, multiple-responses, weak-modal, empty-response, clause-order, repeated-clause, conflicting-triggers, missing-then, missing-comma, missing-article, missing-period, multiple-sentences, unrecognized-structure.
- **No document:** an unreadable or unparsable input gives exit status 2, a message on standard error, and nothing on standard output; empty input gives `{"checked":0,"findings":[]}` and 0. `--help` prints the usage text (not JSON) only when it's the first argument: after `--output json` it's "Unexpected option: --help" and status 2, though the README promises status 0 for `--help`. That's a separate bug, left out of this change and on the roadmap.
- **Tests:** `CheckerSuite` checks fragments with `contains` (`"checked":1`, `"findings":[]`, the kind and fix); nothing checks a whole document or a location object. Adding a field breaks no existing test. `scripts/cli-cases` compares the JVM's JSON with Node's and the native binary's byte for byte on three cases.

## Goals / Non-Goals

**Goals:**

- A reference a consumer can code against, with what's stable and what isn't.
- A version in the document, so a consumer can refuse a format it doesn't know.
- Tests that pin the whole document for each location type.

**Non-Goals:**

- Changing any existing field, the text output, or the npm library's `classify` result (its own TypeScript declarations document it).
- A JSON Schema file (see decision 4).
- Fixing `--help`'s position (on the roadmap).

## Decisions

### 1. `"version": 1`, first

- The document becomes `{"version":1,"checked":…,"findings":[…]}`. First, so a reader can see it at a glance; consumers are told not to depend on field order.
- The version changes only when a field is removed, renamed, or changes meaning or type. Adding a field, a location type's field, or a diagnostic kind doesn't change it: consumers must ignore unknown fields and treat an unknown `kind` as a finding like any other. A new location `type` would change the version, since a consumer can't read a location it doesn't know.
- An integer, not a semver string: there's only one question to answer ("can I read this?").

### 2. Spans stay in UTF-16 code units

- That's what the library, the npm package, Java, and JavaScript use, and the text output never shows spans. The reference says so plainly and shows the conversion for code-point languages (Python: encode the requirement as UTF-16-LE and slice by `2 * offset`). Changing units would change meaning, so a new version.

### 3. The reference: `docs/json-output.md`

- Sections: the document, its fields and types (a table each for the document, a finding, and each location type), one example per location type, spans, `fix` and `null`, kinds (the 14, each described with whether it can have a fix, since the README doesn't describe them), stability within version 1, and when there's no document. The examples are copied from real runs of the jar during apply, and the tests in decision 5 assert the same documents, so they can't drift silently.
- The README gets a short "JSON output" subsection under the command line, with one example and a link.

### 4. No JSON Schema yet

- A schema would let consumers validate, but it's a second description to keep in step, and testing output against it needs a validator library in the tests. The reference plus whole- document tests are enough for one small document. Revisit if a consumer asks.

### 5. Tests

- New `CheckerSuite` tests compare the whole document as an exact string, which also pins field order (the reference doesn't promise order; the tests are stricter on purpose): one per location type (line, change, spec), standard input's `<stdin>`, no findings, several inputs, and a fix of `null`; a property that every document starts with the version and the checker and ends with one line break; and that `CheckerBuild.version` has the build's version form.
- The emoji span case runs on every platform through the shared test sources.

### 6. `"checker"`: the release that wrote the document

- Within a format version, findings can change between releases (decision 7), so the document names its release, as SARIF names its tool's version (`tool.driver.version`). Second, after `version`: `{"version":1,"checker":"0.2.0",…}`.
- The value is `publishVersion`, the Maven version: `0.2.0` in a release, `0.2.0-SNAPSHOT` between releases, the same on the JVM, Node (whose npm version says `-dev`), and natively, so the platforms' output stays identical. `build.mill` generates `CheckerBuild.version` into `ears.cli`, `ears.cli.js`, and `ears.cli.native`, a constant, so `Checker` stays pure.
- Named `CheckerBuild`, not `BuildInfo`: zio-json has a `BuildInfo`, and `Checker.scala`'s `import zio.json.*` takes precedence over a same-package object in another file. The first try wrote zio-json's version, `1.1.1`; the whole-document tests caught it.

### 7. What version 1 covers

- The version covers the document's shape and the meaning of its fields. What fills them can change in any release within version 1: which findings and fixes a requirement gets, how a requirement's text is taken from its input (so `requirement`, spans, and `checked`), and how much of `requirement` a kind's span covers. That's the usual split: SARIF versions its schema while results vary with the tool, and ESLint's JSON has no version while rules change what they report. This release's inline-code change is an example: it changed some requirements' text, and so their spans and verdicts, with no change to the document's form.
- What doesn't change: a span always indexes the same finding's `requirement` in UTF-16 code units, `requirement` is the exact text classified, and `line` is the line it starts on. The CHANGELOG says when derivation or spans change, and `checker` says which release wrote a document. Chosen by the owner, 2026-11-07.

## Risks / Trade-offs

- **[A consumer that rejects unknown fields]** breaks on `version`. None is known (no JSON consumers are published); the CHANGELOG lists it under Changed.
- **[Docs drift]** The reference's examples are text; decision 3's tests check the same documents, but not the Markdown. Apply copies each example from a real run.

## Open Questions

- None. `--help` after another option failing with status 2 (observed above) went on the roadmap's Later list, at the owner's choice (2026-11-07).
