# Tasks

All work is on branch `batch-check-other-projects`, the "Check other projects" theme (roadmap item 10), PR #30. The owner pushes from their own shell; Claude reads runs with `gh`. Test-first: each behavior's test is written and seen failing before the code that makes it pass. Editing, deleting, skipping, or loosening an existing test is asked of the owner first, with the requirement it covers. After each task, every changed file is scanned for raw control, format, and separator characters by general category.

## 1. The reader

- [x] 1.1 Tests first, in `ears/cli/test/src` (every platform), for a shared, pure `OpenSpecMarkdown` reader (design decision 1, the rules in Context): a spec's `Requirements` section and its child headers (any case, any level, a closing `#` run, a child header without `Requirement:`); a delta's ADDED and MODIFIED sections (any case) and their `### Requirement:` blocks, with REMOVED and RENAMED left out; each text as OpenSpec takes it (lines trimmed and joined with `\n`, blank lines and fenced lines of both kinds skipped, a backtick opener with a backtick in its info string not a fence, label lines dropped unless alone, the header's title with its `Requirement:` prefix when there's no text); the line each text starts on; CRLF and a byte-order mark; nothing else read (scenarios, purpose). Seen failing on the JVM and Node
- [x] 1.2 `OpenSpecMarkdown` in `ears/cli/src`, with comments citing the OpenSpec 2.0.0 rule each part follows. Verify `./mill ears.cli.test ears.cli.js.test` pass

## 2. The format, the paths, and the walk

- [x] 2.1 Tests first (every platform): `OpenSpecPaths` (a spec, a delta, an archived delta, a nested capability, `\` separators, a proposal, a `spec.md` outside `openspec`, a path inside `openspec`); `SettingsSuite`, the inference and `--format openspec-markdown`, its profile; `DirectoryWalkSuite`, the walk inside `openspec` (only spec files, the archive skipped when found and walked when given, the message naming OpenSpec spec files); `CliSuite`, a spec and a delta end to end with fakes, located by line, and a walk over an OpenSpec tree. Seen failing on the JVM and Node. If any existing test needs editing, ask the owner first
- [x] 2.2 The code (decisions 2 to 5): `InputFormat.OpenSpecMarkdown`, inferred in `Settings.inputFormat` before `.md`, `--format openspec-markdown` with its help, the OpenSpec profile by default, `openspec-markdown` in the log; `Checker` reads it with `OpenSpecMarkdown`; `OpenSpecPaths`; `DirectoryWalk`'s choices inside `openspec`, the `Archive` reason (logged), and the message. Verify `./mill ears.cli.test ears.cli.js.test` pass and `ears.cli`'s coverage minimums hold
- [x] 2.3 `scripts/cli-cases`: an OpenSpec tree (a spec with a passing and a failing requirement and a scenario, an active change with a proposal and a delta, an archived change, `config.yaml`) compared across the builds as a walk and file by file, text and JSON; and decision 6's check that the jar finds as many requirements in each of this repository's specs' Markdown as in its JSON. Verify `scripts/check-npm-cli`, `scripts/check-native-cli`, and `scripts/check-coursier-app` pass
- [x] 2.4 Docs: README and `ears/cli/js/package/README.md` (OpenSpec's Markdown: what's read and how it's recognized, `--format openspec-markdown`, the walk inside `openspec`, the JSON route still there), CLAUDE.md (the facade rule's exception: OpenSpec's spec format, read by OpenSpec's line rules), CHANGELOG (Added), and roadmap item 10 (this change done). Verify `prettier --check '**/*.md'` and `./mill ears.releaseNotes.run check CHANGELOG.md`

## 3. Checks, review, commits

- [x] 3.1 Run the gate (CLAUDE.md's list) and report coverage from `scripts/coverage-records`; add a dated line to `docs/worklog.md`
- [x] 3.2 Run `/code-review` in a subagent on this change; the owner chooses which findings to fix
- [x] 3.5 The code review's fixes the owner chose (2026-11-17): JavaScript's whitespace and `.` in every pattern, as character scans; section titles compared with `equalsIgnoreCase`; delta sections from the one header list; each text's end by binary search; an OpenSpec directory is an `openspec` directory or below its `specs` or `changes`, not anything below one named so; the nothing-to-check message names both kinds of file after a walk through `openspec`. Known limitations recorded in the design (a spec with no recognized section, walking from inside `openspec/`, `--format openspec-markdown` on a directory). Tests for each, and the jar's counts against `openspec show` on all 37 archived changes again (344 requirements, no difference). Verify the tests on the JVM and Node, the coverage minimums, and the three CLI comparison scripts
- [x] 3.3 Commit the plan and the implementation together (with the CI-run record of 44072397625); the owner pushes. Report the CI run's result and minutes from the records
- [ ] 3.4 Run `/openspec-verify-change` and report it before archiving

## Workflow follow-up

- Archive the change (syncing `ears-cli`), rerun `scripts/check-openspec`, and commit; the theme's third change (a CI snippet or GitHub Action) follows, then the theme's security review in a subagent before PR #30 merges.
