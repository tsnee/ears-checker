# Proposal

## Why

`ears-check` takes files and standard input, but a project's requirements usually live in a folder of Markdown documents. Checking them takes a shell glob, which differs between shells and Windows, misses subdirectories unless the shell has `**`, and silently checks nothing when it matches nothing. A directory given as an input is an error today (`Cannot read docs`). Roadmap item 10 asks for `ears-check docs/`, and the theme's second change (a CI snippet or GitHub Action) builds on it.

Roadmap item 10's first part, the first change of the "Check other projects" theme (branch `batch-check-other-projects`).

## What Changes

- An input that is a directory is checked as the requirement documents in it and in its subdirectories: every file whose name ends in `.md` (read as Markdown) or `.txt` (read as plain text), as if each were named on the command line; other files are left alone (the owner's choice, 2026-11-16).
- Entries whose names start with a period (`.git`, `.github`, `.notes.md`) are skipped, as are symbolic links and anything that isn't a file or a directory (a named pipe, a device). A walk can't loop, can't block on a pipe, and can't be led out of the directory: a crafted repository's `notes.md -> ~/.aws/credentials` would otherwise have its sentences quoted in CI logs. A link given by name on the command line is followed, as now.
- The files are checked in name order, the same on every build and file system: each directory's entries sorted by name, a subdirectory's files where its name sorts. A directory takes its place among the inputs, which keep the order they're given in.
- Each file found is named by the directory as given, a slash, and its path below it (`docs/billing/invoices.md`), in findings, messages, and JSON output.
- A directory that holds no file to check is an error: exit status 2 and `Cannot check docs: it holds no .md or .txt files`, so a mistyped or emptied folder can't pass a CI gate by checking nothing. **BREAKING** only in wording: a directory gave `Cannot read docs` before.
- A subdirectory that can't be listed is an unreadable input: `Cannot read docs/private`, a log entry with the platform's error, exit status 2, and the run stops there, as for a file.
- The options apply to every file found: `--format`, `--profile`, `--output`, and the size limit, which stays per file.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: new requirements for directory input (which files, hidden entries, links, other entries, order, names, a directory with nothing to check, a directory that can't be listed); "Unreadable input status" and "Unreadable input message" turn their "Directory given as a file" scenarios into a directory that can't be listed (renamed after archiving, since a delta can't rename a scenario) and gain scenarios for what's found in a directory.

## Impact

- `ears/cli`: `Reading` gains two functions, whether a path is a directory and a directory's entries with their kinds; `Main` (JVM and Scala Native, `java.nio.file`) and `NodeMain` (`node:fs`) supply them. Which entries are checked, their order, and their names are decided in shared, pure code; `Cli` walks the directories in order, reading each file as now.
- Tests: new shared tests with fake `Reading`s, a JVM test of the real listing (links, an unreadable directory), and `scripts/cli-cases` cases comparing the builds on a tree. `MainSuite`'s "a directory given as a file" expects the old message and changes, with the owner's approval; `CliSuite`'s fake `Reading`s take the new functions (a mechanical edit, asked of the owner first).
- README and the CLI's npm README (directories as input), CHANGELOG, roadmap; SECURITY.md (a directory walk follows no link and reads only regular `.md` and `.txt` files below the directory).
