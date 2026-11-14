# Design

## Context

See proposal.md for why. Observations (2026-11-14, the owner's Mac, macOS 27.2), each checked without changing the repository:

- **Installed:** `openspec` on the PATH is Homebrew's 2.0.0 (`/opt/homebrew/bin/openspec`, the only one). Its user config (`openspec config list`) has `delivery: skills`, profile `custom`, and the 11 workflows the repository keeps. CI pins 1.17.0 (`.github/tools/package.json` and lockfile).
- **2.0.0** (its release notes, published 2026-11-11): skills go to `.agents/skills/`, and tools that don't read that folder, Claude Code among them, get links to it; the `/opsx:*` commands go (this repository has none since update-openspec); `openspec update` removes files older releases generated but keeps edited ones; `delivery` is no longer needed. The workflows themselves don't change. Same `engines` (`node >=22.12.0`) and the same dependencies, with the same ranges, as 1.17.0 (`npm view`), so CI's Node 22 job still qualifies.
- **`openspec update --dry-run`** in the repository: 34 paths change. It adds `.agents/skills/.openspec-target` and the 11 skills' `SKILL.md` there, deletes each `.claude/skills/<name>/SKILL.md`, and adds each `.claude/skills/<name>` itself. Nothing else.
- **Probe:** `openspec update` on a scratch git copy of the repository's `openspec/` and `.claude/` (the scratchpad, not the repository): each `.claude/skills/<name>` became a relative symbolic link, `../../.agents/skills/<name>`; the marker holds `claude own-files`; the skills' text differs from 1.17.0's in 120 lines across the 11 (in `openspec-propose`: `generatedBy` 2.0.0, and "invoked this skill by name" or "use the `openspec-apply-change` skill" where it said "slash command" or `/openspec-apply-change`).
- **References:** `.prettierignore` skips `.claude/` ("OpenSpec writes these skills"); CLAUDE.md's Workflow note says `openspec update` keeps skills only when the user config has `delivery: skills` and all 11 workflows. No script or workflow names `.claude/skills/` or `.agents/` (`grep`). (This said no other doc did either, but `CONTRIBUTING.md`'s "Through OpenSpec" paragraph names `.claude/skills/` and `delivery: both`: the grep printed it and it was misread; found by the code review, 2026-11-14, and CONTRIBUTING.md is now part of task 1.4.) `continuous-integration`'s "Pinned OpenSpec CLI" requires a pinned version, not a particular one.
- **Git:** `core.symlinks` isn't set here, so git checks out links as links on macOS and Linux. On Windows, with git's default there, a link checks out as a small text file holding its target; CI's only Windows job (the Coursier app check) never reads `.claude/`.

## Goals / Non-Goals

**Goals:**

- Local checks, the Claude Code hook, and CI all run OpenSpec 2.0.0.
- The repository holds exactly what 2.0's `openspec update` writes, so a later update changes only what OpenSpec changes.

**Non-Goals:**

- The `onboard` workflow, which 2.0 offers again: not asked for.
- Other tools' folders: only Claude Code is configured.

## Decisions

### 1. Take `openspec update`'s output as it is

- Run `openspec update` in the repository and commit its 34 paths unchanged. Editing the generated skills or replacing the links with copies would be undone by the next update.
- Check the result matches the dry run and the probe: the same 34 paths, links relative, the skills' text as in the probe.

### 2. CI's lockfile

- `.github/tools/package.json` pins `@fission-ai/openspec` at `2.0.0`; the lockfile is regenerated with `npm install --package-lock-only --ignore-scripts` and a temporary `--cache`, and CI installs it with `npm ci --ignore-scripts`, as now. Since 2.0.0's dependencies equal 1.17.0's, the lockfile should change only in OpenSpec's own entry, unless npm resolves a newer version within a range; any other change is reported before committing.

### 3. Prettier and CLAUDE.md

- `.prettierignore` adds `.agents/` beside `.claude/`, under the same comment, so `prettier --check '**/*.md'` doesn't reformat generated files.
- CLAUDE.md's note says the skills live in `.agents/skills/` with links from `.claude/skills/`, both written by `openspec update`, and that it keeps the skill set only when the user config lists all 11 workflows; the `delivery` clause goes.

### 4. Claude Code through the links

- Claude Code reads skills when a session starts, so the check is a fresh session in the repository once the update is made: the 11 `openspec-*` skills are listed and one loads (for example, `/openspec-explore`, then leaving it). This session can't show it, since its skills were read before the change.
- If Claude Code finds them, nothing more is needed. If it doesn't, the change stops before committing and the owner chooses: stay on 1.17.0 until OpenSpec or Claude Code fixes it (revert the update, keep CI's pin, and report it upstream), or keep 2.0 with the links replaced by copies, which each later `openspec update` would undo.

## Risks / Trade-offs

- **[A young release]** 2.0.0 is three days old. Its workflows are unchanged, the probe and dry run show exactly what changes, and every CI job that installs it runs the OpenSpec check with it.
- **[Links on Windows]** A Windows checkout without symlink support gets text files where the links are; nobody develops here on Windows, and no Windows job reads them.
- **[Skills read before the change]** This session, and any session open during the change, keeps the skills it read at start; the owner's next session picks up the new ones.

## Open Questions

- None.

## Assumptions checked and unchecked

Checked on 2026-11-14: every observation above. Not checked, each with what the plan does either way:

- That Claude Code follows the links to load a skill: needs a fresh session after the update (decision 4, both outcomes planned).
- That the regenerated lockfile changes only OpenSpec's entry: needs the regeneration (decision 2 reports any other change before committing).
