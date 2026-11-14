# Tasks

All work is on branch `batch-openspec-2`, the "OpenSpec 2.0" theme (roadmap item 2), one draft pull request for the theme. The owner pushes from their own shell; Claude reads runs with `gh`. The plan commit also carries what the tables theme left uncommitted: the record of CI run 43607741578, roadmap item 8's note on two large-input crashes, and the work log's lines; main's CI run 43608967863 is recorded with the implementation.

## 1. The repository

- [x] 1.1 Run `openspec update` (decision 1); check with `git status` that exactly the dry run's 34 paths changed, with `readlink` that each `.claude/skills/<name>` is a link to `../../.agents/skills/<name>`, and that each skill's text equals the probe's (`diff -r` against the scratch copy, if it's still there, else the 120-line count of changed lines against 1.17.0's) (34 paths: 11 `SKILL.md` deleted, 12 files added under `.agents/skills/`, 11 links added; all 11 links `../../.agents/skills/<name>`; `diff -r` against the probe's skills empty.)
- [x] 1.2 Pin `@fission-ai/openspec` 2.0.0 in `.github/tools/package.json` and regenerate the lockfile (decision 2); check with `jq` that the lockfile resolves 2.0.0, and with `git diff` that only OpenSpec's entry changed (report any other change to the owner before going on) (Regenerated with npm 11.21.1 and a temporary cache: the lockfile resolves 2.0.0, still 142 packages; the only changes are the root's pin and OpenSpec's version, URL, and integrity.)
- [x] 1.3 `.prettierignore` skips `.agents/` (decision 3); check that `prettier --check '**/*.md'` passes and doesn't list anything under `.agents/` (Added beside `.claude/`, under the same comment; the check passes, and without the ignore file prettier would flag all 11 skills.)
- [x] 1.4 CLAUDE.md's Workflow note (decision 3); the roadmap item is marked in progress (moved to done at the archive). Verify with `prettier --check` and by reading the note back with `mq` (The note says where `openspec update` writes the skills and their links, to commit its output unedited, and that the skill set holds only when the user config lists all 11 workflows; the `delivery` clause is gone. Read back under `## Workflow` with `mq`; prettier passes. After the code review: the note no longer says both to commit the output unedited and to discard part of it; CLAUDE.md's Layout gains `.agents/skills/`; and CONTRIBUTING.md's "Through OpenSpec" paragraph, which still described 1.x's `.claude/skills/` and `delivery: both`, says the same as CLAUDE.md.)

## 2. Claude Code through the links

- [x] 2.1 The owner starts a fresh Claude Code session in the repository (decision 4) and reports whether the 11 `openspec-*` skills are listed and whether one loads; if they aren't, stop before committing and let the owner choose between the two ways decision 4 gives (2026-11-14, the owner: in a fresh session the skills are listed and `/openspec-explore` loads, through the links.)

## 3. Checks, review, commits

- [x] 3.1 Run `scripts/check-openspec` (with the local 2.0.0) and the CI tools install (`npm ci --ignore-scripts` with a temporary cache into a scratch copy of `.github/tools`, then its `openspec --version`); add a dated line to `docs/worklog.md`. No Scala changes, so the rest of the gate isn't needed (2026-11-14: `scripts/check-openspec` passed 34 items and the EARS check; the scratch install's `openspec --version` is 2.0.0; the work log has its line.)
- [x] 3.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix (Medium, 2026-11-14: six findings, all documentation or process, each confirmed against its line; nothing assumed the skills were files or broke on the links. The owner chose to fix five: CONTRIBUTING.md's paragraph still on 1.x, the design's wrong observation about it, CLAUDE.md's self-contradicting note, CLAUDE.md's Layout without `.agents/`, and the work log's stale "Next"; and, for the sixth, to commit the plan and the implementation together, since both were uncommitted when the plan's results were recorded.)
- [ ] 3.3 Commit the plan and the implementation together (the owner's choice, 2026-11-14), then the archive; the owner pushes. Report the CI run's result and minutes from the records, and that its jobs installed OpenSpec 2.0.0

## Workflow follow-up

- After CI passes, archive the change on the same branch (no specs to sync) for the owner to push; the theme's pull request is then reviewed and merged as one.
