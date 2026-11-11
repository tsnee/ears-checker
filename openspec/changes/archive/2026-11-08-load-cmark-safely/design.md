# Design

## Context

Observations (2026-11-08, macOS 27.2 on Apple silicon, the native binary from `ears.cli.native.nativeLink` at `fd8c5ac`, Homebrew's cmark 0.31.2 in `/opt/homebrew/lib`). Each probe ran in an empty scratch directory with a library built by `cc -dynamiclib` whose only code is a constructor that writes `PLANTED` to standard error, and a one-line Markdown file:

- **No variable:** the binary loads `/opt/homebrew/lib/libcmark.dylib`; nothing planted runs.
- **`DYLD_LIBRARY_PATH=.`, planted `libcmark.dylib` and `libcmark.0.31.2.dylib`:** `PLANTED` printed twice, and the log shows `attempts="/opt/homebrew/lib/libcmark.dylib: no cmark_version; /opt/homebrew/lib/libcmark.0.31.2.dylib: no cmark_version"`: the binary asked for absolute paths and dyld opened the planted files of the same leaf names instead, running their code.
- **`DYLD_LIBRARY_PATH=/opt/homebrew/lib:.`:** the real cmark loads (the absolute entry comes first); nothing planted runs. Entry order decides, so any relative entry is a risk.
- **Empty entries and values:** with the planted libraries in the current directory, `DYLD_LIBRARY_PATH` set to `""`, `:`, `/opt/homebrew/lib:`, and `:/opt/homebrew/lib` each loaded the real cmark; dyld ignores empty entries, and an empty value. A relative entry other than `.` counts too: `DYLD_LIBRARY_PATH=lib` with a planted library in `./lib` ran it.
- **A planted `libSystem.B.dylib` with `DYLD_LIBRARY_PATH=.`:** not loaded; the binary ran normally. The binary links only `/usr/lib/libSystem.B.dylib`. So the exposure shown is cmark's `dlopen`, which happens only when Markdown is read.
- **The code today** (`CmarkLibrary`, `CmarkLoader`): `directories` keeps only the variable's absolute entries, and every candidate is an absolute path, except Linux's bare `libcmark.so`, tried first, which glibc resolves through `LD_LIBRARY_PATH`, the binary's `RUNPATH`, its cache, and the default directories.
- **dyld** (Apple's `dlopen(3)`): for a path containing a slash, dyld first looks for the leaf name in each `DYLD_LIBRARY_PATH` directory, then at the path; it reads the variable at launch, so changing the environment later doesn't help.
- **glibc** (Linux `dlopen(3)`, `ld.so(8)`): a filename containing a slash is used as given; only a bare name is searched for, `LD_LIBRARY_PATH` first, where an empty entry, or a relative one, means the current directory.
- **A Linux probe** (2026-11-08, run by the owner with colima, since this sandbox can't reach Docker): in `ubuntu:22.04` (glibc 2.35) and `ubuntu:24.04` (glibc 2.39), arm64, with `libcmark-dev` (`libcmark.so.0.30.2`) installed, a C program doing what the binary does (`dlopen`, then `cmark_version`) in a directory holding a planted `libcmark.so` that prints a marker. The bare name `libcmark.so` loaded the planted file with `LD_LIBRARY_PATH` set to `:`, `.`, `:/opt/lib`, `/opt/lib:`, and `/opt/lib::/srv/lib`, and with `lib` when `./lib` held a copy; it loaded the system's cmark when the variable was unset, `""`, or `lib` with nothing in `./lib`. The absolute path `/usr/lib/aarch64-linux-gnu/libcmark.so.0.30.2` loaded the system's cmark in every case. Both releases gave the same results.
- **A second Linux probe** (2026-11-08, the same setup, after both reviews pointed to glibc's `":;"`): the bare name loaded the planted file with `LD_LIBRARY_PATH` set to `/opt/lib;`, `/opt/lib;.`, `;/opt/lib`, and `;`, and the system's cmark with `/opt/lib;/srv/lib`; the absolute path loaded the system's cmark in every case, on both releases. So `;` separates entries exactly as `:` does.

## Goals / Non-Goals

**Goals:**

- No setting of the library path variables makes the native binary run a library from the current directory when it reads Markdown.
- Plain text and OpenSpec JSON unaffected.

**Non-Goals:**

- Defending against an attacker who controls the environment (`DYLD_INSERT_LIBRARIES`, `LD_PRELOAD`, an absolute directory they own): the environment is the user's own.
- Libraries the binary links at startup: the loader resolves those before any of our code runs, for every program alike, and the probe found macOS's `libSystem` not overridden.

## Decisions

### 1. Which entries are unsafe

- Which entries make a value unsafe differs by loader. On macOS, a relative entry (one that doesn't start with `/` and isn't empty: `.`, `lib`); dyld ignores empty entries and an empty value, and separates entries with `:` only (observed above). On Linux, a relative entry or an empty one (`:/opt/lib`, `/opt/lib:`, `a::b`), which glibc documents as the current directory, with entries separated by `:` or `;` (glibc's `ld.so(8)` and its loader's `":;"`; found by both reviews, and confirmed by the owner's probe, task 3.2); a value that's set but wholly empty is treated as unset, since glibc processes `LD_LIBRARY_PATH` only when it isn't empty. The directories `directories` lists split the same way.
- Entries the loaders expand (`$ORIGIN` on Linux, `@executable_path` on macOS) don't start with `/`, so they count as unsafe: the conservative choice, found in review and kept.
- A pure function, `CmarkLibrary.unsafeEntries(platform, value: Option[String]): List[String]`, gives the unsafe entries (empty ones as `""`), so the decision and the message are tested on the JVM.

### 2. Linux: skip the bare name

- `CmarkLibrary.loadable(platform, listing, loaderPath)` gives what to try: `candidates` as before while the variable is safe; on Linux, while it's unsafe, `candidates` without the bare `libcmark.so`, so every candidate is an absolute path from `directories`, which glibc opens as given. `candidates` itself is unchanged. A cmark in a directory only `ld.so.conf` names (not in the listed ones) is then not found, and the message says what to install.
- The log's `attempts` already lists every path tried, so a skipped bare name is visible.

### 3. macOS: decline

- While `DYLD_LIBRARY_PATH` is unsafe, `loadable` returns `MarkdownUnavailable` with no attempts and the message (`relativeLibraryPath`): "cmark wasn't loaded: DYLD_LIBRARY_PATH has the entry '<entry>', not an absolute directory, so macOS would look for cmark in a directory relative to the current one first; remove it or make it absolute" (plural for several entries). Exit status 2 for Markdown, as without cmark; plain text and OpenSpec JSON never load cmark.
- Why decline rather than load anyway: dyld would open a planted file of the same leaf name before the absolute path, and the program can't change how dyld searches once it's running.

### 4. Tests

- `HelpersSuite` (JVM, Node, natively): `unsafeEntries` on unset, empty, all-absolute, `.`, `lib`, leading, trailing, and double separators, and semicolons on each platform; `directories` splitting at semicolons on Linux; `loadable` on Linux with and without the bare name and on macOS declining; the message naming every entry.
- The conformance suite is unaffected (it reads Markdown with whatever cmark is installed, with no unsafe variable set in CI).

### 5. CI: a planted library

- In `native.yml`'s recheck jobs, after the native check, a step builds the marker library with the runner's `cc` into a fresh directory, under the names the binary would look up (`libcmark.so` on Linux; `libcmark.dylib` and every installed versioned name on macOS, failing if there's none), and first runs a control: the binary with the planted library preloaded (`LD_PRELOAD`, `DYLD_INSERT_LIBRARIES`) must print the marker, so a case that prints nothing proves something. Then, on a Markdown file:
  - Linux, with `LD_LIBRARY_PATH` set to `:`, `.`, `:/opt/lib`, `/opt/lib:`, `/opt/lib::/srv/lib`, `lib` (with a copy in `./lib`), `/opt/lib;`, and `/opt/lib;.`: no marker, and the log's `parser` an absolute path; with `""`: no marker, cmark read. (With the old code these would print the marker: a check that can fail.) Also `readelf -d` must show no `RPATH` or `RUNPATH` in the binary, which the bare name would follow too (security review's hardening).
  - macOS, with `.`, `lib`, and `/opt/homebrew/lib:.`: no marker, exit status 2, and the message; with `""`, `:`, `:/opt/homebrew/lib`, and `/opt/homebrew/lib;.`: no marker, cmark read.
- Plain text with the first unsafe value still exits 1 with its finding.

### 6. What the docs claim

- The binary never loads _cmark_ from the current directory. On Linux, a relative or empty `LD_LIBRARY_PATH` entry also exposes the libraries every dynamically linked program loads at startup, before any of our code runs; that's the environment's, as the non-goals say, and SECURITY.md says so, with an absolute entry into a repository (direnv, mise, nix shells) out of scope (both reviews).

## Risks / Trade-offs

- **[A cmark only `ld.so.conf` finds]** With an unsafe `LD_LIBRARY_PATH`, Linux no longer finds a cmark in a directory the binary doesn't list; the message says what to install, and fixing the variable restores it.
- **[A user who relies on a relative DYLD_LIBRARY_PATH]** They lose Markdown in the native binary until they fix the variable; the message says how. The jar and the Node build are unaffected.

## Open Questions

- None.
