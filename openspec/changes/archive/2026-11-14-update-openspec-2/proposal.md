# Proposal

## Why

OpenSpec 2.0.0 came out on 2026-11-11, and the owner's machine already runs it (`/opt/homebrew/bin/openspec`), while CI pins 1.17.0 (`.github/tools/package-lock.json`), so the local OpenSpec check and CI's can drift apart; CLAUDE.md says the two should match. 2.0 also moves the skills OpenSpec writes to `.agents/skills/`, with links from `.claude/skills/`, so the repository's next `openspec update` changes 34 paths whether or not this change makes it. The owner chose this as the theme after tables (2026-11-13, roadmap item 2).

## What Changes

- CI's OpenSpec CLI becomes 2.0.0: `.github/tools/package.json` and its lockfile.
- `openspec update`'s output is committed: the 11 skills move to `.agents/skills/` (with OpenSpec's `.openspec-target` marker), and each `.claude/skills/<name>` becomes a relative link to it, which is how Claude Code finds them. Their text changes slightly (120 lines across the 11, mostly wording about invoking a skill rather than a slash command).
- `.prettierignore` skips `.agents/`, as it skips `.claude/`, so the generated skills keep OpenSpec's formatting.
- CLAUDE.md's Workflow note on `openspec update` no longer depends on the `delivery` setting, which 2.0 doesn't need, and says where the skills live.
- No spec changes: `continuous-integration`'s "Pinned OpenSpec CLI" requires a pinned version, not a particular one, and no requirement covers where the skills live.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- None.

## Impact

- `.github/tools/package.json`, `.github/tools/package-lock.json`.
- `.agents/skills/` (11 skills and a marker, new), `.claude/skills/` (11 directories become links).
- `.prettierignore`, CLAUDE.md, the roadmap, the work log.
- Claude Code in this repository: it must still find the 11 skills through the links, checked in a fresh session.
