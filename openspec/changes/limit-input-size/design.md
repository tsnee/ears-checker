# Design

## Context

See proposal.md for why. Observations (2026-11-15, the source on `batch-input-limits` at 77ce18f, and scratchpad probes):

- `Reading` (shared) is the platforms' seam: `file: String => IO[(ReadFailure, String), String]` and `standardInput: IO[(ReadFailure, String), String]`, each failing with the kind of failure and the platform's description. `Cli.read` logs a failure (`error`, `exit`, and a detail per kind) and turns it into `ReadFailure.describe`'s message; `ReadFailure` has `Unreadable`, `NotUtf8`, and `OtherEncoding`.
- The JVM and Scala Native share `Main` (`ears/cli/src-jvm`): a file is read whole with `Files.readAllBytes`, standard input whole with `InputStream.readAllBytes`, and each is decoded with the shared `Utf8.decode`. Node (`NodeMain`) reads both whole with `fs.readFileSync` (a path, or descriptor 0) and decodes with `Utf8.decode`.
- `Cli.check` loads the inputs in order with `ZIO.foreach`, stopping at the first failure; any read or parse failure gives exit status 2 (`Unreadable input status`).
- Probes (scratchpad): `InputStream.readNBytes(limit + 1)` on a `Files.newInputStream` and on `System.in` returns at most `limit + 1` bytes on JDK 27 and on Scala Native 0.5.12, from a 5,000-byte file and a 3,000-byte pipe with a limit of 1,024; on Node 25.2.1, a loop of `fs.readSync` into a `limit + 1` buffer does the same for a file (opened with `fs.openSync`) and for descriptor 0, and returns fewer bytes when standard input ends first.
- Options are decline `Opts` in `Settings`; invalid arguments give the usage text with the reasons, escaped (`Visible`), and status 2 before any input is read.
- `CliSuite` builds fake `Reading`s in seven places (`Reading(_ => …, …)`), which change shape when the functions take the limit.
- SECURITY.md's scope still says the native binary loads an installed cmark (it compiles cmark-gfm in since read-markdown-with-cmark-gfm), that JSON output writes C1 controls raw (escaped since escape-json-output), and describes escaping before harden-output.

## Goals / Non-Goals

**Goals:**

- No input larger than a limit is held in memory, on any build; one that is fails the way other unreadable inputs do, saying how to raise the limit.
- The limit is a command-line option with a default, the same for every input of a run.

**Non-Goals:**

- A limit on the whole run (the sum of the inputs): each input is read, checked, and released in turn.
- Limits on nesting or on the number of requirements: memory follows bytes.
- Parsing OpenSpec JSON once instead of twice (decision 4).

## Decisions

### 1. Read at most the limit and one byte, on every build

- A platform's `Reading` only reads: `file: (String, Int) => IO[String, Array[Byte]]` and `standardInput: Int => IO[String, Array[Byte]]` return at most the bytes asked for, or fail with the platform's error. The JVM and Scala Native read with `readNBytes`; Node reads with `fs.readSync` in chunks of 64 KiB, so its memory follows what the input holds rather than the limit, and takes an `EOF` error (how Windows ends a pipe, from memory) as the end of input. A file's stream or descriptor is closed on every path, and a failed close is logged as a warning.
- Shared code does the rest (`Cli.read`): it asks for the limit and one byte, which tells "exactly the limit" from "more" without the input's real size and stops a stream that never ends (`yes |`), and checks what came back with the pure `Bounded.decode(bytes, limit)`: more than `limit` bytes is `ReadFailure.TooLarge(limit)`, anything else goes to `Utf8.decode`. So the rule lives once, measured, and a platform can't get it wrong; the code review of 2026-11-15 proposed it, and the owner chose it over each platform adding the byte and the check.
- _Alternative: the file's size first (`stat`)_: no help for standard input or a pipe, and a file can grow between the two calls. Rejected.

### 2. The failure: an unreadable input, with its limit

- `ReadFailure.TooLarge(limit)`: the message is `Cannot read <input>: more than <limit> bytes; --max-input-bytes raises the limit`, the log entry carries `error` (`more than the limit`), `limit`, and `exit` 2, as `Cli.read` logs every read failure, and the run stops there with status 2.

### 3. The option and its bounds: 10 MiB by default, at most 100 MiB (the owner's decision)

- `--max-input-bytes <n>`, a whole number of bytes from 1 to 104,857,600 (100 MiB); 10,485,760 (10 MiB) when it isn't given. Requirement documents are kilobytes, and an OpenSpec change's or spec's JSON rarely more than a megabyte, so 10 MiB stops a hostile input well before memory matters while leaving a large real one room; 100 MiB is far below every `Int` the reading and parsing use (`limit + 1`, `CmarkTree.enter`'s offsets, Native's `strlen`), which can't then overflow. Bytes only, no `10M`: one unit to read and test.
- _Alternatives_: no upper bound (the `Int` overflows the roadmap names come back at 1 GiB and 2 GiB); 1 MiB (too close to a large real change); suffixes (more parsing for little gain). The default and the bound are the owner's to choose.

### 4. OpenSpec JSON parsed twice: left as it is (the owner's decision)

- `OpenSpecInput.candidates` parses once into a generic tree to see whether there are `deltas`, then again as a change or a spec, because the typed parse's account of malformed JSON (`(expected '"' got 'n')`) says more than the tree's. With the limit, the second parse costs at most 10 MiB's worth by default; parsing once would change those messages, which specs and tests quote.
- _Alternative: parse once and decode from the tree_: the messages change. Not chosen; it can come later on its own.

### 5. Tests

- The check (`Bounded`), the message, and the option are pure and tested on every platform; `Main.reading` gets a JVM test with a standard input that never ends; `scripts/cli-cases` compares the builds on a file at and over the limit, a standard input that never ends, and invalid limits.

## Risks / Trade-offs

- [A real input over 10 MiB now fails] → The message names `--max-input-bytes`, up to 100 MiB.
- [Node's `fs.readSync` on descriptor 0 can throw `EAGAIN` when standard input is non-blocking, as some terminals leave it] → `fs.readFileSync(0)`, used now, reads through the same call, so nothing changes there (from memory, not rechecked).
- [Peak memory at the default limit isn't measured] → Decision 3 rests on input sizes, not a measurement; an input of 10 MiB is a small multiple of what CI's cases already parse quickly.

## Assumptions checked and unchecked

Checked on 2026-11-15: every observation above, including the probes. Unchecked: Node's `EAGAIN` behavior on non-blocking standard input (from memory; no change either way), and peak memory per build at 10 MiB (not measured; a measurement would need a new record, see the risk).
