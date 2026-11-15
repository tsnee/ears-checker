# Design

## Context

See proposal.md for why. Observations (2026-11-14, the jar built from 0dcd40c, and the source):

- `--help` alone prints the usage text (15 lines) on standard output and exits 0. `--output json --help`, `--verbose --help`, `n.txt --help`, and `--output yaml --help` fail with `Unexpected option: --help`; `--help --output json` with `Unexpected option: --output`; `--help n.txt` with `Unexpected argument: n.txt`; all exit 2. `-h` fails with `Unexpected option: -h`. `-- --help` reads a file named `--help` (`Cannot read --help`, exit 2).
- `Settings.parseInvocation` parses with decline 2.6.2's `Command`, whose built-in help flag (`helpFlag = true`, the default of `Command.apply(name, header, helpFlag)`) is an alternative to the whole command (`orElse`), so it matches only when it's the only argument. A parse failure and a help request both come back as decline's `Help`; `parseInvocation` turns it into `Usage(text, requested = errors.isEmpty, errors)`, and `Cli.check` writes a requested usage to standard output with exit status 0, an invalid one to standard error with exit status 2 and an error log entry ("Invalid arguments", with the arguments and reasons).
- `Cli.loggerFor` decides `--verbose` from a successful parse only, so today a help request logs nothing.
- Tests of help: `SettingsSuite` ("the usage text lists --verbose"), `CliSuite` and `MainSuite` ("--help: exit code 0 and the usage text on standard output"), all with `--help` alone; `scripts/cli-cases` has `help` (`--help`) and `bad-arguments`. No test checks that `--help` fails elsewhere.
- The first version of this change found `--help` with its own scan of the arguments before decline parsed; the code review (2026-11-14) found that the scan duplicated decline (it treated a `--` given as an option's value as the end of the options), and that letting help win over files made `ears-check *` print the usage and exit 0, checking nothing, in a directory holding a file named `--help` (the jar, confirmed). This design replaces it.

## Goals / Non-Goals

**Goals:**

- `--help` anywhere among the options asks for help, parsed by decline like any other option.
- A stray file named `--help` never turns a check into a silent pass.

**Non-Goals:**

- `-h`, or any short option: none exists, and the owner chose to keep it so.
- Help winning over invalid arguments: they're reported as any invalid arguments are.
- Changing the usage text: it stays as it was, byte for byte.

## Decisions

### 1. `--help` is an ordinary flag that decline parses

- `Command(..., helpFlag = false)`, and a flag `--help` ("Display this help text.") among the options, beside `--verbose`, with partial visibility, so it's listed among the options but not in the usage line, as decline's own flag was (a test pins the line). decline then finds `--`, option values, and files as for every option: `--format --help` is a format named `--help` (invalid), `-- --help` a file.
- `parseInvocation` decides from decline's result, as a pure function: a parse failure is the invalid usage, as now; `--help` with no file is the requested usage; `--help` with a file is the invalid usage with the reason `--help can't be given with input files: leave out --help, or name a file called --help after --.`; anything else is the invocation.
- _Alternative: find `--help` before decline parses_ (this change's first version): it duplicates decline's knowledge of where the options end, and got it wrong for an option's value.
- _Alternative: another library_ (scopt, mainargs, case-app): decline does this once configured, and it already runs the same on the JVM, Node, and Scala Native.

### 2. Help with files is invalid

- So `ears-check *` that picks up a file named `--help` fails visibly (exit 2), as it does today, instead of checking nothing and passing: the owner's choice (2026-11-14), the one that fails closed for CI. The reason says how to name such a file.

### 3. A help request is logged

- `--verbose` is decided from decline's parse, help or not (`Settings.verbose`), and a help request logs an info entry, `Help requested`, with the arguments, so `--verbose --help` leaves a trace like every other run. Help with files is logged as invalid arguments, as now.

## Risks / Trade-offs

- [`--help` beside an invalid option no longer shows plain help] → It shows the usage with the reason, on standard error, exit 2, which says more.
- [A file named `--help` must follow `--`] → As for any file name starting with a dash; the rejection's reason says so.

## Assumptions checked and unchecked

Checked on 2026-11-14: every observation above, and `Command.apply`'s `helpFlag` parameter (`javap` on decline 2.6.2). The second review (2026-11-14) found that a visible flag adds `[--help]` to the usage line, where decline's own had partial visibility; ours has it too, and the text is as before.
