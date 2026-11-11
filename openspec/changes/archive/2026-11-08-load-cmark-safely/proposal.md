# Proposal

## Why

The native binary promises (SECURITY.md, the README, the `markdown-input` spec) never to load a library from the current directory, and its own code only ever names absolute directories. But the system's loader can still search the current directory for cmark when the user's library path variable has a relative or empty entry, which a careless `export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/opt/lib` leaves (`:/opt/lib`). A probe on 2026-11-08 showed it on macOS: with `DYLD_LIBRARY_PATH=.`, a `libcmark.dylib` planted in the current directory ran, though the binary asked for `/opt/homebrew/lib/libcmark.dylib`. Running `ears-check` on a checked-out repository's Markdown would then run that repository's code. 0.2.0 is the first release with the native binary, so close it before then.

## What Changes

- **macOS:** dyld searches `DYLD_LIBRARY_PATH` first even for an absolute path, and reads the variable when the process starts, so the binary can't stop it. While the variable has a relative entry (dyld ignores empty ones, a probe showed), the native Markdown reader loads no cmark and says which variable to fix; plain text and OpenSpec JSON still work, as without cmark.
- **Linux:** glibc's loader uses an absolute path as it is, ignoring `LD_LIBRARY_PATH`; only the bare name `libcmark.so`, which the binary tries first, goes through it. While `LD_LIBRARY_PATH` has a relative or empty entry, the bare name is skipped, and cmark is loaded by absolute path from the directories the binary lists.
- CI plants a library that prints a marker in the native recheck jobs' working directory, sets the variable to include the current directory, and fails if the marker appears.
- SECURITY.md and the README drop their caveat and say what the binary does instead.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `markdown-input`: "No library from the current directory" covers the library path variables: the Linux bare name only while `LD_LIBRARY_PATH` is all absolute, and on macOS no cmark while `DYLD_LIBRARY_PATH` isn't.
- `continuous-integration`: the native check plants a library in the current directory on Linux and macOS.

## Impact

- `ears/markdown/src/.../CmarkLibrary.scala`: a pure check of the variable's entries, the Linux candidates, and the macOS refusal's message; tests in `HelpersSuite`.
- `ears/markdown/native/src/.../MarkdownParser.scala`: applies them.
- `.github/workflows/native.yml`: the planted-library step on each recheck job.
- SECURITY.md, README, CHANGELOG, roadmap, work log.
