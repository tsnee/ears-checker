# Proposal

## Why

`--help` works only as the whole command line: with the jar on 2026-11-14, `ears-check --output json --help`, `ears-check --verbose --help`, and even `ears-check --help --output json` print "Unexpected option" or "Unexpected argument" and exit 2, where other programs accept `--help` among other options. The README documents a limit ("For now it works only as the first argument"), and no requirement covers `--help` at all. Roadmap item 5, the second change of the "CLI messages and output" theme.

## What Changes

- `--help` among the options, wherever it comes, prints the usage text on standard output and exits 0, reading no input, as long as the arguments name no input file.
- `--help` together with an input file is invalid arguments: the usage on standard error, exit status 2, nothing read, with a reason saying how to give a file named `--help` (after `--`). So a glob that picks up a file named `--help` (`ears-check *` → `--help notes.txt`) fails visibly, as it does today, rather than checking nothing and passing: the owner chose this on 2026-11-14, after the code review found that "help wins" would let a stray file silently pass a CI check.
- `--help` beside an invalid option is an ordinary usage error (exit 2, with the reason), as any invalid argument is. After `--`, `--help` stays a file name.
- `-h` isn't added: no option has a short form, and the owner chose to keep it so (2026-11-14).
- The README's note on `--help` loses its limit and says how it combines with files.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: requirements for `--help`: its usage text on standard output, its exit status, that it reads no input, that it can't be combined with input files, and that after `--` it's a file name.

## Impact

- `ears/cli`: `Settings` turns off decline's built-in help flag and adds `--help` as an ordinary flag in its own options, so decline parses it like any other; a help request is then a result of the parse, which `Cli` answers with the usage text, or rejects when files are named.
- Tests: new tests per position and for help with files; no existing test checks the old limit. `scripts/cli-cases` cases compare the three builds.
- README, the npm package's README, `docs/json-output.md`, and CHANGELOG.
