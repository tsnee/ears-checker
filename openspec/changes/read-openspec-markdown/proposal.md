# Proposal

## Why

OpenSpec keeps its specs and its changes' delta specs as Markdown (`openspec/specs/<capability>/spec.md`, `openspec/changes/<change>/specs/<capability>/spec.md`), but `ears-check` reads them as ordinary Markdown: every sentence with a modal keyword is a requirement, so the scenarios' `WHEN`/`THEN` lines are checked too (27 false findings on this repository's `ears-cli` spec with `--profile openspec`, 2026-11-16), and with `check-directories` a walk over a project reaches them, and the proposals, designs, and task lists beside them. Today the only correct route is OpenSpec's JSON (`openspec show --json`), which needs the OpenSpec CLI. The owner chose (2026-11-16) to read OpenSpec's Markdown natively.

Roadmap item 10's second change, in the "Check other projects" theme (branch `batch-check-other-projects`, PR #30).

## What Changes

- A new input format, OpenSpec Markdown, read by OpenSpec's own rules: in a spec, each requirement under its `Requirements` section; in a delta spec, each requirement under `## ADDED Requirements` and `## MODIFIED Requirements`. A requirement's text is what OpenSpec takes as its text (the lines between its heading and the next heading, without blank lines, fenced code, or `**Label**:` lines), so exactly what `openspec show --json` gives: checked as one requirement, with the OpenSpec profile by default, located at the line its text starts on. Scenarios, purposes, removed and renamed requirements, and everything else are not checked.
- It's inferred from the path: a `spec.md` under `openspec/specs/`, under `openspec/changes/<change>/specs/`, or under an archived change's `specs/`. `--format openspec-markdown` selects it for any file.
- In a directory walk, inside a directory named `openspec`, only those spec files are checked: proposals, designs, task lists, and `config.yaml` are left alone, and the archive (`openspec/changes/archive`) is skipped unless it's given itself.
- A directory with nothing to check names what it looked for: `Cannot check openspec: it holds no OpenSpec spec files`, beside `Cannot check docs: it holds no .md or .txt files`.
- `scripts/check-openspec` keeps checking through OpenSpec's JSON, unchanged. `scripts/cli-cases`, which already writes each of this repository's specs as JSON with `openspec show`, also checks that the native reading finds as many requirements in each spec's Markdown, so a change in OpenSpec's rules shows up in CI.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: new requirements for the OpenSpec Markdown format (inferred from the path, the format option, which requirements and what text, the location, the walk inside `openspec`); "Default profile for OpenSpec input" covers OpenSpec Markdown; "Directory input" excludes what's inside `openspec`; "Directory with nothing to check message" names what was looked for.

## Impact

- `ears/cli`: a shared, pure reader of OpenSpec's Markdown (`OpenSpecMarkdown`, line-based, as OpenSpec reads it, not through the Markdown facade); `Settings` (the format, its inference from the path, the profile); `Checker` (parsing the new format); `DirectoryWalk` (what's checked inside `openspec`, the message). The Markdown facade (`ears/markdown`) doesn't change.
- `CLAUDE.md`: the rule that the checker reads Markdown only through the facade gets its exception, OpenSpec's spec format, read by OpenSpec's line rules.
- Tests: the reader's rules (sections, fences, labels, the title fallback, deltas), the inference, the walk, and `scripts/cli-cases` cases comparing the builds on a small OpenSpec tree, and its count against OpenSpec's JSON for each of this repository's specs.
- README and the CLI's npm README (OpenSpec's Markdown as input), CHANGELOG, roadmap; `docs/json-output.md` is unchanged (locations are lines).
