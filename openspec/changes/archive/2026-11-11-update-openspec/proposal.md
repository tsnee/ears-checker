# Proposal

## Why

OpenSpec 1.17.0 came out on 2026-11-09. CI still pins 1.16.0 (`.github/tools/package-lock.json`), this session's `openspec` is nvm's 1.16.0, and the owner's Homebrew copy is 1.17.0, so local and CI checks can drift apart. 1.17.0 also announces that command files (the `/opsx:*` commands) go away in OpenSpec 2.0, with the skills doing the same work. The owner chose to update now rather than wait for the release to age, and to switch to skills only in the same change (2026-11-11).

## What Changes

- CI's OpenSpec CLI becomes 1.17.0: `.github/tools/package.json` and its lockfile.
- One local `openspec`: the owner uninstalls nvm's global copy, leaving Homebrew's 1.17.0.
- Skills only: the owner sets OpenSpec's `delivery` to `skills` (user config, which Claude can't write); `openspec update` then removes `.claude/commands/opsx/` and refreshes the 11 skills, whose text names `/openspec-propose`, `/openspec-apply-change`, and so on.
- CLAUDE.md's Workflow section names the skills instead of `/opsx:*`.
- No spec changes: no requirement covers OpenSpec's version or how its workflows are invoked.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- None.

## Impact

- `.github/tools/package.json`, `.github/tools/package-lock.json`.
- `.claude/skills/` (11 files refreshed), `.claude/commands/opsx/` (11 files removed).
- CLAUDE.md, the roadmap, the work log.
- The owner's machine: nvm's global `@fission-ai/openspec` removed, and `delivery: skills` in `~/.config/openspec/config.json`, which every project picks up at its next `openspec update`.
