# Design

## Context

See proposal.md for why. The CLI today, in `ears/cli`:

```text
Settings.parse(args)
  : Either[Usage, Settings]
      Settings(file: Option[String],
               formatOption, profileOption,
               outputOption)
Main.program
  reads settings.file, or stdin
  -> Checker.outcome(settings, Either[String, String])
  -> Outcome(exitCode, stdout, stderr)
```

All per-input decisions are already extensions on one `Settings`:
`inputFormat` (from the option or the file name), `profile` (from the
option or the format), and `sourceName` (the file name, or `<stdin>`).
`Checker.outcome` turns the input text, or the reason it couldn't be
read, into candidates, classifies them, and renders the report.

Tests that depend on these shapes: `SettingsSuite` builds `Settings`
values and compares `Settings.parse` results; `CheckerSuite` calls
`Checker.outcome` with one `Settings` (14 tests); `MainSuite` runs
`Main.program` against real temporary files.

## Goals / Non-Goals

**Goals:**
- Every invocation that works today behaves exactly the same.
- `CheckerSuite` and `MainSuite` stay unchanged; `SettingsSuite`
  changes only in the expected value of its three parsing tests.
- At least one input per run is a fact the types carry.

**Non-Goals:**
- Reading files in parallel. Inputs are small and the cost being
  removed is JVM start-up, not reading.
- Mixing standard input with files (no `-` argument).
- Recursing into directories or expanding globs; the shell does that.

## Decisions

### 1. `Settings` stays one input; `parse` returns one per file

```scala
def parse(arguments: List[String])
    : Either[Usage, NonEmptyList[Settings]]
```

`Settings` keeps its four fields and its extensions, so everything
that is already decided per input stays where it is. `parse` copies
the shared options into one `Settings` per file argument, or returns
a single `Settings` with no file when there are none (standard
input). `NonEmptyList` comes from cats, already on the classpath
through decline, and says "at least one input" in the type.

Alternatives considered:
- **`Settings(files: List[String], ...)`** with a separate per-file
  view. More honest about shared options, but every test that builds
  a `Settings` (the helpers in both suites and most of
  `SettingsSuite`) would change, and the per-file extensions would
  move to the new type.
- **Keep `parse` and add `parseAll`.** No test edits, but two entry
  points, one of which can't express a valid command line.

The cost of this choice: `NonEmptyList[Settings]` could in principle
hold inconsistent `outputOption`s. Only `parse` builds one, from one
set of options, and the report takes the output format from the
first element.

### 2. Reading and parsing stop together; checking runs on the rest

A run stops at the first file that can't be read *or* parsed, and
must not read any file after it. Reading is an effect (in `Main`)
and parsing is pure (in `Checker`), so the two are interleaved per
file rather than done as two passes. Otherwise a parse error in the
first file and a missing second file would report the second.

`Checker` exposes the pure half:

```scala
def candidates(settings: Settings,
               content: String)
    : Either[String, List[Candidate]]

def outcome(
    found: Either[String,
      NonEmptyList[(Settings, List[Candidate])]]
): Outcome

def outcome(settings: Settings,
            input: Either[String, String])
    : Outcome =
  outcome(input
    .flatMap(candidates(settings, _))
    .map(c => NonEmptyList.one(settings -> c)))
```

`candidates` is today's private function made public. The combined
`outcome` gives:

```text
Left(why)   stderr why, stdout empty,
            status 2
Right(all)  each file classified under its
            own profile; stdout the findings
            in input order (text lines, or
            one JSON document whose "checked"
            sums every file); status 1 if any
            finding, else 0
```

The single-input overload keeps `CheckerSuite` as it is and runs the
same code, so the existing tests also cover the combined path for
one input.

### 3. `Main` reads and parses in order, failing fast

```scala
ZIO.foreach(all) { settings =>
  read(settings, stdin).flatMap(content =>
    ZIO.fromEither(
      Checker.candidates(settings, content))
  ).map(settings -> _)
}.either
```

`ZIO.foreach` is sequential and stops at the first failure, which
is exactly the required behavior. Standard input is read only for the
no-file `Settings`, as now.

### 4. The check script runs the checker once

```sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
set --
for change in ...; do
  f="$tmp/change-$change.json"
  if openspec show "$change" ... > "$f"
  then set -- "$@" "$f"
  else status=1
  fi
done
for spec in ...; do  # same, spec-$spec.json
done
ears_check "$@" || status=1
```

The `change-` and `spec-` prefixes keep a change and a spec with the
same name apart. The positional list keeps the order of today's
output (changes, then specs) without relying on glob order. A failed
`openspec show` marks the check failed, as today, and its file is
left out of the checker run: passing an empty file would stop the
checker at it and hide every finding in the others.

Added during implementation: `openspec show` reports its errors as
JSON on standard output, so they land in the file rather than on the
terminal. The script prints which change or spec failed, followed by
that file, to standard error; without this a failed show only set the
exit status. And with no changes and no specs at all, the script
skips the checker, which would otherwise wait on standard input.

### 5. Usage text

decline's `Opts.arguments[String]("file").orEmpty` gives
`[<file>...]` in the usage line; `Main`'s scaladoc changes to match.

## Risks / Trade-offs

- [`mktemp -d` must be writable inside the nono sandbox when the hook
  runs] → Run the script under the hook once during implementation;
  if `$TMPDIR` is denied, use a directory under `out/` instead, which
  Mill already owns and git ignores.
- [Text output from several files interleaves nothing, but a reader
  can't tell where one file ends] → Every line already starts with
  its file or change and requirement name.
- [One bad file hides every finding in the others] → Intended: status
  2 keeps meaning "nothing was checked". The check script never
  passes a file it failed to produce (Decision 4), so there one bad
  input can't hide the rest.

## Migration Plan

None for users: the command line is a superset of today's. The hook
picks up the new script on its next run.
