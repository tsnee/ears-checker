# Design

## Context

See proposal.md for why. What's in place, and what probes found (2026-11-07, Scala Native
0.5.12, macOS arm64):

- **Libraries:** ZIO 2.1.27, zio-json 1.1.1, decline 2.6.2, cats-parse 1.1.0, and zio-test 2.1.27
  publish Scala Native 0.5 builds. A probe app using ZIO's runtime, clock, and console, decline,
  and zio-json linked and ran: `getArgs` holds the arguments (unlike Scala.js); `Clock.instant`
  works (no time-zone trouble, unlike Scala.js); `Files.readAllBytes`, `System.in.readAllBytes`,
  and `PrintStream(FileOutputStream(FileDescriptor.err), true, UTF_8)` work; a missing file
  fails with `NoSuchFileException`, a directory with an `IOException`;
  `"Iİ".toLowerCase(Locale.ROOT)` gives the JVM's `ii̇`. So the classifier's JVM `LetterCase`
  and the CLI's JVM `Main` work natively unchanged.
- **Sizes and speed:** the probe's release build (`release-full`) linked in 22 s, is 20.6 MB
  (12.6 MB stripped), and starts in under 0.01 s; the jar takes 0.67 s on the README.
- **cmark today:** `ears.markdown.native` binds cmark with `@extern` (8 functions:
  `cmark_parse_document`, `cmark_node_first_child`, `cmark_node_next`,
  `cmark_node_get_type_string`, `cmark_node_get_literal`, `cmark_node_get_start_line`,
  `cmark_node_get_end_line`, `cmark_node_free`) and links it through `pkg-config`
  (`cmarkLinking`). The linked test binary records `libcmark.0.31.2.dylib`: cmark names its
  library by full release, so such a binary starts only where that release is installed.
  - Homebrew installs `libcmark.dylib` (unversioned) and `libcmark.0.31.2.dylib` in
    `/opt/homebrew/lib`, which isn't on macOS's default search path for `dlopen`; it ships no
    static library.
  - Ubuntu's runtime package is `libcmark0.30.2` on 22.04, 24.04, and 26.04, and installs
    `libcmark.so.0.30.2`; `libcmark-dev` adds `libcmark.so` and `libcmark.a`. The package's name
    carries the release, so it differs between distributions, but the `cmark` package (the
    command line) depends on whichever it is, so `apt install cmark` works on each
    (packages.ubuntu.com and packages.debian.org, 2026-11-07). The first draft named
    `libcmark0.30.0`, which CI run 42409556968 on `ubuntu-26.04` showed doesn't exist there.
- **Loading at run time:** a probe with Scala Native's `scala.scalanative.posix.dlfcn` loaded
  `/opt/homebrew/lib/libcmark.dylib` by path, bound `cmark_version_string` with
  `CFuncPtr.fromPtr`, and printed `0.31.2`; the binary linked only `/usr/lib/libSystem.B.dylib`.
  A bare `dlopen("libcmark.dylib")` failed on macOS (search path), as expected.
- **The current directory (2026-11-07, from the security review):** `man dlopen` on macOS says
  that for a bare name, dyld searches `DYLD_LIBRARY_PATH`, then `LC_RPATH`, then, for an
  unrestricted process (ours: not hardened or setuid), the current working directory, before its
  fallbacks. The reviewer loaded a test `libcmark.dylib` from the current directory this way and
  its initializer ran. glibc never searches the current directory for a bare name, only an
  empty or relative entry in the user's own `LD_LIBRARY_PATH`. After the fix, the binary, run
  with `--verbose` in a directory holding a planted `libcmark.dylib`, loaded
  `cmark 0.31.2 at /opt/homebrew/lib/libcmark.dylib`.
- **`HOMEBREW_PREFIX`:** `brew shellenv`, which Homebrew's installer has users add to their
  shell profile, sets it to Homebrew's prefix.
- **Owner's choice:** a runtime dependency on cmark is fine if it's clearly advertised; dynamic
  libraries are preferred where available.

## Goals / Non-Goals

**Goals:**
- A native `ears-check` with the jar's output, messages, and exit statuses, checked by running
  both on the same cases.
- No cmark at link time; any compatible installed cmark at run time; a clear message without
  one.

**Non-Goals:**
- Release attachments, Homebrew or other packages, and platforms CI doesn't build.
- Publishing `ears.native` to Maven Central.
- Coverage of native-only code (scoverage measures the JVM only), which is kept thin.

## Decisions

### 1. Modules

| Module | Sources | Tests |
|---|---|---|
| `ears.native` | `ears/src` + `ears/src-jvm` (`LetterCase` with `Locale.ROOT`) | `ears/test/src`, natively |
| `ears.cli.native` | `ears/cli/src` + `ears/cli/src-jvm` (`Main`), the Markdown reader's shared and native sources | `ears/cli/test/src`, natively |

- `src-jvm` now serves the JVM and Scala Native; its name stays, and CLAUDE.md says so.
- `ears.cli.native` links in `release-full` mode; its test module links in debug mode, which
  is much faster to build.
- Both depend on the Scala Native builds of the libraries above, at the versions the JVM
  modules use (the shared version values in `build.mill`).

### 2. cmark loaded at run time

- **Pure choice, shared and measured** (`ears/markdown/src`, `CmarkLibrary`):
  - the candidates, given the platform (macOS or Linux) and the files listed in each library
    directory: on Linux first the bare `libcmark.so`, which the loader's cache resolves; then
    each directory's unversioned file, then its versioned files, newest release first. On macOS
    there's no bare name: every candidate is an absolute path, so the loader never searches the
    current directory, where a cloned repository could plant a library;
  - the directories, absolute ones only: those named by `LD_LIBRARY_PATH` or
    `DYLD_LIBRARY_PATH`, then `$HOMEBREW_PREFIX/lib`, then macOS `/opt/homebrew/lib`,
    `/usr/local/lib`, `/opt/local/lib`, or Linux `/usr/lib/x86_64-linux-gnu`,
    `/usr/lib/aarch64-linux-gnu`, `/usr/lib64`, `/usr/lib`, `/usr/local/lib`. Nobody has to set
    a variable: Homebrew's directories are listed;
  - the search (`firstLoaded`): the first candidate that opens with every function and a
    supported release, so an old or broken library earlier in the list doesn't hide a good one;
  - whether a release is supported (`cmark_version()` at least 0.30.0, the oldest the
    conformance suite runs on, in CI);
  - without one (`unavailable`), the message, preferring too old (the newest such release) over
    a missing function over none found, and each attempt described for the log.
- **Native shell** (`ears/markdown/native/src`): lists the directories, and for each candidate
  `dlopen`s it (keeping `dlerror`'s text on failure), binds the 8 functions and `cmark_version`
  with `dlsym` and `CFuncPtr.fromPtr`, checks the version, and `dlclose`s a library it can't
  use. It loads on each `Markdown` read (no global state; `dlopen` returns the same handle once
  loaded), so a plain-text or JSON run never touches cmark.
- **The log:** `Markdown.parser()` names the parser (`commonmark-java`, `mdast-util-from-markdown`,
  or `cmark 0.31.2 at /opt/homebrew/lib/libcmark.dylib`) or gives every attempt; the CLI puts
  that on each Markdown input's log entries (`parser`, and `attempts` without one), so the
  error entry for a missing cmark lists every library tried.
- **Failure reaches the user** as a parse failure of that input:
  `notes.md: Markdown needs the cmark library (0.30 or later), which isn't installed; install it
  with brew install cmark, or apt install cmark on Debian and Ubuntu` (exit 2).
  - So `Markdown.sentences` returns `Either[String, List[Sentence]]` (always `Right` on the
    JVM and Node). The conformance suite's `fixture` helper and the CLI's round-trip suite
    compare with `Right(...)`: changes to existing tests, to ask the owner about in apply.
- **Build:** `cmarkLinking` and `pkg-config` go; nothing links cmark. Running the native tests
  still needs cmark installed, as CI does.

### 3. The check: `scripts/check-native-cli`

- The case list and the comparison (`run`, `compare`, the expected statuses) move from
  `scripts/check-npm-cli` into `scripts/cli-cases`, which both scripts source; each script
  builds its own CLI and supplies the command under test. The Node script keeps its package
  checks (no map, size, installed packages, npx).
- `check-native-cli` builds the jar and `ears.cli.native.nativeLink`, checks the binary's linked
  libraries (`otool -L` on macOS, `ldd` on Linux: no cmark), and compares it with the jar on
  every case.

### 4. CI

- `check` builds the native binary and runs `scripts/check-native-cli` after the Node check.
  `__.test` already runs every test module, now including `ears.native.test` and
  `ears.cli.native.test`.
- A last step proves the missing-cmark path on a real system, in `bash` with `pipefail` and an
  explicit `if` for each check (`-e` ignores a failed test inside `&&`). It removes
  `libcmark-dev`, checks no unversioned `libcmark.so` is left in any searched directory, and
  expects a Markdown finding read through the runtime package's versioned library (the
  `parser` log detail names it); then removes the runtime package, checks no cmark is left
  (`ldconfig -p` and the directories), and expects exit 2, the message, and the `attempts`
  detail for Markdown, and the usual result for plain text.
- Apply records how much time the native steps add.

### 5. Docs

- README: the native binary (how to build it until releases attach it), its runtime
  dependency on cmark 0.30 or later, and the install commands.
- CLAUDE.md: the modules, `src-jvm` serving both, and cmark loaded at run time.

## Risks / Trade-offs

- **[A system keeps cmark somewhere unexpected]** Linux's bare-name `dlopen`, `HOMEBREW_PREFIX`,
  the loader-path variables, and the usual directories cover Homebrew (any prefix where
  `brew shellenv` runs), MacPorts, and Debian, Ubuntu, Fedora, and Arch layouts; anything else
  gets the message, and the log lists every library tried. A `--cmark <path>` option waits until
  someone needs it.
- **[A library planted in the current directory]** Avoided on macOS by loading absolute paths
  only, and checked by a property test that every candidate is absolute apart from Linux's bare
  name, which glibc never resolves from the current directory.
- **[Native-only code is unmeasured]** The loader shell is thin; every decision is in
  `CmarkLibrary`, at 100% coverage with the rest of `ears.markdown`.
- **[Linux file names under the C locale]** Unknown for the native binary; apply checks it on
  CI's Linux and the README says what it found.
- **[Build time]** Release linking takes tens of seconds; the probe took 22 s on this machine.
