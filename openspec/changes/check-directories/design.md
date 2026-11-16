# Design

## Context

See proposal.md for why. Observations (2026-11-16, the source on `batch-check-other-projects` at 9382bd6, and scratchpad probes on macOS arm64 with JDK 27, Scala Native 0.5.12, and Node 25.2.1):

- `Settings` keeps one `Settings` per file argument (`file: Option[String]`, `None` for standard input), and infers each input's format from its name: `.md` is Markdown, `.json` OpenSpec JSON, anything else plain text, case-sensitive (`notes.MD` and `notes.markdown` are plain text). The profile follows the format (OpenSpec for OpenSpec JSON, classic otherwise).
- `Cli.check` loads the inputs in order with `ZIO.foreach`, stopping at the first failure; `Cli.read` asks the platform's `Reading` for at most the limit and one byte, and turns a failure into `Cannot read <input>` with an error log entry carrying the platform's error. `Checker.outcome` gives status 2 and no findings when loading failed.
- `Reading` has `file` and `standardInput`; the JVM and Scala Native share `Main` (`java.nio.file`), Node has `NodeMain` (`node:fs`). `CliSuite` builds fake `Reading`s in 11 places, which change shape when `Reading` gains fields.
- A directory as an input fails today: `Files.newInputStream` throws on the JVM and Native, and Node's `readSync` fails with `EISDIR`, so all print `Cannot read notes` (spec "Unreadable input message", `MainSuite`'s "a directory given as a file", `scripts/cli-cases`' `directory` case, which gives an empty directory).
- Listing (probe on a fixture with files, a hidden directory, a link to a file, a link to a directory, a link to `.`, a broken link, and a directory with mode 000): `Files.newDirectoryStream` with `Files.isSymbolicLink`, `Files.isDirectory(p, NOFOLLOW_LINKS)`, and `Files.isRegularFile(p, NOFOLLOW_LINKS)` classify every entry the same on the JVM and on Scala Native 0.5.12; Node's `fs.readdirSync(dir, { withFileTypes: true })` with `isSymbolicLink()`, `isDirectory()`, and `isFile()` classifies them the same way (a link is only a link, never its target). Listing the mode-000 directory fails with `java.nio.file.AccessDeniedException: <path>` (JVM and Native) and `Error: EACCES: permission denied, scandir '<path>'` (Node); listing a file fails with `NotDirectoryException` / `ENOTDIR`.
- Names: a file named with a decomposed `é` (`e` + U+0301) and one with the precomposed U+00E9 come back with the same code points on all three platforms (no normalization).
- Node's `readdirSync` with `withFileTypes` dates from Node 10.10 and `statSync`'s `throwIfNoEntry` from 14.17 (from memory of Node's changelog); the oldest supported Node is 22 (`engines`, CI). Node 22 isn't installed here; CI's Node 22 job runs `scripts/check-npm-cli` on the new cases.
- OpenSpec's own Markdown: `ears-check --profile openspec openspec/specs/ears-cli/spec.md` (the 0.x jar at 9382bd6) reports 27 findings, all on scenario lines (`- **WHEN** …`), which aren't requirements; with the default classic profile, 95. A spec's Markdown is not what this checker reads well; its JSON (`openspec show --json`) is.

## Goals / Non-Goals

**Goals:**

- `ears-check docs/` checks the Markdown under `docs`, with the same output, order, and messages on the JVM, Node, and the native binary, on any file system.
- A walk never leaves the directory, never loops, and never blocks on something that isn't a file.

**Non-Goals:**

- `.gitignore` or any other ignore file; a list of names to skip (`node_modules`) (decision 3).
- Globs in arguments (the shell's job), and a recursion depth option.
- OpenSpec's Markdown layout as a special case: the theme's next change (decision 8).
- A limit on the number of files or on the run's total bytes: each file has the size limit, as now.

## Decisions

### 1. Which files: names ending in `.md` or `.txt` (the owner's decision, 2026-11-16)

- In a directory, a file is checked when its name ends in `.md` or `.txt`, and read as the format inference reads a file named on the command line: `.md` as Markdown, `.txt` as plain text (each nonblank line one requirement). Requirement documents come in both. `.json` files are left alone: in a repository they're mostly not OpenSpec (`package.json`, `tsconfig.json`), and would make `ears-check .` fail on files that hold no requirements.
- `--format` applies to every file found, as to a file named; it doesn't change which files are found.
- _Alternatives_: `.md` only (what Claude proposed: plain-text files in a repository are often other things, such as licenses or Python's `requirements.txt`, each of whose lines would be a finding); every suffix the inference knows (`.md`, `.json`, `.txt`); the selected `--format`'s suffix (`--format openspec dir/` finds `.json`). Case-insensitive `.MD`, `.TXT`, and `.markdown` would also change what a named file is read as; left for a change of its own.

### 2. Subdirectories: all of them

- The walk goes down every subdirectory (OpenSpec-style and docs-site layouts nest by topic). No depth option: the depth is the file system's.

### 3. What is skipped: hidden entries, links, and anything not a file or directory (the owner agreed, 2026-11-16)

- An entry whose name starts with `.` is skipped, file or directory, so `.git`, `.github`'s templates, and editors' `.#notes.md` lock files aren't read. The directory given is checked whatever its name.
- Every symbolic link found in a directory is skipped, whatever it names. Following links to directories could loop (`loop -> .`); following links to files would let a crafted repository make a CI job read and quote any file the runner can read (`notes.md -> ~/.aws/credentials`, sentences quoted in fixes). A path given on the command line is followed, as now: the user chose it.
- An entry that is neither a file nor a directory (a named pipe, a socket, a device) is skipped, so a walk can't block reading a pipe.
- `node_modules` and other tool directories aren't special: the walk reads what's under the directory given, and the README says to give the folder of requirements, not the project root.
- _Alternatives_: follow links to files (convenient for shared documents, but the quoting risk above); follow links that stay inside the directory (needs each platform's real path, and a race between check and read); skip `node_modules`, `target`, `out` by name (a list to keep and argue over); honor `.gitignore` (a parser of its own, and not the only ignore format).

### 4. Order: each directory's entries by name, by code unit

- Each directory's entries are sorted by name with `String`'s ordering (UTF-16 code units, the same on all three platforms), and a subdirectory's files come where its name sorts (`B.md`, `a.md`, `b/c.md`, `b.md`). The platforms list in different orders (directory order on the JVM and Node, both file-system dependent), so sorting happens in shared code. No locale collation: it differs between platforms and versions.
- A directory takes its place among the inputs, which keep the order given ("Findings in file order").

### 5. Names: the directory as given, `/`, the path below

- A file found is named by joining the directory as given and each name below it with `/`, adding none after a directory given with a trailing `/` (`docs/` → `docs/scheduling.md`). The name is built in shared code, so it's the same on every platform, and it's also the path the file is read by; Windows accepts `/` (from memory: the JVM and Node both pass it to Windows' file API, which takes either separator).
- Names come from the file system, so they're untrusted: they go through the existing escaping (`Visible`) wherever they're shown, as file names from a glob do now.

### 6. The seam: two more functions on `Reading`, decisions in shared code

- `Reading` gains `isDirectory: String => UIO[Boolean]` (whether a path given on the command line is a directory, following links; any error is `false`, so the path is then read as a file and fails as now) and `entries: String => IO[String, List[Entry]]` (a directory's entries, unsorted, each `Entry(name, kind)` with `EntryKind` `File`, `Directory`, `Link`, or `Other`, never following a link; a failure is the platform's error, for the log).
- The JVM and Native: `Files.isDirectory(path)`; `Files.newDirectoryStream`, closed on every path, with each entry's `BasicFileAttributes` read with `NOFOLLOW_LINKS`. An entry whose attributes can't be read (in a directory that can be listed but not searched) is a `File`, as Node takes it from the listing's own type, so the read reports it (`Cannot read docs/sub/a.md`) on every build rather than the JVM skipping it silently (the code review, 2026-11-16). Node: `fs.statSync(path, { throwIfNoEntry: false })?.isDirectory()` with errors as `false`; `fs.readdirSync(path, { withFileTypes: true })` with `isSymbolicLink()`, `isDirectory()`, `isFile()`.
- A shared, pure object (`Walk`) decides, for one directory's entries, the ordered steps: check a file, go into a subdirectory, or skip an entry (and why: hidden, link, other, not Markdown), and the name of each. `Cli` sequences it: a file argument that is a directory is walked depth-first, each file loaded as now (`load`, with the file's name in `Settings.file`), each subdirectory listed when it's reached, so the first failure in input order is the one reported and nothing later is read.
- _Alternative: list the whole tree first_: a listing failure deep in the tree would then preempt a parse failure in an earlier file. Rejected.

### 7. Failures: a directory that can't be listed, and one with nothing to check

- A directory or subdirectory that can't be listed is an unreadable input: an error log entry (`Cannot read input`, the platform's `error`, `exit` 2), `Cannot read <name>`, status 2, the run stops.
- A directory given that holds no file to check, counted over its whole tree, fails the same way with `Cannot check <name>: it holds no .md or .txt files` and an error log entry (`Nothing to check`, `exit` 2). An empty folder in a CI gate is far more likely a mistake (a renamed folder, a wrong path) than intended, and passing silently would hide it. A subdirectory with nothing to check is fine.
- _Alternative: exit 0 with nothing checked_, as `grep -r` over an empty directory does. Not chosen: the owner agreed to the error (2026-11-16).

### 8. OpenSpec's Markdown: the theme's next change

- OpenSpec keeps its specs as Markdown (`openspec/specs/*/spec.md`), but scenarios' `WHEN`/`THEN` lines read as requirements to the Markdown reader (27 findings on `ears-cli`'s spec with `--profile openspec`). The owner chose (2026-11-16) to read OpenSpec's Markdown natively, in a change of its own after this one, `read-openspec-markdown`: in an OpenSpec layout (`openspec/specs/**/spec.md` and active changes' delta specs), only each requirement's statement (before its first scenario; in a delta, ADDED and MODIFIED requirements), with the OpenSpec profile, and nothing else under `openspec/`. It needs the Markdown facade to say which heading each sentence is under.
- This change treats `openspec/` like any other directory, so until that change a walk over it gives the scenario-line findings; the theme merges as a whole, so no release has the gap. Its README text waits for that change.
- _Alternatives the owner declined_: running `openspec show --json` from the checker (needs OpenSpec installed, a subprocess on three platforms, and ties the checker to OpenSpec's CLI); refusing an OpenSpec folder with a pointer to the JSON route (prevents false findings, checks nothing).

### 9. Logging

- `Listing directory` (debug) with the directory; `Listed directory` (debug) with the counts of files to check, subdirectories, and entries skipped; `Skipped entry` (debug) for each link and other entry, with its name and kind (rare, and the ones an operator would ask about); hidden entries and other files are counted, not listed one by one (a `.git` or a build folder has thousands).

### 10. Tests

- `Walk` is pure and tested on every platform: hidden names, each kind, `.md` and `.txt` only, the order (code units, subdirectories in place), names with and without a trailing `/`.
- `CliSuite` (every platform) with fake `Reading`s that hold a small tree: the walk's order among inputs, a listing failure that stops the run, a directory with nothing to check, the messages. Its 11 existing fakes take the two new functions (a mechanical edit, asked of the owner first); a helper gives "nothing is a directory".
- `JvmReadingSuite`: the real listing on a temporary tree with each kind (a link to a file, to a directory, a broken link), and an unlistable directory (skipped as root, as the unreadable-file test is).
- `MainSuite`'s "a directory given as a file" gives an empty directory and expects `Cannot read <dir>`; it changes to the new message (asked of the owner first). "by default an unreadable input logs one entry" also gives an empty directory; it still passes (one error entry), but no longer tests an unreadable input, so it moves to a missing file (asked too).
- `scripts/cli-cases`: a tree with nested, hidden, linked, and non-Markdown entries compared across the builds (`directory-tree`), with JSON output; the existing `directory` case (an empty directory) keeps status 2 with the new message; an unlistable subdirectory (skipped as root); a named pipe in the tree (skipped where `mkfifo` is missing, as on Windows, which runs only the smoke cases).

## Risks / Trade-offs

- [`ears-check .` in a JavaScript project walks `node_modules` and checks every package's README] → Many findings and a slow run, not a failure or a leak; the README tells users to give the requirements folder (decision 3).
- [A `.txt` file that isn't requirements (a license, `requirements.txt`) gives a finding on most of its lines] → The same advice: give the folder of requirements.
- [A file found is swapped for a link between listing and reading] → Needs someone writing to the directory during the run; CI checkouts aren't. Opening with `O_NOFOLLOW` would close it on POSIX but needs each platform's flag and Native's support; not done.
- [Windows: junctions and links need privileges and differ] → Node reports a junction as a symbolic link (from memory); the JVM's classification isn't checked there. Windows CI runs only the smoke cases; the tree case is skipped there.
- [A file name that isn't valid UTF-8 (possible on Linux) can't be read back: every build decodes names to strings, replacing the bad bytes, so `caf\xe9.md` is listed as `caf�.md` and its read fails] → The run stops with exit status 2 and `Cannot read docs/caf�.md`: loud, not silent. Accepted as a known limitation (the code review, 2026-11-16); reading names as bytes would need a byte path type on three platforms.
- [Many files: findings accumulate in memory for the whole run, as for many named files] → Each file is limited; a run's total isn't (non-goal).
- [The empty-directory error breaks a script that relied on `Cannot read <dir>`] → Before this change a directory was always an error, so no script could have passed one usefully.

## Assumptions checked and unchecked

- Checked (2026-11-16, probes): listing and classification agree on the JVM, Scala Native 0.5.12, and Node 25.2.1 for files, directories, hidden entries, links to files, directories, and `.`, broken links, and a mode-000 directory; names keep their code points on all three; the jar at 9382bd6 rejects a directory with `Cannot read`; OpenSpec's spec Markdown gives scenario-line findings.
- Unchecked: Node 22's behavior (left to CI's Node 22 job); named pipes on each platform (`isFile()` / `isRegularFile` false is expected; the `cli-cases` case checks it); Windows separators and junctions (decision 5, risks); `String` ordering being code-unit order on Scala.js and Native (it's the Java spec's `compareTo`, which both implement; `Walk`'s tests run on all three).
