# Proposal

## Why

`ears-check --output json` is the command line's interface for programs (CI annotations,
editors, scripts), but the only description of it is one README line and a spec that says only
"one JSON document". A consumer has to reverse-engineer its fields, can't tell what may change,
and can't detect when it does. 0.2.0 is the first release on Node and as a native binary, so its
JSON is about to gain consumers; the contract should be written down, versioned, and tested
before then.

## What Changes

- The JSON document gains a format version, `"version": 1`, as its first field. Adding a field
  is not a breaking change for a consumer that ignores unknown fields, which the reference says
  consumers must do.
- It also gains `"checker"`, the ears-checker release that wrote it (`0.2.0`, or `0.2.0-SNAPSHOT`
  between releases), so a consumer can tell which release's findings it has.
- `docs/json-output.md`: a reference for the document: every field and its type, the three
  location types with an example of each (a plain-text or Markdown line, a change requirement,
  a spec requirement), what spans count (UTF-16 code units in the requirement's text), what's
  stable within version 1 and what can change without a new version (new fields, new diagnostic
  kinds, message wording, how a requirement's text is derived and what a span covers), and
  when there's no document at all (exit status 2, `--help`).
- A short "JSON output" section in the README pointing to it.
- Requirements in the `ears-cli` spec for the document's fields and version, so tests pin them:
  tests that compare the whole document for each location type, and that a finding without a fix
  has `"fix": null`.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `ears-cli`: the JSON output requirement gains a scenario for several inputs (one document,
  one line break); new requirements cover the document's version, the release that wrote it,
  each finding's fields, spans, each location type's fields (standard input included), and a
  `null` fix when there is none.

## Impact

- `ears/cli/src/.../Checker.scala`: the `version` and `checker` fields; `build.mill`: a
  generated `CheckerBuild.version` for the JVM, Node, and native builds.
- `ears/cli/test/src/.../CheckerSuite.scala`: new tests of the whole document. No existing test
  changes: they check fragments with `contains`.
- Docs: `docs/json-output.md` (new), the README, `CHANGELOG.md` (Unreleased), the roadmap.
- The same output on the JVM, Node, and the native binary, as `check-npm-cli` and
  `check-native-cli` already compare.
