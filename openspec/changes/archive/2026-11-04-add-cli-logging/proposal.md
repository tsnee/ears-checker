# Proposal

## Why

`ears-check` logs nothing. When a run fails, standard error carries
one short message ("Cannot read notes: Is a directory") and nothing
else: not the exception's type, not which format and profile were in
effect, not what was read before the failure. A successful run can't
be reconstructed at all. The code review of `add-coverage-measurement`
flagged this against the project's logging rules.

## What Changes

- **Log entries on standard error**, one plain-text line each, through
  ZIO's built-in logging with the default loggers replaced at the
  application's edge. Standard output still carries only results.
- **Warnings and errors by default.** A failure to read or parse an
  input, or invalid arguments, logs an error entry with full
  diagnostics: the input, its format and profile, the error's type and
  message, and the exit status.
- **`--verbose`** adds info and debug entries: the arguments, each
  input as it is read (name, format, profile, size, requirement
  count), and the outcome (findings, exit status), enough to
  reconstruct a successful run.
- **Docs.** The README's usage line and option list gain `--verbose`.

The default deliberately differs from the global rule that logging is
always on at every level: the owner chose warnings and errors by
default (2026-11-04), so a terminal, `scripts/check-openspec`, and the
Claude Code hook that relays its output stay readable.

Not in this change: JSON log lines, a log file, logging in the `ears`
library (it has no effects, so it logs nothing).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ears-cli`: adds the `--verbose` option and requirements for where
  log entries go, what each level shows by default and with
  `--verbose`, the diagnostics a failure logs, and the line format.

## Impact

- `ears/cli/src/.../Main.scala` (the logger at the edge, log calls
  around reading inputs and the outcome), `Settings.scala` (the flag),
  and a new pure `Logging.scala` (level choice and line format), with
  tests.
- `README.md`. No new dependencies.
- Default output changes only on failures: an error entry after the
  existing message.
