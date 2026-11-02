# Proposal

## Why

The EARS checker reads one file, or standard input, per run. So
`scripts/check-openspec` pipes each active change and each spec into a
separate run, and every run starts a fresh JVM: today one per change
plus four for the specs, each paying JVM and Scala start-up before
checking a handful of sentences. The cost grows with every capability,
and it runs after every Claude Code edit under `openspec/`.

This was finding #12 of the `add-ears-checker` code review, deferred to
packaging. It comes first now because it changes the command line, and
the command line should be settled before the checker is published:
release 0.1.0 is the interface early adopters will script against.

## How it fits together

Today, one run checks one input:

```text
FILE or stdin
  -> Settings (format, profile, output)
  -> read -> candidates -> classify
  -> Outcome (stdout, stderr, exit status)
```

After the change, one run checks every file it is given and
combines the results:

```text
FILE... (or stdin when none)
  -> one Settings per file, each inferring
     its own format and profile
  -> per file, in order: read -> candidates
       (the first file that can't be read or
        parsed stops the run)
  -> classify every file's candidates
  -> one Outcome:
       stopped  stderr: why; stdout: nothing;
                status 2
       else     stdout: findings, file by file;
                status 1 if any finding, else 0
```

From the command line:

```text
$ ears-check notes.md change.json
notes.md:4: missing-modal: ...
add-reminders/reminders/"Reminder sent": ...
$ echo $?
1
```

`--format`, `--profile` and `--output` still apply to the whole run.
With `--format` or `--profile` set, every file uses it; without them,
each file's own name decides, exactly as a single file does today.
JSON output stays one document: `checked` counts requirements across
all files, and `findings` lists them in the order the files were
given.

The check script then starts the checker once:

```text
before  openspec show X | ears-check     (per change and per spec)
after   openspec show X > tmp/X.json      (per change and per spec)
        ears-check tmp/*.json             (once)
```

Findings already name their change or spec from the JSON content, so
the report reads the same as before.

## What Changes

- **Several files per run.** `ears-check [options] [FILE...]`. With no
  file it still reads standard input; a run never mixes the two.
- **Per-file inference.** Format and default profile are worked out for
  each file from its own name, unless an option sets them for all.
- **Stop at the first unreadable file.** The first file that can't be
  read or parsed ends the run: its cause goes to standard error, no
  later file is read, no findings are reported (not even for earlier
  files), and the run exits with status 2. So status 2 still means
  "nothing was checked", as it does for one file today.
- **Check script.** `scripts/check-openspec` collects every change and
  spec as JSON in a temporary directory and runs the checker once.
- **Tests.** Existing tests stay as they are except the three
  `SettingsSuite` parsing tests, whose expected value becomes a list of
  settings (one per file). New behavior is written test-first.

Not in this change: packaging, the hook timeout, and the JDK-version
handling deferred from the same review.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ears-cli`: adds requirements for several input files (all checked
  in one run, each format inferred per file, findings in file order,
  the first unreadable file stopping the run with no findings), and
  adjusts the three exit-status requirements and the unreadable-input
  message so they cover several inputs.
- `openspec-check`: adds a requirement that the check starts the EARS
  checker once per run.

## Impact

- `ears/cli`: `Settings.parse` returns one `Settings` per file;
  `Checker` combines several inputs into one `Outcome`; `Main` reads
  every file. The `ears` library is unchanged.
- `scripts/check-openspec`: one checker run, using a temporary
  directory that is removed on exit.
- Command line: backward compatible. Every invocation that works
  today behaves the same.
- No new dependencies.
