## Why

The Markdown reader reads GitHub's tables with about 700 lines of its own (`Tables`, `Rows`), layered on three parsers that don't know tables (commonmark-java, commonmark.js, cmark), and the batch's security review (2026-11-13) found three ways small crafted input makes that code overflow the stack (54 KB on the JVM, 27 KB on Node) or take quadratic time. GitHub renders Markdown with cmark-gfm, and a probe on 2026-11-13 found that cmark-gfm 0.29.0.gfm.13 with its table extension matches GitHub's renderer on all 144 conformance fixtures, where commonmark-java's table extension matches on 127 and micromark's on 139 (`docs/measurements/table-parser-probe.jsonl`). A spike the same day compiled cmark-gfm to WebAssembly and ran it on Node and, through Chicory, on the JVM, with output identical to native cmark-gfm on every fixture and no extra JVM option (`docs/measurements/cmark-gfm-wasm-spike.jsonl`). Reading every platform with GitHub's own parser makes the table code, its findings, and two of the three parsers unnecessary.

## What Changes

- **One parser everywhere**: the Markdown reader parses with cmark-gfm 0.29.0.gfm.13 and its table extension on the JVM, Node, and Scala Native, built from one vendored copy of its C source with a small C entry point of this repository's that returns the parts of the tree the reader uses.
  - Scala Native compiles the C source into the binary.
  - The JVM and Node run one WebAssembly module built from it with a pinned wasi-sdk: Node with its own `WebAssembly`, the JVM with Chicory, compiled to JVM classes at build time.
- **Removed**: commonmark-java, commonmark.js, the run-time loading of an installed cmark, and the reader's table code (`Tables`, most of `Rows`, `SourceLines`). The reader keeps correcting cmark's known position errors (`CmarkLines`), and adds corrections for cmark-gfm's around tables (a header row placed on the line of the paragraph the table interrupts, and that paragraph's lost positions).
- **The native binary needs no cmark installed.** **BREAKING** only for anyone relying on the native binary's messages and log details about a missing cmark, which go.
- **A link definition directly above a table** is read as GitHub reads it, as paragraph text, so a cell's link to it stays unresolved; the reader resolved it before (a difference the owner accepted on 2026-11-12).
- **The CLI npm package** has no dependencies, and ships the WebAssembly module beside `main.js`.
- **cmark-gfm's license notices** ship with the CLI jar, the CLI npm package, and the GitHub Release.
- **Build safeguards**: the wasi-sdk archive is checked against its recorded checksum, the built module against its recorded checksum (so every host builds the same module), and the JVM classes generated from it against a list of the functions allowed to exceed HotSpot's 8,000-byte compile limit.
- **Docs**: the README's, CONTRIBUTING's, and the release notes' instructions to install cmark go; CONTRIBUTING gains wasi-sdk.

## Capabilities

### New Capabilities

- `markdown-parser-build`: building cmark-gfm into the checker: the pinned source and toolchain, the WebAssembly module's checksum, the generated JVM classes' method sizes, and the license notices each distribution carries.

### Modified Capabilities

- `markdown-input`: three requirements about loading an installed cmark are removed; added requirements that the reader parses as cmark-gfm 0.29.0.gfm.13 with its table extension does, needs no Markdown library installed, and reads a paragraph of thousands of chained tables without failing.
- `ears-cli`: the requirements for a missing cmark ("Missing cmark", "Libraries tried for Markdown") are removed.
- `cli-native-binary`: the purpose and the glibc scenario no longer mention cmark; no requirement statement changes beyond that, so only the scenario is modified.
- `cli-npm-package`: "Markdown parser dependency" is replaced by a requirement for no dependencies and one for the WebAssembly module in the package.
- `continuous-integration`: "A library planted in the current directory" and "Pinned Node CLI install" are removed, since the binary loads no library and the package installs no dependency.
- `github-release`: the release carries a third-party notices file, and its notes no longer name cmark as a native binary's need.

## Impact

- Code: `ears/markdown` (the three `MarkdownParser`s become one shared reader of the C entry point's output, over a thin call on each platform; `Tables`, `Rows`, `SourceLines`, `CmarkLibrary`, and the native loader go), `ears/cli` (the native missing-cmark messages and log details), and the conformance suite (fixture changes listed in the tasks, each asked of the owner first).
- Build: `build.mill` gains tasks that download wasi-sdk, build the module, compile it to JVM classes with Chicory's build-time compiler, check both checksums and the method sizes, and give Scala Native the C source; Chicory's runtime (Apache-2.0) joins `ears.cli`'s dependencies; commonmark-java and the Node lockfile under `ears/markdown/js` go.
- CI: the native jobs stop installing cmark and lose the planted-library check; jobs that build the module cache wasi-sdk.
- New in the repository: cmark-gfm's C source, vendored unedited (about 900 KB, BSD-2-Clause and other permissive notices) under `ears/markdown/cmark-gfm/` and marked `linguist-vendored`, with `scripts/update-cmark-gfm` to replace it from a tag; and about 60 lines of C.
- Performance (spike, 2026-11-13, Apple M4 Max): on the JVM, about 120 ms more to load the parser and 2.4 times commonmark-java's time on 1.9 MB of Markdown; Node about the same as native cmark-gfm. `scripts/time-cli` measures the CLI before and after.
