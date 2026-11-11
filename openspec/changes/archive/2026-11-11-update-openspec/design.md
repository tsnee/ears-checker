# Design

## Context

Observations (2026-11-11, macOS 27.2):

- **Installed:** `openspec` on this session's PATH is nvm's 1.16.0 (`~/.nvm/versions/node/v25.2.1/bin/openspec`), ahead of Homebrew's 1.17.0 (`/opt/homebrew/bin/openspec`). CI pins 1.16.0 (`.github/tools/package.json` and lockfile).
- **1.17.0** (its release notes): `openspec update` upgrades from any release in one run and announces that command files go away in 2.0; no change to `validate`, specs, or changes. Same `engines` (`node >=22.12.0`) and dependencies as 1.16.0 (`npm view`), so CI's Node 22 job still qualifies.
- **Probe, in a scratch worktree of a42995f, with Homebrew's 1.17.0:**
  - `openspec update` changed one line in each of the 11 skills (`generatedBy: "1.16.0"` → `"1.17.0"`) and printed the 2.0 notice.
  - `openspec validate --all --strict` passed 12 items, and with `--archived` 24.
  - With a scratch copy of the user config (`XDG_CONFIG_HOME`) set to `delivery: skills`, `openspec update` removed the 11 files in `.claude/commands/opsx/` and rewrote the skills' references to the `/openspec-*` names (37 lines changed in all); nothing else changed.
- **User config:** `openspec config path` is `~/.config/openspec/config.json` (`delivery: both` today), read-only in Claude's sandbox; `delivery` applies to every project.
- **References:** CLAUDE.md's Workflow section names `/opsx:propose`, `/opsx:new`, `/opsx:apply`, `/opsx:verify`, and `/opsx:archive`; no spec, script, or other doc does (`grep`).

## Goals / Non-Goals

**Goals:**

- Local checks, the Claude Code hook, and CI all run OpenSpec 1.17.0.
- The workflows are invoked as skills, ahead of OpenSpec 2.0.

**Non-Goals:**

- The `onboard` workflow 1.17.0 offers: not asked for.
- Any other project's update: they pick up `delivery: skills` at their own next `openspec update`.

## Decisions

### 1. One local install: Homebrew's

- The owner uninstalls nvm's global copy (`npm uninstall -g @fission-ai/openspec`), so `/opt/homebrew/bin/openspec` is the only one, kept current by `brew upgrade`. Upgrading nvm's copy instead would leave two installs to keep in step (the owner's choice, 2026-11-11).

### 2. Skills only

- The owner runs `openspec config set delivery skills`; Claude then runs `openspec update` in the repository and checks the result matches the probe: 11 skills refreshed, 11 command files removed, nothing else.
- The owner then invokes the workflows as `/openspec-propose`, `/openspec-apply-change`, `/openspec-verify-change`, `/openspec-archive-change`, and so on.

### 3. CI's lockfile

- `.github/tools/package.json` pins `@fission-ai/openspec` at `1.17.0`; the lockfile is regenerated with `npm install --package-lock-only --ignore-scripts` and a temporary `--cache`, and CI installs it with `npm ci --ignore-scripts`, as now.

## Risks / Trade-offs

- **[A young release]** 1.17.0 is two days old. Its changes are in `update` only, the probe validated everything with it, and CI checks it on every job that installs it.
- **[Other projects]** `delivery: skills` is global: another project's next `openspec update` removes its command files too. The owner chose that.

## Open Questions

- None.
