# Proposal

## Why

`ears-check` reads each input whole before checking it, so an untrusted input as large as the machine allows is read into memory, and the Markdown parser's memory grows with it (roadmap item 9; SECURITY.md lists the missing limit as known). A limit on nesting wouldn't help, since memory follows the input's size; a limit on bytes does. It also keeps out two crashes the last theme's security review (2026-11-14) noted below its reporting bar: records over about 1 GB overflow `CmarkTree.enter`'s `literalStart + length` (an `Int`), and on Scala Native records over 2 GiB make `strlen(...).toInt` negative in `CmarkGfm.records`. And a standard input that never ends (`yes | ears-check`) is read until memory runs out.

Roadmap item 9, the first change of the "Input limits" theme (branch `batch-input-limits`).

## What Changes

- Each input (a file or standard input) has a size limit in bytes: 10 MiB (10,485,760 bytes) by default, set with a new option `--max-input-bytes <n>`, from 1 to 100 MiB (104,857,600 bytes).
- Every build reads at most the limit and one more byte of an input, so an input over the limit is never held whole, and a standard input that doesn't end is stopped there.
- An input over the limit is an input that can't be read: exit status 2, a message on standard error (`Cannot read notes.md: more than 10485760 bytes; --max-input-bytes raises the limit`), and an error log entry with the limit, as other read failures have. Inputs are read in order and the first failure stops the run, as now.
- `--max-input-bytes` with a value that isn't a whole number from 1 to 104,857,600 makes the arguments invalid (status 2 and the usage text).
- SECURITY.md says the limit exists, and its stale lines are brought up to date (the native binary compiles cmark-gfm in rather than loading an installed cmark, and every output now escapes characters outside the English character set).

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ears-cli`: new requirements for the size limit, its message, its default, and the option's value; "Unreadable input status" gains a scenario for an input over the limit.

## Impact

- `ears/cli`: `Reading` (each platform reads at most the bytes asked for, as bytes), `Main` (JVM and Scala Native: `InputStream.readNBytes`), `NodeMain` (`fs.readSync` in 64 KiB chunks), `Settings` (the option), `Cli` (asks for the limit and one byte, checks and decodes it, and gives the message, through `ReadFailure`), and `Bounded`, the shared, pure check.
- Tests: new tests for the check, the option, and the message; `CliSuite`'s fake `Reading`s take the new parameter (a mechanical edit, asked of the owner first); `scripts/cli-cases` cases compare the builds, among them a standard input that never ends.
- README (the option and the limit), SECURITY.md, CHANGELOG, roadmap.
